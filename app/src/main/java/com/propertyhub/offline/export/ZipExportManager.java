package com.propertyhub.offline.export;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.propertyhub.offline.model.PosterFileItem;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.storage.StorageManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipExportManager {

    public static final class ZipExportResult {
        private final boolean success;
        @Nullable
        private final Uri zipUri;
        @Nullable
        private final String fileName;
        private final int imageCount;
        private final int txtCount;
        @Nullable
        private final String errorMessage;

        private ZipExportResult(
                boolean success,
                @Nullable Uri zipUri,
                @Nullable String fileName,
                int imageCount,
                int txtCount,
                @Nullable String errorMessage
        ) {
            this.success = success;
            this.zipUri = zipUri;
            this.fileName = fileName;
            this.imageCount = imageCount;
            this.txtCount = txtCount;
            this.errorMessage = errorMessage;
        }

        public static ZipExportResult success(
                @NonNull Uri zipUri,
                @NonNull String fileName,
                int imageCount,
                int txtCount
        ) {
            return new ZipExportResult(true, zipUri, fileName, imageCount, txtCount, null);
        }

        public static ZipExportResult error(@NonNull String errorMessage) {
            return new ZipExportResult(false, null, null, 0, 0, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public Uri getZipUri() {
            return zipUri;
        }

        @Nullable
        public String getFileName() {
            return fileName;
        }

        public int getImageCount() {
            return imageCount;
        }

        public int getTxtCount() {
            return txtCount;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public interface Callback {
        void onComplete(@NonNull ZipExportResult result);
    }

    private final Context appContext;
    private final StorageManager storageManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ZipExportManager(@NonNull Context context, @NonNull StorageManager storageManager) {
        this.appContext = context.getApplicationContext();
        this.storageManager = storageManager;
    }

    public void shutdown() {
        executor.shutdown();
    }

    @NonNull
    public static String buildZipFileName(
            @Nullable PropertyCategory categoryFilter,
            @Nullable String startDate,
            @Nullable String endDate
    ) {
        String catPart = categoryFilter == null ? "All_Posters" : categoryFilter.getZipSlug();
        String datePart;
        if (startDate == null && endDate == null) {
            datePart = "All_Dates";
        } else if (startDate != null && startDate.equals(endDate)) {
            datePart = startDate;
        } else if (startDate != null && endDate != null) {
            datePart = startDate + "_to_" + endDate;
        } else if (startDate != null) {
            datePart = "From_" + startDate;
        } else {
            datePart = "UpTo_" + endDate;
        }
        return "Property_Hub_" + catPart + "_" + datePart + ".zip";
    }

    public void exportZipAsync(
            @Nullable PropertyCategory categoryFilter,
            @Nullable String startDateInclusive,
            @Nullable String endDateInclusive,
            boolean includeTxtRecords,
            @NonNull Callback callback
    ) {
        executor.execute(() -> {
            ZipExportResult result = exportZipSync(
                    categoryFilter,
                    startDateInclusive,
                    endDateInclusive,
                    includeTxtRecords
            );
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    @NonNull
    public ZipExportResult exportZipSync(
            @Nullable PropertyCategory categoryFilter,
            @Nullable String startDateInclusive,
            @Nullable String endDateInclusive,
            boolean includeTxtRecords
    ) {
        if (!storageManager.hasValidStorageAccess()) {
            return ZipExportResult.error("Storage folder is not accessible. Please select it in Settings.");
        }

        List<PosterFileItem> posters = storageManager.listEligiblePostersForZip(
                categoryFilter, startDateInclusive, endDateInclusive);

        if (posters.isEmpty()) {
            return ZipExportResult.error("No generated JPG posters found for the selected category and date filter.");
        }

        String zipName = buildZipFileName(categoryFilter, startDateInclusive, endDateInclusive);
        DocumentFile zipDoc;
        try {
            zipDoc = storageManager.createOrReplaceZipFile(zipName);
            if (zipDoc == null) {
                return ZipExportResult.error("Could not create ZIP file in Property Hub/ZIP Exports.");
            }
        } catch (IOException e) {
            return ZipExportResult.error("ZIP creation error: " + e.getMessage());
        }

        Set<String> addedEntryNames = new HashSet<>();
        int imageCount = 0;
        int txtCount = 0;

        try {
            OutputStream rawOut = openTruncatedOutputStream(zipDoc.getUri());
            if (rawOut == null) {
                return ZipExportResult.error("Could not open ZIP file for writing.");
            }

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut))) {
                for (PosterFileItem poster : posters) {
                    String entryPath = poster.getCategory().getFolderName() + "/" + poster.getFileName();
                    if (!addedEntryNames.add(entryPath)) {
                        continue;
                    }
                    byte[] imgBytes = storageManager.readBytesFromUri(poster.getFileUri());
                    if (imgBytes.length == 0) {
                        continue;
                    }
                    ZipEntry entry = new ZipEntry(entryPath);
                    entry.setTime(poster.getLastModifiedMillis());
                    zos.putNextEntry(entry);
                    zos.write(imgBytes);
                    zos.closeEntry();
                    imageCount++;
                }

                if (includeTxtRecords) {
                    List<DocumentFile> txtFiles = storageManager.listTxtFilesForZip(
                            categoryFilter, startDateInclusive, endDateInclusive);
                    for (DocumentFile txt : txtFiles) {
                        String name = txt.getName();
                        if (name == null) {
                            continue;
                        }
                        String folderPrefix = txt.getParentFile() != null && txt.getParentFile().getName() != null
                                ? txt.getParentFile().getName()
                                : "Records";
                        String entryPath = "TXT Records/" + folderPrefix + "/" + name;
                        if (!addedEntryNames.add(entryPath)) {
                            continue;
                        }
                        byte[] txtBytes = storageManager.readBytesFromUri(txt.getUri());
                        if (txtBytes.length == 0) {
                            continue;
                        }
                        ZipEntry entry = new ZipEntry(entryPath);
                        entry.setTime(txt.lastModified());
                        zos.putNextEntry(entry);
                        zos.write(txtBytes);
                        zos.closeEntry();
                        txtCount++;
                    }
                }
                zos.finish();
            }

            if (imageCount == 0) {
                zipDoc.delete();
                return ZipExportResult.error("No valid JPG poster data could be archived.");
            }

            // Verify ZIP integrity by reading back entries
            int verifiedEntries = verifyZipArchive(zipDoc.getUri());
            if (verifiedEntries < imageCount) {
                return ZipExportResult.error("ZIP verification failed: archive appears incomplete.");
            }

            return ZipExportResult.success(zipDoc.getUri(), zipName, imageCount, txtCount);
        } catch (Exception e) {
            return ZipExportResult.error("Failed to create ZIP archive: " + e.getMessage());
        }
    }

    @Nullable
    private OutputStream openTruncatedOutputStream(@NonNull Uri uri) throws IOException {
        try {
            return appContext.getContentResolver().openOutputStream(uri, "wt");
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return appContext.getContentResolver().openOutputStream(uri, "w");
        }
    }

    private int verifyZipArchive(@NonNull Uri zipUri) throws IOException {
        int count = 0;
        try (InputStream in = appContext.getContentResolver().openInputStream(zipUri)) {
            if (in == null) {
                throw new IOException("Cannot open created ZIP for verification.");
            }
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in))) {
                ZipEntry entry;
                byte[] buffer = new byte[4096];
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        while (zis.read(buffer) != -1) {
                            // Stream through entry to validate CRC/stream integrity
                        }
                        count++;
                    }
                    zis.closeEntry();
                }
            }
        }
        return count;
    }
}
