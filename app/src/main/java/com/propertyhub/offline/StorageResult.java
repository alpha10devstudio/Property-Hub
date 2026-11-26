package com.propertyhub.offline;

public final class StorageResult {

    private final boolean success;
    private final boolean permissionMissing;
    private final PropertyCategory category;
    private final int recordNumber;
    private final String fileName;
    private final String displayPath;
    private final String errorMessage;

    private StorageResult(
            boolean success,
            boolean permissionMissing,
            PropertyCategory category,
            int recordNumber,
            String fileName,
            String displayPath,
            String errorMessage
    ) {
        this.success = success;
        this.permissionMissing = permissionMissing;
        this.category = category;
        this.recordNumber = recordNumber;
        this.fileName = fileName;
        this.displayPath = displayPath;
        this.errorMessage = errorMessage;
    }

    public static StorageResult success(
            PropertyCategory category,
            int recordNumber,
            String fileName,
            String displayPath
    ) {
        return new StorageResult(true, false, category, recordNumber, fileName, displayPath, null);
    }

    public static StorageResult permissionMissing(PropertyCategory category, String errorMessage) {
        return new StorageResult(false, true, category, 0, null, null, errorMessage);
    }

    public static StorageResult error(PropertyCategory category, String errorMessage) {
        return new StorageResult(false, false, category, 0, null, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isPermissionMissing() {
        return permissionMissing;
    }

    public PropertyCategory getCategory() {
        return category;
    }

    public int getRecordNumber() {
        return recordNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDisplayPath() {
        return displayPath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
