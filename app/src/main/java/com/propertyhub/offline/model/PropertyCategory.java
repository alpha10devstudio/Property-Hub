package com.propertyhub.offline.model;

public enum PropertyCategory {
    HOUSE_FOR_RENT(
            "House for Rent",
            "HOUSE_FOR_RENT",
            "HOUSE_",
            "House_",
            "House_Rent"
    ),
    LAND_FOR_SALE(
            "Land for Sale",
            "LAND_FOR_SALE",
            "LAND_",
            "Land_",
            "Land_Sale"
    );

    private final String folderName;
    private final String recordCategoryCode;
    private final String idPrefix;
    private final String posterPrefix;
    private final String zipSlug;

    PropertyCategory(
            String folderName,
            String recordCategoryCode,
            String idPrefix,
            String posterPrefix,
            String zipSlug
    ) {
        this.folderName = folderName;
        this.recordCategoryCode = recordCategoryCode;
        this.idPrefix = idPrefix;
        this.posterPrefix = posterPrefix;
        this.zipSlug = zipSlug;
    }

    public String getFolderName() {
        return folderName;
    }

    public String getDisplayName() {
        return folderName;
    }

    public String getRecordCategoryCode() {
        return recordCategoryCode;
    }

    public String getIdPrefix() {
        return idPrefix;
    }

    public String getPosterPrefix() {
        return posterPrefix;
    }

    public String getZipSlug() {
        return zipSlug;
    }

    public static PropertyCategory fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.US);
        if ("HOUSE_FOR_RENT".equals(normalized) || "HOUSE FOR RENT".equals(normalized)) {
            return HOUSE_FOR_RENT;
        }
        if ("LAND_FOR_SALE".equals(normalized) || "LAND FOR SALE".equals(normalized)) {
            return LAND_FOR_SALE;
        }
        return null;
    }
}
