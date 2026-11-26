package com.propertyhub.offline.storage;

import androidx.annotation.NonNull;

import com.propertyhub.offline.model.PropertyFieldValue;
import com.propertyhub.offline.model.PropertyRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RecordFormatter {

    public static final String RECORD_DIVIDER = "========================================";
    public static final String RECORD_HEADER = "PROPERTY RECORD";

    private static final Pattern DIVIDER_PATTERN = Pattern.compile("(?m)^={10,}\\s*$");

    private RecordFormatter() {
    }

    @NonNull
    public static String formatRecord(@NonNull PropertyRecord record) {
        String normalizedDetails = normalizeLineEndings(record.getDetails());
        StringBuilder sb = new StringBuilder();
        sb.append(RECORD_DIVIDER).append('\n');
        sb.append(RECORD_HEADER).append('\n');
        sb.append("RECORD_ID: ").append(record.getRecordId()).append('\n');
        sb.append("DATE: ").append(record.getDate()).append('\n');
        sb.append("TIME: ").append(record.getTime()).append('\n');
        sb.append("CATEGORY: ").append(record.getCategory().getRecordCategoryCode()).append('\n');
        if (record.hasStructuredFields()) {
            sb.append("TITLE: ").append(record.getTitle().trim()).append('\n');
        }
        sb.append("MOBILE: ").append(record.getMobile().trim()).append('\n');
        if (record.hasStructuredFields()) {
            sb.append("FIELDS_JSON: ")
                    .append(PropertyFieldValue.toJsonArrayString(record.getFieldValues()))
                    .append('\n');
        }
        sb.append("DETAILS:").append('\n');
        sb.append(normalizedDetails).append('\n');
        return sb.toString();
    }

    @NonNull
    public static String appendRecordToContent(
            @NonNull String existingContent,
            @NonNull PropertyRecord record
    ) {
        String block = formatRecord(record);
        if (existingContent.trim().isEmpty()) {
            return block;
        }
        StringBuilder combined = new StringBuilder(existingContent);
        if (!existingContent.endsWith("\n")) {
            combined.append('\n');
        }
        if (!existingContent.endsWith("\n\n")) {
            combined.append('\n');
        }
        combined.append(block);
        return combined.toString();
    }

    @NonNull
    public static String rebuildFileContent(@NonNull List<PropertyRecord> records) {
        String content = "";
        for (PropertyRecord rec : records) {
            content = appendRecordToContent(content, rec);
        }
        return content;
    }

    /**
     * Removes a single record block matching targetRecordId from TXT content while
     * preserving all remaining blocks verbatim.
     */
    @NonNull
    public static RemoveResult removeRecordFromContent(
            @NonNull String existingContent,
            @NonNull String targetRecordId
    ) {
        if (existingContent.trim().isEmpty()) {
            return new RemoveResult(false, "");
        }
        String targetUpper = targetRecordId.trim().toUpperCase(Locale.US);
        String normalized = normalizeLineEndings(existingContent);
        String stripped = DIVIDER_PATTERN.matcher(normalized).replaceAll("\n<<PH_SPLIT>>\n");
        String[] rawBlocks = stripped.split("<<PH_SPLIT>>");

        List<String> keptBlocks = new ArrayList<>();
        boolean removed = false;

        for (String rawBlock : rawBlocks) {
            String trimmed = rawBlock.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (blockMatchesRecordId(trimmed, targetUpper)) {
                removed = true;
            } else {
                keptBlocks.add(RECORD_DIVIDER + "\n" + trimmed + "\n");
            }
        }

        if (!removed) {
            return new RemoveResult(false, existingContent);
        }

        StringBuilder rebuilt = new StringBuilder();
        for (int i = 0; i < keptBlocks.size(); i++) {
            if (i > 0) {
                rebuilt.append('\n');
            }
            rebuilt.append(keptBlocks.get(i));
        }
        return new RemoveResult(true, rebuilt.toString());
    }

    private static boolean blockMatchesRecordId(@NonNull String block, @NonNull String targetUpper) {
        String[] lines = block.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("RECORD_ID:")) {
                String id = t.substring("RECORD_ID:".length()).trim().toUpperCase(Locale.US);
                return id.equals(targetUpper);
            }
            if ("DETAILS:".equals(t) || "Details:".equals(t)) {
                break;
            }
        }
        return false;
    }

    public static final class RemoveResult {
        private final boolean removed;
        private final String updatedContent;

        public RemoveResult(boolean removed, @NonNull String updatedContent) {
            this.removed = removed;
            this.updatedContent = updatedContent;
        }

        public boolean isRemoved() {
            return removed;
        }

        @NonNull
        public String getUpdatedContent() {
            return updatedContent;
        }
    }

    @NonNull
    private static String normalizeLineEndings(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
