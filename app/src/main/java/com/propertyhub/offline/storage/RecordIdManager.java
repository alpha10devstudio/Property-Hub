package com.propertyhub.offline.storage;

import androidx.annotation.NonNull;

import com.propertyhub.offline.model.PropertyCategory;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecordIdManager {

    private static final Pattern HOUSE_ID_PATTERN = Pattern.compile("^HOUSE_(\\d+)$");
    private static final Pattern LAND_ID_PATTERN = Pattern.compile("^LAND_(\\d+)$");

    private final SettingsManager settingsManager;

    public RecordIdManager(@NonNull SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @NonNull
    public synchronized String allocateNextRecordId(
            @NonNull PropertyCategory category,
            long observedMaxSequence
    ) {
        if (observedMaxSequence > settingsManager.getLastSequence(category)) {
            settingsManager.setLastSequence(category, observedMaxSequence);
        }
        long nextSeq = settingsManager.incrementAndGetSequence(category);
        return formatId(category, nextSeq);
    }

    @NonNull
    public static String formatId(@NonNull PropertyCategory category, long sequence) {
        return String.format(Locale.US, "%s%06d", category.getIdPrefix(), sequence);
    }

    public static long extractNumericSuffix(
            @NonNull PropertyCategory category,
            @NonNull String recordId
    ) {
        Pattern pattern = category == PropertyCategory.HOUSE_FOR_RENT
                ? HOUSE_ID_PATTERN
                : LAND_ID_PATTERN;
        Matcher matcher = pattern.matcher(recordId.trim().toUpperCase(Locale.US));
        if (matcher.matches()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
