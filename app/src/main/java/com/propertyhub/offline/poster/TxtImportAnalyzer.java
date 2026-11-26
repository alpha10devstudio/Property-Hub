package com.propertyhub.offline.poster;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.storage.RecordParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TxtImportAnalyzer {

    public static final class ImportedTxtSource {
        private final String displayName;
        private final String content;
        @Nullable
        private final PropertyCategory categoryHint;

        public ImportedTxtSource(
                @NonNull String displayName,
                @NonNull String content,
                @Nullable PropertyCategory categoryHint
        ) {
            this.displayName = displayName;
            this.content = content;
            this.categoryHint = categoryHint;
        }

        @NonNull
        public String getDisplayName() {
            return displayName;
        }

        @NonNull
        public String getContent() {
            return content;
        }

        @Nullable
        public PropertyCategory getCategoryHint() {
            return categoryHint;
        }
    }

    public static final class RecordConflict {
        private final String normalizedKey;
        private final PropertyRecord firstRecord;
        private final PropertyRecord conflictingRecord;
        private final String reason;

        public RecordConflict(
                @NonNull String normalizedKey,
                @NonNull PropertyRecord firstRecord,
                @NonNull PropertyRecord conflictingRecord,
                @NonNull String reason
        ) {
            this.normalizedKey = normalizedKey;
            this.firstRecord = firstRecord;
            this.conflictingRecord = conflictingRecord;
            this.reason = reason;
        }

        @NonNull
        public String getNormalizedKey() {
            return normalizedKey;
        }

        @NonNull
        public PropertyRecord getFirstRecord() {
            return firstRecord;
        }

        @NonNull
        public PropertyRecord getConflictingRecord() {
            return conflictingRecord;
        }

        @NonNull
        public String getReason() {
            return reason;
        }
    }

    public static final class ImportAnalysisResult {
        private final int totalFiles;
        private final int totalRecordsScanned;
        private final int invalidRecordsCount;
        private final int duplicateRecordsSkippedCount;
        private final List<PropertyRecord> uniqueCleanRecords;
        private final List<RecordConflict> conflicts;
        private final List<String> warnings;

        public ImportAnalysisResult(
                int totalFiles,
                int totalRecordsScanned,
                int invalidRecordsCount,
                int duplicateRecordsSkippedCount,
                @NonNull List<PropertyRecord> uniqueCleanRecords,
                @NonNull List<RecordConflict> conflicts,
                @NonNull List<String> warnings
        ) {
            this.totalFiles = totalFiles;
            this.totalRecordsScanned = totalRecordsScanned;
            this.invalidRecordsCount = invalidRecordsCount;
            this.duplicateRecordsSkippedCount = duplicateRecordsSkippedCount;
            this.uniqueCleanRecords = uniqueCleanRecords;
            this.conflicts = conflicts;
            this.warnings = warnings;
        }

        public int getTotalFiles() {
            return totalFiles;
        }

        public int getTotalRecordsScanned() {
            return totalRecordsScanned;
        }

        public int getInvalidRecordsCount() {
            return invalidRecordsCount;
        }

        public int getDuplicateRecordsSkippedCount() {
            return duplicateRecordsSkippedCount;
        }

        @NonNull
        public List<PropertyRecord> getUniqueCleanRecords() {
            return uniqueCleanRecords;
        }

        @NonNull
        public List<RecordConflict> getConflicts() {
            return conflicts;
        }

        @NonNull
        public List<String> getWarnings() {
            return warnings;
        }
    }

    private TxtImportAnalyzer() {
    }

    @NonNull
    public static ImportAnalysisResult analyze(@NonNull List<ImportedTxtSource> sources) {
        int totalValidParsed = 0;
        int totalInvalid = 0;
        int duplicateSkipped = 0;
        List<String> warnings = new ArrayList<>();

        Map<String, PropertyRecord> firstByKey = new LinkedHashMap<>();
        Set<String> conflictedKeys = new LinkedHashSet<>();
        List<RecordConflict> conflicts = new ArrayList<>();

        for (ImportedTxtSource source : sources) {
            RecordParser.ParseResult parsed = RecordParser.parseFileContent(
                    source.getContent(),
                    source.getCategoryHint(),
                    source.getDisplayName()
            );

            totalInvalid += parsed.getInvalidCount();
            warnings.addAll(parsed.getWarnings());

            for (PropertyRecord rec : parsed.getRecords()) {
                totalValidParsed++;
                String key = buildIdentityKey(rec.getRecordId());
                PropertyRecord existing = firstByKey.get(key);

                if (existing == null) {
                    firstByKey.put(key, rec);
                } else if (hasConflictingContent(existing, rec)) {
                    conflictedKeys.add(key);
                    String reason = describeConflict(existing, rec);
                    conflicts.add(new RecordConflict(key, existing, rec, reason));
                    warnings.add("Conflict on " + rec.getRecordId() + ": " + reason);
                } else {
                    duplicateSkipped++;
                }
            }
        }

        // Do not silently choose a winner for conflicted IDs; keep clean non-conflicted records ready
        List<PropertyRecord> cleanUnique = new ArrayList<>();
        for (Map.Entry<String, PropertyRecord> entry : firstByKey.entrySet()) {
            if (!conflictedKeys.contains(entry.getKey())) {
                cleanUnique.add(entry.getValue());
            }
        }

        int totalScanned = totalValidParsed + totalInvalid;
        return new ImportAnalysisResult(
                sources.size(),
                totalScanned,
                totalInvalid,
                duplicateSkipped,
                cleanUnique,
                conflicts,
                warnings
        );
    }

    @NonNull
    public static String buildIdentityKey(@NonNull String recordId) {
        return recordId.trim().toUpperCase(Locale.US);
    }

    private static boolean hasConflictingContent(
            @NonNull PropertyRecord a,
            @NonNull PropertyRecord b
    ) {
        if (a.getCategory() != b.getCategory()) {
            return true;
        }
        if (!normalizeField(a.getMobile()).equals(normalizeField(b.getMobile()))) {
            return true;
        }
        return !normalizeDetails(a.getDetails()).equals(normalizeDetails(b.getDetails()));
    }

    @NonNull
    private static String describeConflict(
            @NonNull PropertyRecord first,
            @NonNull PropertyRecord second
    ) {
        List<String> diffs = new ArrayList<>();
        if (first.getCategory() != second.getCategory()) {
            diffs.add("category (" + first.getCategory().getDisplayName()
                    + " vs " + second.getCategory().getDisplayName() + ")");
        }
        if (!normalizeField(first.getMobile()).equals(normalizeField(second.getMobile()))) {
            diffs.add("mobile (" + first.getMobile() + " vs " + second.getMobile() + ")");
        }
        if (!normalizeDetails(first.getDetails()).equals(normalizeDetails(second.getDetails()))) {
            diffs.add("details differ");
        }
        return joinReasons(diffs) + " [" + first.getSourceFileName() + " vs " + second.getSourceFileName() + "]";
    }

    @NonNull
    private static String joinReasons(@NonNull List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    @NonNull
    private static String normalizeField(@NonNull String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    @NonNull
    private static String normalizeDetails(@NonNull String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
