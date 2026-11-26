package com.propertyhub.offline.poster;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.storage.RecordParser;
import com.propertyhub.offline.storage.StorageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BulkPosterManager {

    public static final class ImportPreparationSummary {
        public final int totalFiles;
        public final int totalRecordsScanned;
        public final int alreadyGeneratedCount;
        public final int invalidRecordsCount;
        public final int duplicateRecordsSkippedCount;
        public final List<PropertyRecord> missingRecordsQueue;
        public final List<TxtImportAnalyzer.RecordConflict> conflicts;
        public final List<String> warnings;

        public ImportPreparationSummary(
                int totalFiles,
                int totalRecordsScanned,
                int alreadyGeneratedCount,
                int invalidRecordsCount,
                int duplicateRecordsSkippedCount,
                @NonNull List<PropertyRecord> missingRecordsQueue,
                @NonNull List<TxtImportAnalyzer.RecordConflict> conflicts,
                @NonNull List<String> warnings
        ) {
            this.totalFiles = totalFiles;
            this.totalRecordsScanned = totalRecordsScanned;
            this.alreadyGeneratedCount = alreadyGeneratedCount;
            this.invalidRecordsCount = invalidRecordsCount;
            this.duplicateRecordsSkippedCount = duplicateRecordsSkippedCount;
            this.missingRecordsQueue = missingRecordsQueue;
            this.conflicts = conflicts;
            this.warnings = warnings;
        }

        public int getPendingCount() {
            return missingRecordsQueue.size();
        }
    }

    public static final class BulkProgress {
        public final int totalRecords;
        public final int alreadyExistsCount;
        public final int waitingCount;
        public final int succeededCount;
        public final int failedCount;
        public final int invalidRecordsCount;
        public final int duplicateRecordsSkippedCount;
        public final int processedCount;
        public final int totalToProcess;
        public final String currentRecordId;
        public final boolean running;
        public final boolean cancelled;
        public final List<PropertyRecord> failedRecords;
        public final List<String> warnings;

        public BulkProgress(
                int totalRecords,
                int alreadyExistsCount,
                int waitingCount,
                int succeededCount,
                int failedCount,
                int invalidRecordsCount,
                int duplicateRecordsSkippedCount,
                int processedCount,
                int totalToProcess,
                @NonNull String currentRecordId,
                boolean running,
                boolean cancelled,
                @NonNull List<PropertyRecord> failedRecords,
                @NonNull List<String> warnings
        ) {
            this.totalRecords = totalRecords;
            this.alreadyExistsCount = alreadyExistsCount;
            this.waitingCount = waitingCount;
            this.succeededCount = succeededCount;
            this.failedCount = failedCount;
            this.invalidRecordsCount = invalidRecordsCount;
            this.duplicateRecordsSkippedCount = duplicateRecordsSkippedCount;
            this.processedCount = processedCount;
            this.totalToProcess = totalToProcess;
            this.currentRecordId = currentRecordId;
            this.running = running;
            this.cancelled = cancelled;
            this.failedRecords = failedRecords;
            this.warnings = warnings;
        }

        public int getProgressPercent() {
            if (totalToProcess <= 0) {
                return 100;
            }
            return Math.min(100, (int) Math.round((processedCount * 100.0) / totalToProcess));
        }
    }

    public interface ImportPreparationListener {
        void onPrepared(@NonNull ImportPreparationSummary summary);
    }

    public interface ProgressListener {
        void onProgress(@NonNull BulkProgress progress);
    }

    private final StorageManager storageManager;
    private final PosterRenderer posterRenderer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public BulkPosterManager(
            @NonNull StorageManager storageManager,
            @NonNull PosterRenderer posterRenderer
    ) {
        this.storageManager = storageManager;
        this.posterRenderer = posterRenderer;
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public void requestCancel() {
        cancelRequested.set(true);
    }

    public void shutdown() {
        cancelRequested.set(true);
        executor.shutdown();
    }

    /**
     * Reads imported TXT URIs in read-only mode (never modifying or deleting source files),
     * deduplicates by stable RECORD_ID, detects conflicts, and checks existing JPG integrity
     * across all poster folders. Independent of Auto JPG submit setting.
     */
    public void analyzeImportedTxtUrisAsync(
            @NonNull List<Uri> txtUris,
            @NonNull ImportPreparationListener listener
    ) {
        executor.execute(() -> {
            List<TxtImportAnalyzer.ImportedTxtSource> sources = new ArrayList<>();
            List<String> ioWarnings = new ArrayList<>();

            for (Uri uri : txtUris) {
                String name = storageManager.resolveDisplayName(uri);
                try {
                    String content = storageManager.readTextFromExternalUri(uri);
                    PropertyCategory hint = RecordParser.inferCategoryFromFileName(name);
                    sources.add(new TxtImportAnalyzer.ImportedTxtSource(name, content, hint));
                } catch (Exception e) {
                    ioWarnings.add("Could not read \"" + name + "\": " + e.getMessage());
                }
            }

            TxtImportAnalyzer.ImportAnalysisResult analysis = TxtImportAnalyzer.analyze(sources);
            Map<String, Uri> validPosterIndex = storageManager.getValidPosterIndex(null);

            List<PropertyRecord> missingQueue = new ArrayList<>();
            int alreadyGenerated = 0;

            for (PropertyRecord record : analysis.getUniqueCleanRecords()) {
                String expectedPosterKey = record.getPosterFileName().toLowerCase(java.util.Locale.US);
                if (validPosterIndex.containsKey(expectedPosterKey)) {
                    alreadyGenerated++;
                } else {
                    missingQueue.add(record);
                }
            }

            List<String> allWarnings = new ArrayList<>(ioWarnings);
            allWarnings.addAll(analysis.getWarnings());

            ImportPreparationSummary summary = new ImportPreparationSummary(
                    txtUris.size(),
                    analysis.getTotalRecordsScanned(),
                    alreadyGenerated,
                    analysis.getInvalidRecordsCount(),
                    analysis.getDuplicateRecordsSkippedCount(),
                    missingQueue,
                    analysis.getConflicts(),
                    allWarnings
            );

            mainHandler.post(() -> listener.onPrepared(summary));
        });
    }

    public void startImportedBulkGeneration(
            @NonNull ImportPreparationSummary summary,
            @NonNull List<PropertyRecord> resolvedConflictChoices,
            @NonNull ProgressListener listener
    ) {
        if (!isRunning.compareAndSet(false, true)) {
            return;
        }
        cancelRequested.set(false);

        executor.execute(() -> {
            try {
                List<PropertyRecord> combinedCandidates = new ArrayList<>(summary.missingRecordsQueue);
                combinedCandidates.addAll(resolvedConflictChoices);

                List<PropertyRecord> queue = new ArrayList<>();
                Set<String> seenIds = new LinkedHashSet<>();
                int alreadyExists = summary.alreadyGeneratedCount;
                int extraDuplicates = 0;

                for (PropertyRecord record : combinedCandidates) {
                    String idKey = TxtImportAnalyzer.buildIdentityKey(record.getRecordId());
                    if (!seenIds.add(idKey)) {
                        extraDuplicates++;
                        continue;
                    }
                    if (storageManager.findValidPosterUri(record.getCategory(), record.getRecordId()) != null) {
                        alreadyExists++;
                    } else {
                        queue.add(record);
                    }
                }

                processQueue(
                        summary.totalRecordsScanned,
                        alreadyExists,
                        summary.invalidRecordsCount,
                        summary.duplicateRecordsSkippedCount + extraDuplicates,
                        queue,
                        new ArrayList<>(summary.warnings),
                        listener
                );
            } finally {
                isRunning.set(false);
            }
        });
    }

    public void startBulkGeneration(
            @Nullable PropertyCategory categoryFilter,
            @Nullable String startDateInclusive,
            @Nullable String endDateInclusive,
            @NonNull ProgressListener listener
    ) {
        if (!isRunning.compareAndSet(false, true)) {
            return;
        }
        cancelRequested.set(false);

        executor.execute(() -> {
            try {
                StorageManager.RecordsQueryResult queryResult =
                        storageManager.loadRecords(categoryFilter, startDateInclusive, endDateInclusive);

                List<PropertyRecord> allRecords = queryResult.getRecords();
                List<String> warnings = new ArrayList<>(queryResult.getWarnings());

                List<PropertyRecord> queue = new ArrayList<>();
                Set<String> seenIds = new LinkedHashSet<>();
                int existingCount = 0;
                int duplicateCount = 0;

                for (PropertyRecord record : allRecords) {
                    String idKey = TxtImportAnalyzer.buildIdentityKey(record.getRecordId());
                    if (!seenIds.add(idKey)) {
                        duplicateCount++;
                        continue;
                    }
                    if (storageManager.findValidPosterUri(record.getCategory(), record.getRecordId()) != null) {
                        existingCount++;
                    } else {
                        queue.add(record);
                    }
                }

                processQueue(
                        allRecords.size(),
                        existingCount,
                        0,
                        duplicateCount,
                        queue,
                        warnings,
                        listener
                );
            } finally {
                isRunning.set(false);
            }
        });
    }

    public void retryFailedRecords(
            @NonNull List<PropertyRecord> failedToRetry,
            @NonNull ProgressListener listener
    ) {
        if (failedToRetry.isEmpty() || !isRunning.compareAndSet(false, true)) {
            return;
        }
        cancelRequested.set(false);
        final List<PropertyRecord> queue = new ArrayList<>(failedToRetry);

        executor.execute(() -> {
            try {
                processQueue(
                        queue.size(),
                        0,
                        0,
                        0,
                        queue,
                        new ArrayList<>(),
                        listener
                );
            } finally {
                isRunning.set(false);
            }
        });
    }

    private void processQueue(
            int totalRecordsScanned,
            int alreadyExistsCount,
            int invalidRecordsCount,
            int duplicateRecordsSkippedCount,
            @NonNull List<PropertyRecord> queue,
            @NonNull List<String> warnings,
            @NonNull ProgressListener listener
    ) {
        int totalToProcess = queue.size();
        int succeeded = 0;
        int failed = 0;
        int processed = 0;
        List<PropertyRecord> failedList = new ArrayList<>();

        postProgress(listener, new BulkProgress(
                totalRecordsScanned,
                alreadyExistsCount,
                totalToProcess,
                0,
                0,
                invalidRecordsCount,
                duplicateRecordsSkippedCount,
                0,
                totalToProcess,
                totalToProcess > 0 ? queue.get(0).getRecordId() : "-",
                totalToProcess > 0,
                false,
                Collections.emptyList(),
                new ArrayList<>(warnings)
        ));

        for (PropertyRecord record : queue) {
            if (cancelRequested.get()) {
                postProgress(listener, new BulkProgress(
                        totalRecordsScanned,
                        alreadyExistsCount,
                        totalToProcess - processed,
                        succeeded,
                        failed,
                        invalidRecordsCount,
                        duplicateRecordsSkippedCount,
                        processed,
                        totalToProcess,
                        "Cancelled",
                        false,
                        true,
                        new ArrayList<>(failedList),
                        new ArrayList<>(warnings)
                ));
                return;
            }

            // Double-check verified valid poster existence right before rendering; never overwrite valid JPG
            if (storageManager.findValidPosterUri(record.getCategory(), record.getRecordId()) != null) {
                alreadyExistsCount++;
                processed++;
                continue;
            }

            try {
                PosterRenderResult renderResult = posterRenderer.renderPoster(record);
                if (!renderResult.isSuccess() || renderResult.getJpegBytes() == null) {
                    failed++;
                    failedList.add(record);
                    warnings.add(record.getRecordId() + ": "
                            + (renderResult.getErrorMessage() != null
                            ? renderResult.getErrorMessage()
                            : "Rendering error"));
                } else {
                    if (renderResult.hasTextOverflowWarning()) {
                        warnings.add(record.getRecordId()
                                + ": Details text was very long; please review generated poster.");
                    }
                    Uri saved = storageManager.savePosterJpeg(
                            record.getCategory(),
                            record.getRecordId(),
                            renderResult.getJpegBytes(),
                            false
                    );
                    if (saved != null) {
                        succeeded++;
                        record.setPosterGenerated(true);
                        record.setPosterUri(saved);
                    } else {
                        failed++;
                        failedList.add(record);
                        warnings.add(record.getRecordId() + ": Could not write JPG file.");
                    }
                }
            } catch (Exception e) {
                failed++;
                failedList.add(record);
                warnings.add(record.getRecordId() + ": " + e.getMessage());
            }

            processed++;
            postProgress(listener, new BulkProgress(
                    totalRecordsScanned,
                    alreadyExistsCount,
                    Math.max(0, totalToProcess - processed),
                    succeeded,
                    failed,
                    invalidRecordsCount,
                    duplicateRecordsSkippedCount,
                    processed,
                    totalToProcess,
                    record.getRecordId(),
                    processed < totalToProcess,
                    false,
                    new ArrayList<>(failedList),
                    new ArrayList<>(warnings)
            ));
        }

        postProgress(listener, new BulkProgress(
                totalRecordsScanned,
                alreadyExistsCount,
                0,
                succeeded,
                failed,
                invalidRecordsCount,
                duplicateRecordsSkippedCount,
                processed,
                totalToProcess,
                "Completed",
                false,
                false,
                new ArrayList<>(failedList),
                new ArrayList<>(warnings)
        ));
    }

    private void postProgress(
            @NonNull ProgressListener listener,
            @NonNull BulkProgress progress
    ) {
        mainHandler.post(() -> listener.onProgress(progress));
    }
}
