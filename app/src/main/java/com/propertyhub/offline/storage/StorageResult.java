package com.propertyhub.offline.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;

public final class StorageResult {

    private final boolean success;
    private final boolean permissionMissing;
    private final PropertyCategory category;
    @Nullable
    private final PropertyRecord savedRecord;
    @Nullable
    private final String fileName;
    @Nullable
    private final String displayPath;
    @Nullable
    private final String errorMessage;

    private StorageResult(
            boolean success,
            boolean permissionMissing,
            @NonNull PropertyCategory category,
            @Nullable PropertyRecord savedRecord,
            @Nullable String fileName,
            @Nullable String displayPath,
            @Nullable String errorMessage
    ) {
        this.success = success;
        this.permissionMissing = permissionMissing;
        this.category = category;
        this.savedRecord = savedRecord;
        this.fileName = fileName;
        this.displayPath = displayPath;
        this.errorMessage = errorMessage;
    }

    public static StorageResult success(
            @NonNull PropertyCategory category,
            @NonNull PropertyRecord savedRecord,
            @NonNull String fileName,
            @NonNull String displayPath
    ) {
        return new StorageResult(true, false, category, savedRecord, fileName, displayPath, null);
    }

    public static StorageResult permissionMissing(
            @NonNull PropertyCategory category,
            @NonNull String errorMessage
    ) {
        return new StorageResult(false, true, category, null, null, null, errorMessage);
    }

    public static StorageResult error(
            @NonNull PropertyCategory category,
            @NonNull String errorMessage
    ) {
        return new StorageResult(false, false, category, null, null, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isPermissionMissing() {
        return permissionMissing;
    }

    @NonNull
    public PropertyCategory getCategory() {
        return category;
    }

    @Nullable
    public PropertyRecord getSavedRecord() {
        return savedRecord;
    }

    @Nullable
    public String getFileName() {
        return fileName;
    }

    @Nullable
    public String getDisplayPath() {
        return displayPath;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }
}
