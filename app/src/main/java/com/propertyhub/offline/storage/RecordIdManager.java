package com.propertyhub.offline.storage;

import androidx.annotation.NonNull;

import com.propertyhub.offline.model.PropertyCategory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecordIdManager {

    private static final Pattern RENT_DATED_ID_PATTERN = Pattern.compile("^PH_RENT_\\d{8}_(\\d+)$");
    private static final Pattern LAND_DATED_ID_PATTERN = Pattern.compile("^PH_LAND_\\d{8}_(\\d+)$");
    private static final Pattern LEGACY_HOUSE_ID_PATTERN = Pattern.compile("^HOUSE_(\\d+)$");
    private static final Pattern LEGACY_LAND_ID_PATTERN = Pattern.compile("^LAND_(\\d+)$");

    private final SettingsManager settingsManager;

    public RecordIdManager(@NonNull SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @NonNull
    public synchronized String allocateNextRecordId(
            @NonNull PropertyCategory category,
            @NonNull String dateYmd,
            long observedMaxSequence
    ) {
        if (observedMaxSequence > settingsManager.getLastSequence(category)) {
            settingsManager.setLastSequence(category, observedMaxSequence);
        }
        long nextSeq = settingsManager.incrementAndGetSequence(category);
        return formatDatedId(category, dateYmd, nextSeq);
    }

    @NonNull
    public synchronized String allocateNextRecordId(
            @NonNull PropertyCategory category,
            long observedMaxSequence
    ) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return allocateNextRecordId(category, today, observedMaxSequence);
    }

    @NonNull
    public static String formatDatedId(
            @NonNull PropertyCategory category,
            @NonNull String dateYmd,
            long sequence
    ) {
        String compactDate = dateYmd.replaceAll("[^0-9]", "");
        if (compactDate.length() != 8) {
            compactDate = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        }
        return String.format(Locale.US, "%s%s_%04d", category.getIdPrefix(), compactDate, sequence);
    }

    @NonNull
    public static String formatId(@NonNull PropertyCategory category, long sequence) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return formatDatedId(category, today, sequence);
    }

    public static long extractNumericSuffix(
            @NonNull PropertyCategory category,
            @NonNull String recordId
    ) {
        String upper = recordId.trim().toUpperCase(Locale.US);
        Pattern dated = category == PropertyCategory.HOUSE_FOR_RENT
                ? RENT_DATED_ID_PATTERN
                : LAND_DATED_ID_PATTERN;
        Matcher datedMatcher = dated.matcher(upper);
        if (datedMatcher.matches()) {
            return parseLongSafe(datedMatcher.group(1));
        }

        Pattern legacy = category == PropertyCategory.HOUSE_FOR_RENT
                ? LEGACY_HOUSE_ID_PATTERN
                : LEGACY_LAND_ID_PATTERN;
        Matcher legacyMatcher = legacy.matcher(upper);
        if (legacyMatcher.matches()) {
            return parseLongSafe(legacyMatcher.group(1));
        }
        return 0L;
    }

    private static long parseLongSafe(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
