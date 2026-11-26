package com.propertyhub.offline.storage;

import androidx.annotation.NonNull;

import com.propertyhub.offline.model.PropertyRecord;

public final class RecordFormatter {

    public static final String RECORD_DIVIDER = "========================================";
    public static final String RECORD_HEADER = "PROPERTY RECORD";

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
        sb.append("MOBILE: ").append(record.getMobile().trim()).append('\n');
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
    private static String normalizeLineEndings(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
