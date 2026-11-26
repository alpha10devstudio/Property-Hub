package com.propertyhub.offline.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.propertyhub.offline.model.PosterFileItem;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyFieldCatalog;
import com.propertyhub.offline.model.PropertyFieldValue;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.model.ZipFileItem;
import com.propertyhub.offline.util.DateTimeProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StorageManager {

    public static final String ROOT_FOLDER_NAME = "Property Hub";
    public static final String GENERATED_POSTERS_FOLDER = "Generated Posters";
    public static final String ZIP_EXPORTS_FOLDER = "ZIP Exports";

    private static final Object FILE_LOCK = new Object();

    private final Context appContext;
    private final SettingsManager settingsManager;
    private final RecordIdManager recordIdManager;
    private final DateTimeProvider dateTimeProvider;

    public static final class RecordsQueryResult {
        private final List<PropertyRecord> records;
        private final List<String> warnings;
        private final boolean storageAccessible;

        public RecordsQueryResult(
                @NonNull List<PropertyRecord> records,
                @NonNull List<String> warnings,
                boolean storageAccessible
        ) {
            this.records = records;
            this.warnings = warnings;
            this.storageAccessible = storageAccessible;
        }

        @NonNull
        public List<PropertyRecord> getRecords() {
            return records;
        }

        @NonNull
        public List<String> getWarnings() {
            return warnings;
        }

        public boolean isStorageAccessible() {
            return storageAccessible;
        }
    }

    public StorageManager(@NonNull Context context) {
        this(context, new SettingsManager(context), new DateTimeProvider());
    }

    public StorageManager(
            @NonNull Context context,
            @NonNull SettingsManager settingsManager,
            @NonNull DateTimeProvider dateTimeProvider
    ) {
        this.appContext = context.getApplicationContext();
        this.settingsManager = settingsManager;
        this.recordIdManager = new RecordIdManager(settingsManager);
        this.dateTimeProvider = dateTimeProvider;
    }

    @NonNull
    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    @Nullable
    public Uri getSavedTreeUri() {
        return settingsManager.getSavedTreeUri();
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

    @NonNull
    public String getSelectedFolderShortName() {
        return ROOT_FOLDER_NAME;
    }

    @Nullable
    public String getSelectedFolderDisplayPath() {
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
            return "/" + ROOT_FOLDER_NAME;
        }
        if (ROOT_FOLDER_NAME.equals(name)) {
            return "/" + ROOT_FOLDER_NAME;
        }
        return "/" + name + "/" + ROOT_FOLDER_NAME;
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

                DocumentFile houseTxtDir = findOrCreateSubdirectory(
                        propertyHubDir, PropertyCategory.HOUSE_FOR_RENT.getFolderName());
                DocumentFile landTxtDir = findOrCreateSubdirectory(
                        propertyHubDir, PropertyCategory.LAND_FOR_SALE.getFolderName());
                DocumentFile postersRoot = findOrCreateSubdirectory(
                        propertyHubDir, GENERATED_POSTERS_FOLDER);
                DocumentFile zipRoot = findOrCreateSubdirectory(
                        propertyHubDir, ZIP_EXPORTS_FOLDER);

                if (houseTxtDir == null || landTxtDir == null || postersRoot == null || zipRoot == null) {
                    return false;
                }

                DocumentFile housePostersDir = findOrCreateSubdirectory(
                        postersRoot, PropertyCategory.HOUSE_FOR_RENT.getFolderName());
                DocumentFile landPostersDir = findOrCreateSubdirectory(
                        postersRoot, PropertyCategory.LAND_FOR_SALE.getFolderName());

                if (housePostersDir == null || landPostersDir == null) {
                    return false;
                }

                settingsManager.setSavedTreeUri(treeUri);

                // Reconcile ID sequences against existing records in the selected directory
                reconcileMaxSequenceLocked(propertyHubDir, PropertyCategory.HOUSE_FOR_RENT);
                reconcileMaxSequenceLocked(propertyHubDir, PropertyCategory.LAND_FOR_SALE);

                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static final class DeleteResult {
        private final boolean success;
        private final boolean deletedPoster;
        private final boolean deletedRecord;
        @Nullable
        private final String errorMessage;

        private DeleteResult(
                boolean success,
                boolean deletedPoster,
                boolean deletedRecord,
                @Nullable String errorMessage
        ) {
            this.success = success;
            this.deletedPoster = deletedPoster;
            this.deletedRecord = deletedRecord;
            this.errorMessage = errorMessage;
        }

        public static DeleteResult success(boolean deletedPoster, boolean deletedRecord) {
            return new DeleteResult(true, deletedPoster, deletedRecord, null);
        }

        public static DeleteResult error(@NonNull String message) {
            return new DeleteResult(false, false, false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isDeletedPoster() {
            return deletedPoster;
        }

        public boolean isDeletedRecord() {
            return deletedRecord;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Legacy 3-argument save overload kept for compatibility.
     */
    @NonNull
    public StorageResult saveNewPropertyRecord(
            @NonNull PropertyCategory category,
            @NonNull String mobile,
            @NonNull String details
    ) {
        return saveNewPropertyRecord(
                category,
                PropertyFieldCatalog.getDefaultTitle(category),
                mobile,
                Collections.emptyList(),
                details
        );
    }

    /**
     * Primary source-of-truth save operation for structured multi-field property records.
     * Stages the updated TXT payload to a temporary document, verifies staging integrity,
     * appends/updates the daily category TXT file, and verifies final read-back before returning success.
     */
    @NonNull
    public StorageResult saveNewPropertyRecord(
            @NonNull PropertyCategory category,
            @NonNull String title,
            @NonNull String mobile,
            @NonNull List<PropertyFieldValue> fieldValues
    ) {
        String summaryDetails = PropertyRecord.buildHumanReadableSummary(category, fieldValues);
        return saveNewPropertyRecord(category, title, mobile, fieldValues, summaryDetails);
    }

    @NonNull
    private StorageResult saveNewPropertyRecord(
            @NonNull PropertyCategory category,
            @NonNull String title,
            @NonNull String mobile,
            @NonNull List<PropertyFieldValue> fieldValues,
            @NonNull String detailsSummary
    ) {
        synchronized (FILE_LOCK) {
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                return StorageResult.permissionMissing(
                        category,
                        "Storage folder access is missing or revoked. Please select your storage folder again."
                );
            }

            try {
                DocumentFile categoryDir = findOrCreateSubdirectory(
                        propertyHubDir, category.getFolderName());
                if (categoryDir == null) {
                    return StorageResult.error(
                            category,
                            "Unable to access or create the \"" + category.getFolderName() + "\" folder."
                    );
                }

                Date submissionTime = dateTimeProvider.now();
                String dateStr = dateTimeProvider.formatLocalDate(submissionTime);
                String timeStr = dateTimeProvider.formatLocalTime(submissionTime);
                String targetFileName = dateStr + ".txt";

                DocumentFile dailyFile = findExactFile(categoryDir, targetFileName);
                String existingContent = "";
                if (dailyFile != null && dailyFile.exists()) {
                    existingContent = readExistingTextSafely(dailyFile);
                }

                long observedMax = Math.max(
                        extractMaxSequenceFromText(category, existingContent),
                        scanMaxSequenceInCategoryFolder(propertyHubDir, category)
                );
                String recordId = recordIdManager.allocateNextRecordId(category, dateStr, observedMax);

                PropertyRecord record = new PropertyRecord(
                        recordId,
                        dateStr,
                        timeStr,
                        category,
                        title,
                        mobile.trim(),
                        detailsSummary,
                        fieldValues,
                        targetFileName
                );

                String updatedContent = RecordFormatter.appendRecordToContent(existingContent, record);
                writeTxtViaStagingAndVerify(categoryDir, dailyFile, targetFileName, updatedContent, recordId);

                String displayPath = ROOT_FOLDER_NAME + "/"
                        + category.getFolderName() + "/" + targetFileName;

                return StorageResult.success(category, record, targetFileName, displayPath);
            } catch (SecurityException se) {
                return StorageResult.permissionMissing(
                        category,
                        "Permission to write to the selected folder was revoked. Please choose the folder again."
                );
            } catch (IOException ioe) {
                return StorageResult.error(
                        category,
                        "Failed to write property record to TXT: " + safeErrorDetail(ioe)
                );
            } catch (Exception e) {
                return StorageResult.error(
                        category,
                        "Unexpected storage error: " + safeErrorDetail(e)
                );
            }
        }
    }

    /**
     * Deletes both the generated JPG poster (if present) and the linked record block from its daily TXT file
     * so no orphaned JPGs or dangling records remain.
     */
    @NonNull
    public DeleteResult deleteRecordAndPoster(
            @NonNull PropertyCategory category,
            @NonNull String recordId
    ) {
        synchronized (FILE_LOCK) {
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                return DeleteResult.error("Storage folder access is missing or revoked.");
            }

            boolean deletedPoster = false;
            boolean deletedRecord = false;

            try {
                // 1. Delete matching poster JPG file(s)
                DocumentFile postersRoot = findExactDirectory(propertyHubDir, GENERATED_POSTERS_FOLDER);
                if (postersRoot != null) {
                    String expectedNameLower = PropertyRecord.buildPosterFileName(category, recordId)
                            .toLowerCase(Locale.US);
                    List<DocumentFile> candidates = new ArrayList<>();
                    collectPosterDocumentsForCategory(postersRoot, category, candidates);
                    for (DocumentFile candidate : candidates) {
                        String name = candidate.getName();
                        if (name != null && name.toLowerCase(Locale.US).equals(expectedNameLower)) {
                            if (candidate.delete()) {
                                deletedPoster = true;
                            }
                        }
                    }
                }
                settingsManager.removePosterManifestEntry(category, recordId);

                // 2. Remove matching record block safely from category daily TXT files
                DocumentFile catDir = findExactDirectory(propertyHubDir, category.getFolderName());
                if (catDir != null) {
                    for (DocumentFile txtFile : listSortedTxtFiles(catDir)) {
                        String fileName = txtFile.getName();
                        if (fileName == null) {
                            continue;
                        }
                        String content = readExistingTextSafely(txtFile);
                        RecordFormatter.RemoveResult removeResult =
                                RecordFormatter.removeRecordFromContent(content, recordId);
                        if (removeResult.isRemoved()) {
                            String updated = removeResult.getUpdatedContent();
                            if (updated.trim().isEmpty()) {
                                txtFile.delete();
                            } else {
                                writeTxtViaStagingAndVerifyContent(catDir, txtFile, fileName, updated);
                            }
                            deletedRecord = true;
                            break;
                        }
                    }
                }

                if (!deletedPoster && !deletedRecord) {
                    return DeleteResult.error("Matching poster or record could not be found.");
                }
                return DeleteResult.success(deletedPoster, deletedRecord);
            } catch (Exception e) {
                return DeleteResult.error("Delete failed: " + safeErrorDetail(e));
            }
        }
    }

    @NonNull
    public RecordsQueryResult loadRecords(
            @Nullable PropertyCategory categoryFilter,
            @Nullable String startDateInclusive,
            @Nullable String endDateInclusive
    ) {
        synchronized (FILE_LOCK) {
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                return new RecordsQueryResult(
                        Collections.emptyList(),
                        Collections.singletonList("Storage folder is not configured or access was revoked."),
                        false
                );
            }

            List<PropertyRecord> allRecords = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            PropertyCategory[] categories = categoryFilter == null
                    ? new PropertyCategory[]{PropertyCategory.HOUSE_FOR_RENT, PropertyCategory.LAND_FOR_SALE}
                    : new PropertyCategory[]{categoryFilter};

            for (PropertyCategory cat : categories) {
                Map<String, Uri> validPosters = indexValidPostersLocked(propertyHubDir, cat);
                DocumentFile catDir = findExactDirectory(propertyHubDir, cat.getFolderName());
                if (catDir == null) {
                    continue;
                }

                List<DocumentFile> txtFiles = listSortedTxtFiles(catDir);
                for (DocumentFile txtFile : txtFiles) {
                    String name = txtFile.getName();
                    if (name == null) {
                        continue;
                    }
                    String fileDate = name.substring(0, name.length() - 4);
                    if (!isDateInRange(fileDate, startDateInclusive, endDateInclusive)) {
                        continue;
                    }
                    try {
                        String content = readExistingTextSafely(txtFile);
                        RecordParser.ParseResult parsed =
                                RecordParser.parseFileContent(content, cat, name);
                        warnings.addAll(parsed.getWarnings());
                        for (PropertyRecord record : parsed.getRecords()) {
                            if (isDateInRange(record.getDate(), startDateInclusive, endDateInclusive)) {
                                Uri posterUri = validPosters.get(
                                        record.getPosterFileName().toLowerCase(Locale.US));
                                if (posterUri != null) {
                                    record.setPosterGenerated(true);
                                    record.setPosterUri(posterUri);
                                } else {
                                    record.setPosterGenerated(false);
                                    record.setPosterUri(null);
                                    settingsManager.removePosterManifestEntry(
                                            cat, record.getRecordId());
                                }
                                allRecords.add(record);
                            }
                        }
                    } catch (IOException ioe) {
                        warnings.add("Could not read " + cat.getFolderName() + "/" + name
                                + ": " + safeErrorDetail(ioe));
                    }
                }
            }

            Collections.sort(allRecords, (a, b) -> {
                int dateCmp = b.getDate().compareTo(a.getDate());
                if (dateCmp != 0) {
                    return dateCmp;
                }
                return b.getRecordId().compareTo(a.getRecordId());
            });

            return new RecordsQueryResult(allRecords, warnings, true);
        }
    }

    /**
     * Searches across all poster folders under Property Hub/Generated Posters and verifies
     * that the matching JPG file is readable, non-empty, and a valid uncorrupted JPEG image.
     */
    public boolean posterExists(@NonNull PropertyCategory category, @NonNull String recordId) {
        return findValidPosterUri(category, recordId) != null;
    }

    @Nullable
    public Uri findValidPosterUri(@NonNull PropertyCategory category, @NonNull String recordId) {
        synchronized (FILE_LOCK) {
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                return null;
            }
            Map<String, Uri> validMap = indexValidPostersLocked(propertyHubDir, category);
            String expectedKey = PropertyRecord.buildPosterFileName(category, recordId)
                    .toLowerCase(Locale.US);
            Uri uri = validMap.get(expectedKey);
            if (uri == null) {
                settingsManager.removePosterManifestEntry(category, recordId);
            }
            return uri;
        }
    }

    @NonNull
    public Map<String, Uri> getValidPosterIndex(@Nullable PropertyCategory categoryFilter) {
        synchronized (FILE_LOCK) {
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                return Collections.emptyMap();
            }
            Map<String, Uri> combined = new HashMap<>();
            if (categoryFilter == null || categoryFilter == PropertyCategory.HOUSE_FOR_RENT) {
                combined.putAll(indexValidPostersLocked(
                        propertyHubDir, PropertyCategory.HOUSE_FOR_RENT));
            }
            if (categoryFilter == null || categoryFilter == PropertyCategory.LAND_FOR_SALE) {
                combined.putAll(indexValidPostersLocked(
                        propertyHubDir, PropertyCategory.LAND_FOR_SALE));
            }
            return combined;
        }
    }

    @Nullable
    public Uri savePosterJpeg(
            @NonNull PropertyCategory category,
            @NonNull String recordId,
            @NonNull byte[] jpegBytes,
            boolean overwriteIfExists
    ) throws IOException {
        synchronized (FILE_LOCK) {
            if (!hasValidJpegMarkers(jpegBytes)) {
                throw new IOException("Rendered poster bytes failed JPEG header validation.");
            }
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                throw new IOException("Storage folder access is unavailable.");
            }
            DocumentFile posterDir = resolvePosterCategoryDir(propertyHubDir, category, true);
            if (posterDir == null) {
                throw new IOException("Could not access Generated Posters/" + category.getFolderName());
            }

            String posterName = PropertyRecord.buildPosterFileName(category, recordId);
            DocumentFile existing = findExactFile(posterDir, posterName);
            if (existing != null && existing.exists()) {
                boolean existingValid = isValidPosterFile(existing);
                if (!overwriteIfExists && existingValid) {
                    settingsManager.recordPosterManifestEntry(category, recordId);
                    return existing.getUri();
                }
                writeBytesSafely(existing.getUri(), jpegBytes);
                if (!isValidPosterFile(existing)) {
                    throw new IOException("Written JPG failed integrity verification.");
                }
                settingsManager.recordPosterManifestEntry(category, recordId);
                return existing.getUri();
            }

            // Also check across any nested poster folders if not overwriting
            if (!overwriteIfExists) {
                Uri elsewhereValid = findValidPosterUri(category, recordId);
                if (elsewhereValid != null) {
                    settingsManager.recordPosterManifestEntry(category, recordId);
                    return elsewhereValid;
                }
            }

            DocumentFile created = createFileExact(posterDir, "image/jpeg", posterName);
            if (created == null) {
                throw new IOException("Could not create poster file " + posterName);
            }
            writeBytesSafely(created.getUri(), jpegBytes);
            if (!isValidPosterFile(created)) {
                created.delete();
                throw new IOException("Created JPG failed integrity verification.");
            }
            settingsManager.recordPosterManifestEntry(category, recordId);
            return created.getUri();
        }
    }

    @NonNull
    public List<PosterFileItem> listPosterFiles(@Nullable PropertyCategory categoryFilter) {
        synchronized (FILE_LOCK) {
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                return Collections.emptyList();
            }

            RecordsQueryResult recordsResult = loadRecords(categoryFilter, null, null);
            Map<String, PropertyRecord> recordByPosterName = new HashMap<>();
            for (PropertyRecord rec : recordsResult.getRecords()) {
                recordByPosterName.put(
                        rec.getCategory().name() + "|" + rec.getPosterFileName().toLowerCase(Locale.US),
                        rec
                );
            }

            List<PosterFileItem> items = new ArrayList<>();
            PropertyCategory[] categories = categoryFilter == null
                    ? new PropertyCategory[]{PropertyCategory.HOUSE_FOR_RENT, PropertyCategory.LAND_FOR_SALE}
                    : new PropertyCategory[]{categoryFilter};

            DocumentFile postersRoot = findExactDirectory(propertyHubDir, GENERATED_POSTERS_FOLDER);
            if (postersRoot == null) {
                return Collections.emptyList();
            }

            for (PropertyCategory cat : categories) {
                List<DocumentFile> posterDocs = new ArrayList<>();
                collectPosterDocumentsForCategory(postersRoot, cat, posterDocs);

                for (DocumentFile f : posterDocs) {
                    String name = f.getName();
                    if (name == null || !isValidPosterFile(f)) {
                        continue;
                    }
                    PropertyRecord linked = recordByPosterName.get(
                            cat.name() + "|" + name.toLowerCase(Locale.US));
                    String recordId = linked != null
                            ? linked.getRecordId()
                            : deriveRecordIdFromPosterName(cat, name);
                    String recordDate = linked != null
                            ? linked.getDate()
                            : dateTimeProvider.formatLocalDate(new Date(f.lastModified()));
                    String sourceTxt = linked != null
                            ? linked.getSourceFileName()
                            : (recordDate + ".txt");

                    items.add(new PosterFileItem(
                            cat,
                            name,
                            recordId,
                            recordDate,
                            sourceTxt,
                            f.getUri(),
                            f.lastModified(),
                            linked
                    ));
                }
            }

            Collections.sort(items, (a, b) -> Long.compare(b.getLastModifiedMillis(), a.getLastModifiedMillis()));
            return items;
        }
    }

    @NonNull
    public List<PosterFileItem> listEligiblePostersForZip(
            @Nullable PropertyCategory categoryFilter,
            @Nullable String startDateInclusive,
            @Nullable String endDateInclusive
    ) {
        List<PosterFileItem> allPosters = listPosterFiles(categoryFilter);
        if (startDateInclusive == null && endDateInclusive == null) {
            return allPosters;
        }
        List<PosterFileItem> filtered = new ArrayList<>();
        for (PosterFileItem item : allPosters) {
            if (isDateInRange(item.getRecordDate(), startDateInclusive, endDateInclusive)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    @NonNull
    public List<DocumentFile> listTxtFilesForZip(
            @Nullable PropertyCategory categoryFilter,
            @Nullable String startDateInclusive,
            @Nullable String endDateInclusive
    ) {
        synchronized (FILE_LOCK) {
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                return Collections.emptyList();
            }
            List<DocumentFile> result = new ArrayList<>();
            PropertyCategory[] categories = categoryFilter == null
                    ? new PropertyCategory[]{PropertyCategory.HOUSE_FOR_RENT, PropertyCategory.LAND_FOR_SALE}
                    : new PropertyCategory[]{categoryFilter};
            for (PropertyCategory cat : categories) {
                DocumentFile catDir = findExactDirectory(propertyHubDir, cat.getFolderName());
                if (catDir == null) {
                    continue;
                }
                for (DocumentFile f : listSortedTxtFiles(catDir)) {
                    String name = f.getName();
                    if (name == null) {
                        continue;
                    }
                    String fileDate = name.substring(0, name.length() - 4);
                    if (isDateInRange(fileDate, startDateInclusive, endDateInclusive)) {
                        result.add(f);
                    }
                }
            }
            return result;
        }
    }

    @Nullable
    public DocumentFile createOrReplaceZipFile(@NonNull String zipFileName) throws IOException {
        synchronized (FILE_LOCK) {
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                throw new IOException("Storage folder access is unavailable.");
            }
            DocumentFile zipDir = findOrCreateSubdirectory(propertyHubDir, ZIP_EXPORTS_FOLDER);
            if (zipDir == null) {
                throw new IOException("Could not create ZIP Exports folder.");
            }
            DocumentFile existing = findExactFile(zipDir, zipFileName);
            if (existing != null && existing.exists()) {
                return existing;
            }
            return createFileExact(zipDir, "application/zip", zipFileName);
        }
    }

    @NonNull
    public List<ZipFileItem> listZipExports() {
        synchronized (FILE_LOCK) {
            DocumentFile propertyHubDir = resolveValidPropertyHubDir();
            if (propertyHubDir == null) {
                return Collections.emptyList();
            }
            DocumentFile zipDir = findExactDirectory(propertyHubDir, ZIP_EXPORTS_FOLDER);
            if (zipDir == null) {
                return Collections.emptyList();
            }
            List<ZipFileItem> list = new ArrayList<>();
            for (DocumentFile f : zipDir.listFiles()) {
                String name = f.getName();
                if (f.isFile() && name != null && name.toLowerCase(Locale.US).endsWith(".zip") && f.length() > 0) {
                    list.add(new ZipFileItem(name, f.getUri(), f.length(), f.lastModified()));
                }
            }
            Collections.sort(list, (a, b) -> Long.compare(b.getLastModifiedMillis(), a.getLastModifiedMillis()));
            return list;
        }
    }

    /**
     * Reads UTF-8 text from an external SAF file URI picked via ACTION_OPEN_DOCUMENT
     * without altering, moving, or deleting the source file.
     */
    @NonNull
    public String readTextFromExternalUri(@NonNull Uri uri) throws IOException {
        byte[] data = readBytesFromUri(uri);
        return new String(data, StandardCharsets.UTF_8);
    }

    @NonNull
    public String resolveDisplayName(@NonNull Uri uri) {
        try {
            DocumentFile doc = DocumentFile.fromSingleUri(appContext, uri);
            if (doc != null && doc.getName() != null && !doc.getName().trim().isEmpty()) {
                return doc.getName();
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "imported.txt";
    }

    @NonNull
    public byte[] readBytesFromUri(@NonNull Uri uri) throws IOException {
        try (InputStream in = appContext.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Unable to open input stream.");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    public boolean isValidPosterFile(@Nullable DocumentFile file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() <= 0) {
            return false;
        }
        return isValidPosterUri(file.getUri());
    }

    public boolean isValidPosterUri(@Nullable Uri uri) {
        if (uri == null) {
            return false;
        }
        Bitmap sampled = null;
        try {
            byte[] bytes = readBytesFromUri(uri);
            if (!hasValidJpegMarkers(bytes)) {
                return false;
            }
            BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
            boundsOpts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, boundsOpts);
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return false;
            }

            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = 8;
            decodeOpts.inPreferredConfig = Bitmap.Config.RGB_565;
            sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOpts);
            return sampled != null && sampled.getWidth() > 0 && sampled.getHeight() > 0;
        } catch (OutOfMemoryError | Exception e) {
            return false;
        } finally {
            if (sampled != null && !sampled.isRecycled()) {
                sampled.recycle();
            }
        }
    }

    public static boolean hasValidJpegMarkers(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length < 16) {
            return false;
        }
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        if (b0 != 0xFF || b1 != 0xD8) {
            return false;
        }
        // Check JPEG End-Of-Image marker (FF D9) at end (allow at most 2 trailing 0x00 padding bytes)
        int tailEnd = bytes.length - 1;
        while (tailEnd > 2 && bytes[tailEnd] == 0x00 && (bytes.length - 1 - tailEnd) < 2) {
            tailEnd--;
        }
        int e0 = bytes[tailEnd - 1] & 0xFF;
        int e1 = bytes[tailEnd] & 0xFF;
        return e0 == 0xFF && e1 == 0xD9;
    }

    /**
     * Writes TXT content safely using a temporary staging document first.
     * Existing daily file is only updated after staging succeeds and matches byte-for-byte,
     * and the temporary file is removed only after final read-back verification succeeds.
     */
    private void writeTxtViaStagingAndVerify(
            @NonNull DocumentFile categoryDir,
            @Nullable DocumentFile existingDailyFile,
            @NonNull String targetFileName,
            @NonNull String updatedContent,
            @NonNull String expectedRecordId
    ) throws IOException {
        byte[] expectedBytes = updatedContent.getBytes(StandardCharsets.UTF_8);
        String tempFileName = "." + targetFileName + ".tmp";

        DocumentFile staleTemp = findExactFile(categoryDir, tempFileName);
        if (staleTemp != null && staleTemp.exists()) {
            staleTemp.delete();
        }

        DocumentFile tempDoc = createFileExact(categoryDir, "text/plain", tempFileName);
        if (tempDoc == null) {
            throw new IOException("Could not create temporary staging document.");
        }

        try {
            writeBytesSafely(tempDoc.getUri(), expectedBytes);
            byte[] stagedVerify = readBytesFromUri(tempDoc.getUri());
            if (!Arrays.equals(expectedBytes, stagedVerify)) {
                throw new IOException("Staged TXT verification failed before committing record.");
            }

            DocumentFile targetDoc = existingDailyFile;
            if (targetDoc == null || !targetDoc.exists()) {
                targetDoc = createFileExact(categoryDir, "text/plain", targetFileName);
            }
            if (targetDoc == null || !targetDoc.exists()) {
                throw new IOException("Could not create daily text file \"" + targetFileName + "\".");
            }

            writeBytesSafely(targetDoc.getUri(), expectedBytes);
            byte[] finalVerify = readBytesFromUri(targetDoc.getUri());
            String finalText = new String(finalVerify, StandardCharsets.UTF_8);
            if (!Arrays.equals(expectedBytes, finalVerify) || !finalText.contains(expectedRecordId)) {
                throw new IOException("Daily TXT verification failed after write.");
            }
        } finally {
            if (tempDoc.exists()) {
                tempDoc.delete();
            }
        }
    }

    private void writeTxtViaStagingAndVerifyContent(
            @NonNull DocumentFile categoryDir,
            @NonNull DocumentFile targetDoc,
            @NonNull String targetFileName,
            @NonNull String updatedContent
    ) throws IOException {
        byte[] expectedBytes = updatedContent.getBytes(StandardCharsets.UTF_8);
        String tempFileName = "." + targetFileName + ".tmp";

        DocumentFile staleTemp = findExactFile(categoryDir, tempFileName);
        if (staleTemp != null && staleTemp.exists()) {
            staleTemp.delete();
        }

        DocumentFile tempDoc = createFileExact(categoryDir, "text/plain", tempFileName);
        if (tempDoc == null) {
            throw new IOException("Could not create temporary staging document.");
        }

        try {
            writeBytesSafely(tempDoc.getUri(), expectedBytes);
            byte[] stagedVerify = readBytesFromUri(tempDoc.getUri());
            if (!Arrays.equals(expectedBytes, stagedVerify)) {
                throw new IOException("Staged TXT verification failed before updating file.");
            }
            writeBytesSafely(targetDoc.getUri(), expectedBytes);
            byte[] finalVerify = readBytesFromUri(targetDoc.getUri());
            if (!Arrays.equals(expectedBytes, finalVerify)) {
                throw new IOException("Daily TXT verification failed after update.");
            }
        } finally {
            if (tempDoc.exists()) {
                tempDoc.delete();
            }
        }
    }

    @NonNull
    private Map<String, Uri> indexValidPostersLocked(
            @NonNull DocumentFile propertyHubDir,
            @NonNull PropertyCategory category
    ) {
        Map<String, Uri> validMap = new HashMap<>();
        DocumentFile postersRoot = findExactDirectory(propertyHubDir, GENERATED_POSTERS_FOLDER);
        if (postersRoot == null) {
            return validMap;
        }
        List<DocumentFile> candidates = new ArrayList<>();
        collectPosterDocumentsForCategory(postersRoot, category, candidates);

        for (DocumentFile f : candidates) {
            String name = f.getName();
            if (name == null) {
                continue;
            }
            if (isValidPosterFile(f)) {
                String key = name.toLowerCase(Locale.US);
                if (!validMap.containsKey(key)) {
                    validMap.put(key, f.getUri());
                }
            }
        }
        return validMap;
    }

    private void collectPosterDocumentsForCategory(
            @NonNull DocumentFile postersRoot,
            @NonNull PropertyCategory category,
            @NonNull List<DocumentFile> out
    ) {
        DocumentFile categoryDir = findExactDirectory(postersRoot, category.getFolderName());
        if (categoryDir != null) {
            collectJpgFilesRecursive(categoryDir, out, null, null, 0);
        }
        // Also scan any nested folders directly under Generated Posters for category-prefixed files
        String expectedPrefix = category.getPosterPrefix().toLowerCase(Locale.US);
        String legacyPrefix = category.getLegacyPosterPrefix().toLowerCase(Locale.US);
        for (DocumentFile child : postersRoot.listFiles()) {
            String childName = child.getName();
            if (childName == null) {
                continue;
            }
            if (child.isDirectory()
                    && !PropertyCategory.HOUSE_FOR_RENT.getFolderName().equals(childName)
                    && !PropertyCategory.LAND_FOR_SALE.getFolderName().equals(childName)) {
                collectJpgFilesRecursive(child, out, expectedPrefix, legacyPrefix, 1);
            } else if (child.isFile()) {
                String lower = childName.toLowerCase(Locale.US);
                if ((lower.startsWith(expectedPrefix) || lower.startsWith(legacyPrefix))
                        && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
                    out.add(child);
                }
            }
        }
    }

    private void collectJpgFilesRecursive(
            @NonNull DocumentFile dir,
            @NonNull List<DocumentFile> out,
            @Nullable String requiredPrefixLower,
            @Nullable String altPrefixLower,
            int depth
    ) {
        if (depth > 4) {
            return;
        }
        for (DocumentFile child : dir.listFiles()) {
            String name = child.getName();
            if (name == null) {
                continue;
            }
            if (child.isDirectory()) {
                collectJpgFilesRecursive(child, out, requiredPrefixLower, altPrefixLower, depth + 1);
            } else if (child.isFile()) {
                String lower = name.toLowerCase(Locale.US);
                boolean matchesPrefix = requiredPrefixLower == null
                        || lower.startsWith(requiredPrefixLower)
                        || (altPrefixLower != null && lower.startsWith(altPrefixLower));
                if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && matchesPrefix) {
                    out.add(child);
                }
            }
        }
    }

    private void reconcileMaxSequenceLocked(
            @NonNull DocumentFile propertyHubDir,
            @NonNull PropertyCategory category
    ) {
        long max = scanMaxSequenceInCategoryFolder(propertyHubDir, category);
        if (max > 0) {
            settingsManager.setLastSequence(category, max);
        }
    }

    private long scanMaxSequenceInCategoryFolder(
            @NonNull DocumentFile propertyHubDir,
            @NonNull PropertyCategory category
    ) {
        long max = 0L;
        DocumentFile catDir = findExactDirectory(propertyHubDir, category.getFolderName());
        if (catDir != null) {
            for (DocumentFile f : listSortedTxtFiles(catDir)) {
                try {
                    String content = readExistingTextSafely(f);
                    long found = extractMaxSequenceFromText(category, content);
                    if (found > max) {
                        max = found;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        DocumentFile posterDir = resolvePosterCategoryDir(propertyHubDir, category, false);
        if (posterDir != null) {
            for (DocumentFile p : posterDir.listFiles()) {
                String name = p.getName();
                if (p.isFile() && name != null) {
                    String derivedId = deriveRecordIdFromPosterName(category, name);
                    long seq = RecordIdManager.extractNumericSuffix(category, derivedId);
                    if (seq > max) {
                        max = seq;
                    }
                }
            }
        }
        return max;
    }

    private long extractMaxSequenceFromText(
            @NonNull PropertyCategory category,
            @NonNull String content
    ) {
        long max = 0L;
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("RECORD_ID:")) {
                String id = trimmed.substring("RECORD_ID:".length()).trim();
                long seq = RecordIdManager.extractNumericSuffix(category, id);
                if (seq > max) {
                    max = seq;
                }
            }
        }
        return max;
    }

    @Nullable
    private DocumentFile resolveValidPropertyHubDir() {
        Uri treeUri = getSavedTreeUri();
        if (treeUri == null || !hasPersistedReadWritePermission(treeUri)) {
            return null;
        }
        DocumentFile selectedRoot = DocumentFile.fromTreeUri(appContext, treeUri);
        if (selectedRoot == null || !selectedRoot.exists()
                || !selectedRoot.isDirectory() || !selectedRoot.canWrite()) {
            return null;
        }
        return resolveOrCreatePropertyHubDir(selectedRoot);
    }

    @Nullable
    private DocumentFile resolvePosterCategoryDir(
            @NonNull DocumentFile propertyHubDir,
            @NonNull PropertyCategory category,
            boolean createIfMissing
    ) {
        DocumentFile postersRoot = createIfMissing
                ? findOrCreateSubdirectory(propertyHubDir, GENERATED_POSTERS_FOLDER)
                : findExactDirectory(propertyHubDir, GENERATED_POSTERS_FOLDER);
        if (postersRoot == null) {
            return null;
        }
        return createIfMissing
                ? findOrCreateSubdirectory(postersRoot, category.getFolderName())
                : findExactDirectory(postersRoot, category.getFolderName());
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
            boolean hasChild =
                    findExactDirectory(selectedRoot, PropertyCategory.HOUSE_FOR_RENT.getFolderName()) != null
                            || findExactDirectory(selectedRoot, PropertyCategory.LAND_FOR_SALE.getFolderName()) != null
                            || findExactDirectory(selectedRoot, GENERATED_POSTERS_FOLDER) != null;
            if (hasChild) {
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
        for (DocumentFile child : parent.listFiles()) {
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
        for (DocumentFile child : parent.listFiles()) {
            if (child.isFile() && fileName.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    @Nullable
    private DocumentFile createFileExact(
            @NonNull DocumentFile dir,
            @NonNull String mimeType,
            @NonNull String targetFileName
    ) {
        DocumentFile created = dir.createFile(mimeType, targetFileName);
        if (created == null) {
            return null;
        }
        String createdName = created.getName();
        if (createdName != null && !targetFileName.equals(createdName)) {
            if (createdName.startsWith(targetFileName + ".")) {
                created.renameTo(targetFileName);
            }
        }
        return created;
    }

    @NonNull
    private List<DocumentFile> listSortedTxtFiles(@NonNull DocumentFile catDir) {
        List<DocumentFile> files = new ArrayList<>();
        for (DocumentFile child : catDir.listFiles()) {
            String name = child.getName();
            if (child.isFile() && name != null && name.endsWith(".txt") && !name.startsWith(".")) {
                files.add(child);
            }
        }
        Collections.sort(files, (a, b) -> {
            String na = a.getName() == null ? "" : a.getName();
            String nb = b.getName() == null ? "" : b.getName();
            return nb.compareTo(na);
        });
        return files;
    }

    private boolean isDateInRange(
            @Nullable String date,
            @Nullable String startInclusive,
            @Nullable String endInclusive
    ) {
        if (startInclusive == null && endInclusive == null) {
            return true;
        }
        if (date == null || date.trim().isEmpty()) {
            return false;
        }
        if (startInclusive != null && date.compareTo(startInclusive) < 0) {
            return false;
        }
        if (endInclusive != null && date.compareTo(endInclusive) > 0) {
            return false;
        }
        return true;
    }

    @NonNull
    private String deriveRecordIdFromPosterName(
            @NonNull PropertyCategory category,
            @NonNull String posterFileName
    ) {
        String base = posterFileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String upper = base.toUpperCase(Locale.US);
        if (upper.startsWith(category.getIdPrefix())) {
            return base;
        }
        if (base.startsWith(category.getLegacyPosterPrefix())) {
            return category.getLegacyIdPrefix() + base.substring(category.getLegacyPosterPrefix().length());
        }
        return base;
    }

    @NonNull
    private String readExistingTextSafely(@NonNull DocumentFile file) throws IOException {
        ContentResolver resolver = appContext.getContentResolver();
        long reportedLength = file.length();
        try (InputStream in = resolver.openInputStream(file.getUri())) {
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
                throw new IOException("Existing file could not be read reliably.");
            }
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private void writeBytesSafely(@NonNull Uri fileUri, @NonNull byte[] bytes) throws IOException {
        ContentResolver resolver = appContext.getContentResolver();
        OutputStream out = null;
        try {
            try {
                out = resolver.openOutputStream(fileUri, "wt");
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                out = resolver.openOutputStream(fileUri, "w");
            }
            if (out == null) {
                throw new IOException("Could not open output stream.");
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

    @NonNull
    private String safeErrorDetail(Throwable t) {
        String msg = t.getMessage();
        return (msg == null || msg.trim().isEmpty()) ? t.getClass().getSimpleName() : msg.trim();
    }
}
