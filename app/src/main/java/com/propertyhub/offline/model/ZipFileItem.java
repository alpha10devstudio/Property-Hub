package com.propertyhub.offline.model;

import android.net.Uri;

import androidx.annotation.NonNull;

public class ZipFileItem {

    private final String fileName;
    private final Uri fileUri;
    private final long sizeBytes;
    private final long lastModifiedMillis;

    public ZipFileItem(
            @NonNull String fileName,
            @NonNull Uri fileUri,
            long sizeBytes,
            long lastModifiedMillis
    ) {
        this.fileName = fileName;
        this.fileUri = fileUri;
        this.sizeBytes = sizeBytes;
        this.lastModifiedMillis = lastModifiedMillis;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public Uri getFileUri() {
        return fileUri;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getLastModifiedMillis() {
        return lastModifiedMillis;
    }
}
