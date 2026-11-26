package com.propertyhub.offline.model;

public enum AppThemeMode {
    BLUE_DEFAULT("blue", "Blue (Default)"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    private final String prefValue;
    private final String label;

    AppThemeMode(String prefValue, String label) {
        this.prefValue = prefValue;
        this.label = label;
    }

    public String getPrefValue() {
        return prefValue;
    }

    public String getLabel() {
        return label;
    }

    public static AppThemeMode fromPrefValue(String value) {
        for (AppThemeMode mode : values()) {
            if (mode.prefValue.equals(value)) {
                return mode;
            }
        }
        return BLUE_DEFAULT;
    }
}
