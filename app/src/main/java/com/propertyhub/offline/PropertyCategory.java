package com.propertyhub.offline;

public enum PropertyCategory {
    HOUSE_FOR_RENT("House for Rent"),
    LAND_FOR_SALE("Land for Sale");

    private final String folderName;

    PropertyCategory(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderName() {
        return folderName;
    }

    public String getDisplayName() {
        return folderName;
    }
}
