package com.propertyhub.offline.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecordParser {

    private static final Pattern DIVIDER_PATTERN =
            Pattern.compile("(?m)^={10,}\\s*$");
    private static final Pattern LEGACY_HEADER_PATTERN =
            Pattern.compile("^PROPERTY RECORD #(\\d+)\\s*$");

    public static final class ParseResult {
        private final List<PropertyRecord> records;
        private final List<String> warnings;
        private final int invalidCount;

        public ParseResult(
                @NonNull List<PropertyRecord> records,
                @NonNull List<String> warnings,
                int invalidCount
        ) {
            this.records = records;
            this.warnings = warnings;
            this.invalidCount = invalidCount;
        }

        @NonNull
        public List<PropertyRecord> getRecords() {
            return records;
        }

        @NonNull
        public List<String> getWarnings() {
            return warnings;
        }

        public int getInvalidCount() {
            return invalidCount;
        }
    }

    private RecordParser() {
    }

    @NonNull
    public static ParseResult parseFileContent(
            @Nullable String content,
            @Nullable PropertyCategory fallbackCategory,
            @NonNull String sourceFileName
    ) {
        List<PropertyRecord> records = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int invalidCount = 0;

        if (content == null || content.trim().isEmpty()) {
            return new ParseResult(records, warnings, 0);
        }

        PropertyCategory fileHintCategory = fallbackCategory != null
                ? fallbackCategory
                : inferCategoryFromFileName(sourceFileName);

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String stripped = DIVIDER_PATTERN.matcher(normalized).replaceAll("\n<<PH_SPLIT>>\n");
        String[] blocks = stripped.split("<<PH_SPLIT>>");

        int blockIndex = 0;
        for (String rawBlock : blocks) {
            String trimmed = rawBlock.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            List<String> subBlocks = splitLegacySubBlocks(trimmed);
            for (String subBlock : subBlocks) {
                blockIndex++;
                PropertyRecord parsed = parseSingleBlock(
                        subBlock,
                        fileHintCategory,
                        sourceFileName,
                        blockIndex,
                        warnings
                );
                if (parsed != null) {
                    records.add(parsed);
                } else {
                    invalidCount++;
                }
            }
        }

        return new ParseResult(records, warnings, invalidCount);
    }

    @NonNull
    private static List<String> splitLegacySubBlocks(@NonNull String block) {
        List<String> result = new ArrayList<>();
        String[] lines = block.split("\n", -1);
        StringBuilder current = new StringBuilder();
        boolean sawLegacyHeader = false;

        for (String line : lines) {
            if (LEGACY_HEADER_PATTERN.matcher(line.trim()).matches()) {
                if (sawLegacyHeader && current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                sawLegacyHeader = true;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    @Nullable
    private static PropertyRecord parseSingleBlock(
            @NonNull String block,
            @Nullable PropertyCategory fallbackCategory,
            @NonNull String sourceFileName,
            int blockIndex,
            @NonNull List<String> warnings
    ) {
        String[] lines = block.split("\n", -1);
        String recordId = null;
        String legacyNumber = null;
        String date = extractDateFromFileName(sourceFileName);
        String time = "";
        PropertyCategory explicitCategory = null;
        String mobile = "";
        StringBuilder detailsBuilder = new StringBuilder();
        boolean inDetails = false;

        for (String line : lines) {
            if (inDetails) {
                if (detailsBuilder.length() > 0) {
                    detailsBuilder.append('\n');
                }
                detailsBuilder.append(line);
                continue;
            }

            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }
            if (RecordFormatter.RECORD_HEADER.equals(trimmedLine)) {
                continue;
            }

            Matcher legacyMatcher = LEGACY_HEADER_PATTERN.matcher(trimmedLine);
            if (legacyMatcher.matches()) {
                legacyNumber = legacyMatcher.group(1);
                continue;
            }

            if (trimmedLine.startsWith("RECORD_ID:")) {
                recordId = trimmedLine.substring("RECORD_ID:".length()).trim();
            } else if (trimmedLine.startsWith("DATE:")) {
                date = trimmedLine.substring("DATE:".length()).trim();
            } else if (trimmedLine.startsWith("Date:")) {
                date = trimmedLine.substring("Date:".length()).trim();
            } else if (trimmedLine.startsWith("TIME:")) {
                time = trimmedLine.substring("TIME:".length()).trim();
            } else if (trimmedLine.startsWith("Time:")) {
                time = trimmedLine.substring("Time:".length()).trim();
            } else if (trimmedLine.startsWith("CATEGORY:")) {
                PropertyCategory parsedCat = PropertyCategory.fromString(
                        trimmedLine.substring("CATEGORY:".length()).trim()
                );
                if (parsedCat != null) {
                    explicitCategory = parsedCat;
                }
            } else if (trimmedLine.startsWith("Category:")) {
                PropertyCategory parsedCat = PropertyCategory.fromString(
                        trimmedLine.substring("Category:".length()).trim()
                );
                if (parsedCat != null) {
                    explicitCategory = parsedCat;
                }
            } else if (trimmedLine.startsWith("MOBILE:")) {
                mobile = trimmedLine.substring("MOBILE:".length()).trim();
            } else if (trimmedLine.startsWith("Mobile:")) {
                mobile = trimmedLine.substring("Mobile:".length()).trim();
            } else if ("DETAILS:".equals(trimmedLine) || "Details:".equals(trimmedLine)) {
                inDetails = true;
            } else if (trimmedLine.startsWith("DETAILS:")) {
                inDetails = true;
                String inline = line.substring(line.indexOf("DETAILS:") + "DETAILS:".length()).trim();
                if (!inline.isEmpty()) {
                    detailsBuilder.append(inline);
                }
            } else if (trimmedLine.startsWith("Details:")) {
                inDetails = true;
                String inline = line.substring(line.indexOf("Details:") + "Details:".length()).trim();
                if (!inline.isEmpty()) {
                    detailsBuilder.append(inline);
                }
            }
        }

        String details = trimTrailingNewlines(detailsBuilder.toString());

        PropertyCategory category = explicitCategory;
        if (category == null && recordId != null) {
            category = inferCategoryFromRecordId(recordId);
        }
        if (category == null) {
            category = fallbackCategory;
        }

        if (category == null) {
            warnings.add(sourceFileName + " (entry #" + blockIndex + "): Unknown property category.");
            return null;
        }

        if (mobile.isEmpty() || details.trim().isEmpty()) {
            warnings.add(sourceFileName + " (entry #" + blockIndex + "): Missing required mobile or details.");
            return null;
        }

        if (recordId == null || recordId.isEmpty()) {
            String compactDate = date.replaceAll("[^0-9]", "");
            if (compactDate.isEmpty()) {
                compactDate = "UNKNOWN";
            }
            String numPart = legacyNumber != null
                    ? legacyNumber
                    : String.format(Locale.US, "%03d", blockIndex);
            recordId = category.getIdPrefix() + "LEGACY_" + compactDate + "_" + numPart;
        }

        return new PropertyRecord(
                recordId,
                date,
                time,
                category,
                mobile,
                details,
                sourceFileName
        );
    }

    @Nullable
    public static PropertyCategory inferCategoryFromRecordId(@NonNull String recordId) {
        String upper = recordId.trim().toUpperCase(Locale.US);
        if (upper.startsWith("HOUSE_")) {
            return PropertyCategory.HOUSE_FOR_RENT;
        }
        if (upper.startsWith("LAND_")) {
            return PropertyCategory.LAND_FOR_SALE;
        }
        return null;
    }

    @Nullable
    public static PropertyCategory inferCategoryFromFileName(@Nullable String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.contains("house") || lower.contains("rent")) {
            return PropertyCategory.HOUSE_FOR_RENT;
        }
        if (lower.contains("land") || lower.contains("sale")) {
            return PropertyCategory.LAND_FOR_SALE;
        }
        return null;
    }

    @NonNull
    private static String extractDateFromFileName(@NonNull String fileName) {
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < base.length()) {
            base = base.substring(slash + 1);
        }
        if (base.endsWith(".txt") && base.length() >= 14) {
            String candidate = base.substring(0, 10);
            if (candidate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return candidate;
            }
        }
        return "";
    }

    @NonNull
    private static String trimTrailingNewlines(@NonNull String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\n') {
            end--;
        }
        return text.substring(0, end);
    }
}
