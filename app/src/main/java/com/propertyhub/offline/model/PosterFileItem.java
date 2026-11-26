package com.propertyhub.offline.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PosterFileItem {

    private final PropertyCategory category;
    private final String fileName;
    private final String recordId;
    private final String recordDate;
    private final String sourceTxtFile;
    private final Uri fileUri;
    private final long lastModifiedMillis;
    @Nullable
    private final PropertyRecord linkedRecord;

    public PosterFileItem(
            @NonNull PropertyCategory category,
            @NonNull String fileName,
            @NonNull String recordId,
            @NonNull String recordDate,
            @NonNull String sourceTxtFile,
            @NonNull Uri fileUri,
            long lastModifiedMillis,
            @Nullable PropertyRecord linkedRecord
    ) {
        this.category = category;
        this.fileName = fileName;
        this.recordId = recordId;
        this.recordDate = recordDate;
        this.sourceTxtFile = sourceTxtFile;
        this.fileUri = fileUri;
        this.lastModifiedMillis = lastModifiedMillis;
        this.linkedRecord = linkedRecord;
    }

    @NonNull
    public PropertyCategory getCategory() {
        return category;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public String getRecordId() {
        return recordId;
    }

    @NonNull
    public String getRecordDate() {
        return recordDate;
    }

    @NonNull
    public String getSourceTxtFile() {
        return sourceTxtFile;
    }

    @NonNull
    public Uri getFileUri() {
        return fileUri;
    }

    public long getLastModifiedMillis() {
        return lastModifiedMillis;
    }

    @Nullable
    public PropertyRecord getLinkedRecord() {
        return linkedRecord;
    }
}
