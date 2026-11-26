package com.propertyhub.offline;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecordFormatter {

    public static final String FILE_HEADER_DIVIDER = "====================================";
    private static final Pattern RECORD_NUMBER_PATTERN =
            Pattern.compile("^PROPERTY RECORD #(\\d+)\\s*$", Pattern.MULTILINE);

    private RecordFormatter() {
    }

    public static int extractHighestRecordNumber(String existingContent) {
        if (existingContent == null || existingContent.trim().isEmpty()) {
            return 0;
        }
        int maxNumber = 0;
        Matcher matcher = RECORD_NUMBER_PATTERN.matcher(existingContent);
        while (matcher.find()) {
            try {
                int parsed = Integer.parseInt(matcher.group(1));
                if (parsed > maxNumber) {
                    maxNumber = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return maxNumber;
    }

    public static String formatSingleRecord(
            int recordNumber,
            String dateStr,
            String timeStr,
            PropertyCategory category,
            String mobile,
            String details,
            boolean includeHeaderDivider
    ) {
        String cleanMobile = mobile == null ? "" : mobile.trim();
        String cleanDetails = normalizeDetails(details);

        StringBuilder sb = new StringBuilder();
        if (includeHeaderDivider) {
            sb.append(FILE_HEADER_DIVIDER).append('\n');
        }
        sb.append(String.format(Locale.US, "PROPERTY RECORD #%03d", recordNumber)).append('\n');
        sb.append("Date: ").append(dateStr).append('\n');
        sb.append("Time: ").append(timeStr).append('\n');
        sb.append("Category: ").append(category.getDisplayName()).append('\n');
        sb.append("Mobile: ").append(cleanMobile).append('\n');
        sb.append("Details:").append('\n');
        sb.append(cleanDetails).append('\n');
        return sb.toString();
    }

    public static String appendRecordToContent(
            String existingContent,
            int recordNumber,
            String dateStr,
            String timeStr,
            PropertyCategory category,
            String mobile,
            String details
    ) {
        boolean isBlankFile = existingContent == null || existingContent.trim().isEmpty();
        if (isBlankFile) {
            return formatSingleRecord(
                    recordNumber,
                    dateStr,
                    timeStr,
                    category,
                    mobile,
                    details,
                    true
            );
        }

        StringBuilder combined = new StringBuilder(existingContent);
        if (existingContent.endsWith("\n\n")) {
            // Already has blank line separator
        } else if (existingContent.endsWith("\n")) {
            combined.append('\n');
        } else {
            combined.append("\n\n");
        }

        combined.append(formatSingleRecord(
                recordNumber,
                dateStr,
                timeStr,
                category,
                mobile,
                details,
                false
        ));
        return combined.toString();
    }

    private static String normalizeDetails(String details) {
        if (details == null) {
            return "";
        }
        String normalized = details.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.trim();
    }
}
