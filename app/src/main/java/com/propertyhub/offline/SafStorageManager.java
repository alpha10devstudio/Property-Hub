package com.propertyhub.offline;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

public class SafStorageManager {

    private static final String PREFS_NAME = "property_hub_storage_prefs";
    private static final String KEY_TREE_URI = "persisted_tree_uri";
    private static final String ROOT_FOLDER_NAME = "Property Hub";

    private static final Object FILE_LOCK = new Object();

    private final Context appContext;
    private final SharedPreferences prefs;
    private final DateTimeProvider dateTimeProvider;

    public SafStorageManager(@NonNull Context context) {
        this(context, new DateTimeProvider());
    }

    public SafStorageManager(@NonNull Context context, @NonNull DateTimeProvider dateTimeProvider) {
        this.appContext = context.getApplicationContext();
        this.prefs = this.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dateTimeProvider = dateTimeProvider;
    }

    @Nullable
    public Uri getSavedTreeUri() {
        String uriString = prefs.getString(KEY_TREE_URI, null);
        if (uriString == null || uriString.trim().isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(uriString);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasValidStorageAccess() {
        Uri treeUri = getSavedTreeUri();
        if (treeUri == null) {
            return false;
        }
        if (!hasPersistedReadWritePermission(treeUri)) {
            return false;
        }
        DocumentFile root = DocumentFile.fromTreeUri(appContext, treeUri);
        return root != null && root.exists() && root.isDirectory() && root.canWrite();
    }

    public String getSelectedFolderDisplayName() {
        Uri treeUri = getSavedTreeUri();
        if (treeUri == null) {
            return null;
        }
        DocumentFile root = DocumentFile.fromTreeUri(appContext, treeUri);
        if (root == null || !root.exists()) {
            return null;
        }
        String name = root.getName();
        if (name == null || name.trim().isEmpty()) {
            return "Selected Folder/" + ROOT_FOLDER_NAME;
        }
        if (ROOT_FOLDER_NAME.equals(name)) {
            return ROOT_FOLDER_NAME;
        }
        return name + "/" + ROOT_FOLDER_NAME;
    }

    @Nullable
    public Uri buildSuggestedDocumentsInitialUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:Documents"
                );
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public boolean initializeDirectoryFromTreeUri(@NonNull Uri treeUri) {
        synchronized (FILE_LOCK) {
            try {
                ContentResolver resolver = appContext.getContentResolver();
                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                resolver.takePersistableUriPermission(treeUri, takeFlags);

                DocumentFile selectedRoot = DocumentFile.fromTreeUri(appContext, treeUri);
                if (selectedRoot == null || !selectedRoot.exists()
                        || !selectedRoot.isDirectory() || !selectedRoot.canWrite()) {
                    return false;
                }

                DocumentFile propertyHubDir = resolveOrCreatePropertyHubDir(selectedRoot);
                if (propertyHubDir == null) {
                    return false;
                }

                DocumentFile houseDir = findOrCreateSubdirectory(
                        propertyHubDir,
                        PropertyCategory.HOUSE_FOR_RENT.getFolderName()
                );
                DocumentFile landDir = findOrCreateSubdirectory(
                        propertyHubDir,
                        PropertyCategory.LAND_FOR_SALE.getFolderName()
                );

                if (houseDir == null || landDir == null) {
                    return false;
                }

                prefs.edit().putString(KEY_TREE_URI, treeUri.toString()).apply();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    @NonNull
    public StorageResult savePropertyRecord(
            @NonNull PropertyCategory category,
            @NonNull String mobile,
            @NonNull String details
    ) {
        synchronized (FILE_LOCK) {
            Uri treeUri = getSavedTreeUri();
            if (treeUri == null || !hasPersistedReadWritePermission(treeUri)) {
                return StorageResult.permissionMissing(
                        category,
                        "Storage folder access is missing or has been revoked. Please select your storage folder again."
                );
            }

            DocumentFile selectedRoot = DocumentFile.fromTreeUri(appContext, treeUri);
            if (selectedRoot == null || !selectedRoot.exists()
                    || !selectedRoot.isDirectory() || !selectedRoot.canWrite()) {
                return StorageResult.permissionMissing(
                        category,
                        "The selected storage folder is unavailable or not writable. Please select a folder again."
                );
            }

            try {
                DocumentFile propertyHubDir = resolveOrCreatePropertyHubDir(selectedRoot);
                if (propertyHubDir == null) {
                    return StorageResult.error(
                            category,
                            "Unable to access or create the Property Hub folder."
                    );
                }

                DocumentFile categoryDir = findOrCreateSubdirectory(
                        propertyHubDir,
                        category.getFolderName()
                );
                if (categoryDir == null) {
                    return StorageResult.error(
                            category,
                            "Unable to access or create the \"" + category.getFolderName() + "\" folder."
                    );
                }

                // Always read current local device date & time at submission time (never UTC)
                Date submissionTime = dateTimeProvider.now();
                String dateStr = dateTimeProvider.formatLocalDate(submissionTime);
                String timeStr = dateTimeProvider.formatLocalTime(submissionTime);
                String targetFileName = dateStr + ".txt";

                DocumentFile dailyFile = findExactFile(categoryDir, targetFileName);
                boolean newlyCreated = false;
                if (dailyFile == null) {
                    dailyFile = createDailyTextFile(categoryDir, targetFileName);
                    newlyCreated = true;
                }

                if (dailyFile == null || !dailyFile.exists()) {
                    return StorageResult.error(
                            category,
                            "Could not create daily text file \"" + targetFileName + "\"."
                    );
                }

                String existingContent = "";
                if (!newlyCreated) {
                    existingContent = readExistingTextSafely(dailyFile);
                }

                int nextRecordNumber =
                        RecordFormatter.extractHighestRecordNumber(existingContent) + 1;

                String updatedContent = RecordFormatter.appendRecordToContent(
                        existingContent,
                        nextRecordNumber,
                        dateStr,
                        timeStr,
                        category,
                        mobile,
                        details
                );

                writeTextSafely(dailyFile.getUri(), updatedContent);

                String displayPath = ROOT_FOLDER_NAME + "/"
                        + category.getFolderName() + "/" + targetFileName;

                return StorageResult.success(
                        category,
                        nextRecordNumber,
                        targetFileName,
                        displayPath
                );
            } catch (SecurityException se) {
                return StorageResult.permissionMissing(
                        category,
                        "Permission to write to the selected folder was revoked. Please choose the folder again."
                );
            } catch (IOException ioe) {
                return StorageResult.error(
                        category,
                        "Failed to write property record: " + safeErrorDetail(ioe)
                );
            } catch (Exception e) {
                return StorageResult.error(
                        category,
                        "Unexpected storage error: " + safeErrorDetail(e)
                );
            }
        }
    }

    private boolean hasPersistedReadWritePermission(@NonNull Uri treeUri) {
        List<UriPermission> persisted = appContext.getContentResolver().getPersistedUriPermissions();
        for (UriPermission permission : persisted) {
            if (treeUri.equals(permission.getUri())
                    && permission.isReadPermission()
                    && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private DocumentFile resolveOrCreatePropertyHubDir(@NonNull DocumentFile selectedRoot) {
        if (ROOT_FOLDER_NAME.equals(selectedRoot.getName())) {
            boolean hasCategoryChild =
                    findExactDirectory(selectedRoot, PropertyCategory.HOUSE_FOR_RENT.getFolderName()) != null
                            || findExactDirectory(selectedRoot, PropertyCategory.LAND_FOR_SALE.getFolderName()) != null;
            if (hasCategoryChild) {
                return selectedRoot;
            }
        }
        return findOrCreateSubdirectory(selectedRoot, ROOT_FOLDER_NAME);
    }

    @Nullable
    private DocumentFile findOrCreateSubdirectory(
            @NonNull DocumentFile parent,
            @NonNull String directoryName
    ) {
        DocumentFile existing = findExactDirectory(parent, directoryName);
        if (existing != null) {
            return existing;
        }
        return parent.createDirectory(directoryName);
    }

    @Nullable
    private DocumentFile findExactDirectory(
            @NonNull DocumentFile parent,
            @NonNull String directoryName
    ) {
        DocumentFile[] children = parent.listFiles();
        for (DocumentFile child : children) {
            if (child.isDirectory() && directoryName.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    @Nullable
    private DocumentFile findExactFile(
            @NonNull DocumentFile parent,
            @NonNull String fileName
    ) {
        DocumentFile[] children = parent.listFiles();
        for (DocumentFile child : children) {
            if (child.isFile() && fileName.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    @Nullable
    private DocumentFile createDailyTextFile(
            @NonNull DocumentFile categoryDir,
            @NonNull String targetFileName
    ) {
        DocumentFile created = categoryDir.createFile("text/plain", targetFileName);
        if (created == null) {
            return null;
        }
        String createdName = created.getName();
        if (createdName != null && !targetFileName.equals(createdName)) {
            if (createdName.equals(targetFileName + ".txt")) {
                created.renameTo(targetFileName);
            }
        }
        return created;
    }

    @NonNull
    private String readExistingTextSafely(@NonNull DocumentFile dailyFile) throws IOException {
        ContentResolver resolver = appContext.getContentResolver();
        long reportedLength = dailyFile.length();

        try (InputStream in = resolver.openInputStream(dailyFile.getUri())) {
            if (in == null) {
                throw new IOException("Could not open existing file for reading.");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            byte[] data = buffer.toByteArray();
            if (reportedLength > 0 && data.length == 0) {
                throw new IOException("Existing daily file could not be read reliably.");
            }
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private void writeTextSafely(@NonNull Uri fileUri, @NonNull String content) throws IOException {
        ContentResolver resolver = appContext.getContentResolver();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        OutputStream out = null;
        try {
            try {
                out = resolver.openOutputStream(fileUri, "wt");
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                out = resolver.openOutputStream(fileUri, "w");
            }
            if (out == null) {
                throw new IOException("Could not open output stream for daily file.");
            }
            out.write(bytes);
            out.flush();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String safeErrorDetail(Throwable t) {
        String msg = t.getMessage();
        return (msg == null || msg.trim().isEmpty()) ? t.getClass().getSimpleName() : msg.trim();
    }
}
