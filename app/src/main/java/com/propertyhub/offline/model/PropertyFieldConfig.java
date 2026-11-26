package com.propertyhub.offline.model;

import androidx.annotation.NonNull;

public final class PropertyFieldConfig {

    private final String fieldId;
    private final PropertyCategory category;
    private final String label;
    private final String defaultValue;
    private final String placeholder;
    private final PropertyFieldInputType inputType;
    private final boolean required;
    private final boolean supportsFormatting;
    private final int displayOrder;
    private final boolean posterVisible;

    public PropertyFieldConfig(
            @NonNull String fieldId,
            @NonNull PropertyCategory category,
            @NonNull String label,
            @NonNull String defaultValue,
            @NonNull String placeholder,
            @NonNull PropertyFieldInputType inputType,
            boolean required,
            boolean supportsFormatting,
            int displayOrder,
            boolean posterVisible
    ) {
        this.fieldId = fieldId;
        this.category = category;
        this.label = label;
        this.defaultValue = defaultValue;
        this.placeholder = placeholder;
        this.inputType = inputType;
        this.required = required;
        this.supportsFormatting = supportsFormatting;
        this.displayOrder = displayOrder;
        this.posterVisible = posterVisible;
    }

    @NonNull
    public String getFieldId() {
        return fieldId;
    }

    @NonNull
    public PropertyCategory getCategory() {
        return category;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    @NonNull
    public String getDefaultValue() {
        return defaultValue;
    }

    @NonNull
    public String getPlaceholder() {
        return placeholder;
    }

    @NonNull
    public PropertyFieldInputType getInputType() {
        return inputType;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean supportsFormatting() {
        return supportsFormatting;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isPosterVisible() {
        return posterVisible;
    }

    public boolean isTitleField() {
        return "title".equals(fieldId);
    }

    public boolean isMobileField() {
        return "mobile".equals(fieldId);
    }
}
