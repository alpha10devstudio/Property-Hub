package com.propertyhub.offline.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.AppThemeMode;
import com.propertyhub.offline.model.PropertyCategory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SettingsManager {

    private static final String PREFS_NAME = "property_hub_storage_prefs";
    private static final String KEY_TREE_URI = "persisted_tree_uri";
    private static final String KEY_AUTO_JPG = "auto_jpg_image_by_submit";
    private static final String KEY_THEME_MODE = "app_theme_mode";
    private static final String KEY_LANGUAGE = "app_language";
    private static final String KEY_HOUSE_SEQ = "next_house_record_seq";
    private static final String KEY_LAND_SEQ = "next_land_record_seq";
    private static final String KEY_POSTER_MANIFEST = "generated_poster_manifest_ids";

    private final SharedPreferences prefs;

    public SettingsManager(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    public Uri getSavedTreeUri() {
        String uriString = prefs.getString(KEY_TREE_URI, null);
        if (uriString == null || uriString.trim().isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(uriString);
        } catch (Exception e) {
            return null;
        }
    }

    public void setSavedTreeUri(@Nullable Uri uri) {
        if (uri == null) {
            prefs.edit().remove(KEY_TREE_URI).apply();
        } else {
            prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply();
        }
    }

    public boolean isAutoJpgEnabled() {
        return prefs.getBoolean(KEY_AUTO_JPG, true);
    }

    public void setAutoJpgEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_JPG, enabled).apply();
    }

    @NonNull
    public AppThemeMode getThemeMode() {
        return AppThemeMode.fromPrefValue(
                prefs.getString(KEY_THEME_MODE, AppThemeMode.BLUE_DEFAULT.getPrefValue())
        );
    }

    public void setThemeMode(@NonNull AppThemeMode mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.getPrefValue()).apply();
    }

    @NonNull
    public String getLanguageCode() {
        return prefs.getString(KEY_LANGUAGE, "en");
    }

    public void setLanguageCode(@NonNull String languageCode) {
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    @NonNull
    public String getLanguageDisplayName() {
        String code = getLanguageCode();
        if ("en".equals(code)) {
            return "English";
        }
        return "English";
    }

    public long getLastSequence(@NonNull PropertyCategory category) {
        String key = category == PropertyCategory.HOUSE_FOR_RENT ? KEY_HOUSE_SEQ : KEY_LAND_SEQ;
        return prefs.getLong(key, 0L);
    }

    public void setLastSequence(@NonNull PropertyCategory category, long sequence) {
        String key = category == PropertyCategory.HOUSE_FOR_RENT ? KEY_HOUSE_SEQ : KEY_LAND_SEQ;
        long current = prefs.getLong(key, 0L);
        if (sequence > current) {
            prefs.edit().putLong(key, sequence).commit();
        }
    }

    public synchronized long incrementAndGetSequence(@NonNull PropertyCategory category) {
        String key = category == PropertyCategory.HOUSE_FOR_RENT ? KEY_HOUSE_SEQ : KEY_LAND_SEQ;
        long next = prefs.getLong(key, 0L) + 1L;
        prefs.edit().putLong(key, next).commit();
        return next;
    }

    /**
     * Secondary metadata manifest of generated poster IDs.
     * Never used as proof on its own; StorageManager always reconciles against actual validated JPG files.
     */
    public synchronized void recordPosterManifestEntry(
            @NonNull PropertyCategory category,
            @NonNull String recordId
    ) {
        Set<String> current = new HashSet<>(
                prefs.getStringSet(KEY_POSTER_MANIFEST, Collections.emptySet()));
        current.add(category.name() + "|" + recordId.trim().toUpperCase(Locale.US));
        prefs.edit().putStringSet(KEY_POSTER_MANIFEST, current).apply();
    }

    public synchronized void removePosterManifestEntry(
            @NonNull PropertyCategory category,
            @NonNull String recordId
    ) {
        Set<String> current = new HashSet<>(
                prefs.getStringSet(KEY_POSTER_MANIFEST, Collections.emptySet()));
        if (current.remove(category.name() + "|" + recordId.trim().toUpperCase(Locale.US))) {
            prefs.edit().putStringSet(KEY_POSTER_MANIFEST, current).apply();
        }
    }
}
