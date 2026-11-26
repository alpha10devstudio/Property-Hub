package com.propertyhub.offline.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public class PropertyRecord {

    private final String recordId;
    private final String date;
    private final String time;
    private final PropertyCategory category;
    private final String mobile;
    private final String details;
    private final String sourceFileName;
    private boolean posterGenerated;
    @Nullable
    private Uri posterUri;

    public PropertyRecord(
            @NonNull String recordId,
            @NonNull String date,
            @NonNull String time,
            @NonNull PropertyCategory category,
            @NonNull String mobile,
            @NonNull String details,
            @NonNull String sourceFileName
    ) {
        this.recordId = recordId;
        this.date = date;
        this.time = time;
        this.category = category;
        this.mobile = mobile;
        this.details = details;
        this.sourceFileName = sourceFileName;
    }

    @NonNull
    public String getRecordId() {
        return recordId;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    @NonNull
    public String getTime() {
        return time;
    }

    @NonNull
    public PropertyCategory getCategory() {
        return category;
    }

    @NonNull
    public String getMobile() {
        return mobile;
    }

    @NonNull
    public String getDetails() {
        return details;
    }

    @NonNull
    public String getSourceFileName() {
        return sourceFileName;
    }

    public boolean isPosterGenerated() {
        return posterGenerated;
    }

    public void setPosterGenerated(boolean posterGenerated) {
        this.posterGenerated = posterGenerated;
    }

    @Nullable
    public Uri getPosterUri() {
        return posterUri;
    }

    public void setPosterUri(@Nullable Uri posterUri) {
        this.posterUri = posterUri;
    }

    @NonNull
    public String getPosterFileName() {
        return buildPosterFileName(category, recordId);
    }

    @NonNull
    public static String buildPosterFileName(@NonNull PropertyCategory category, @NonNull String recordId) {
        String upper = recordId.toUpperCase(Locale.US);
        String prefix = category.getIdPrefix();
        String suffix;
        if (upper.startsWith(prefix)) {
            suffix = recordId.substring(prefix.length());
        } else {
            suffix = recordId;
        }
        String safeSuffix = suffix.replaceAll("[^A-Za-z0-9_-]", "_");
        return category.getPosterPrefix() + safeSuffix + ".jpg";
    }
}
