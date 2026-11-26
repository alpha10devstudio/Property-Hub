package com.propertyhub.offline.poster;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.propertyhub.offline.model.PropertyCategory;

/**
 * Category-specific theme, branding copy, and fixed-size typography settings
 * shared with the unified DynamicPosterLayoutEngine.
 */
public final class PosterThemeConfig {

    public static final float FIXED_TITLE_FONT_SIZE_PX = 94f;
    public static final float FIXED_CONTACT_FONT_SIZE_PX = 82f;
    public static final String CONTACT_PREFIX = "যোগাযোগ: ";

    public static final String HOUSE_TITLE_TEXT = "ফ্ল্যাট / বাসা ভাড়া দেওয়া হবে";
    public static final String LAND_TITLE_TEXT = "জমি বিক্রি করা হবে";

    private final PropertyCategory category;
    private final String titleText;
    private final int primaryColor;
    private final int secondaryAccentColor;
    private final int softSurfaceColor;
    private final int canvasBackgroundColor;
    private final int detailsTextColor;
    private final float outerBorderRadius;
    private final float titleCornerRadius;
    private final float detailsCornerRadius;
    private final float contactCornerRadius;

    private PosterThemeConfig(
            @NonNull PropertyCategory category,
            @NonNull String titleText,
            int primaryColor,
            int secondaryAccentColor,
            int softSurfaceColor,
            int canvasBackgroundColor,
            int detailsTextColor,
            float outerBorderRadius,
            float titleCornerRadius,
            float detailsCornerRadius,
            float contactCornerRadius
    ) {
        this.category = category;
        this.titleText = titleText;
        this.primaryColor = primaryColor;
        this.secondaryAccentColor = secondaryAccentColor;
        this.softSurfaceColor = softSurfaceColor;
        this.canvasBackgroundColor = canvasBackgroundColor;
        this.detailsTextColor = detailsTextColor;
        this.outerBorderRadius = outerBorderRadius;
        this.titleCornerRadius = titleCornerRadius;
        this.detailsCornerRadius = detailsCornerRadius;
        this.contactCornerRadius = contactCornerRadius;
    }

    @NonNull
    public static PosterThemeConfig houseForRent() {
        return new PosterThemeConfig(
                PropertyCategory.HOUSE_FOR_RENT,
                HOUSE_TITLE_TEXT,
                Color.parseColor("#032668"),
                Color.parseColor("#0F4C81"),
                Color.parseColor("#F5F8FF"),
                Color.WHITE,
                Color.parseColor("#032668"),
                48f,
                50f,
                44f,
                50f
        );
    }

    @NonNull
    public static PosterThemeConfig landForSale() {
        return new PosterThemeConfig(
                PropertyCategory.LAND_FOR_SALE,
                LAND_TITLE_TEXT,
                Color.parseColor("#0B5D2E"),
                Color.parseColor("#16A34A"),
                Color.parseColor("#EEFBF3"),
                Color.WHITE,
                Color.parseColor("#063B1D"),
                30f,
                22f,
                24f,
                22f
        );
    }

    @NonNull
    public static PosterThemeConfig forCategory(@NonNull PropertyCategory category) {
        return category == PropertyCategory.HOUSE_FOR_RENT
                ? houseForRent()
                : landForSale();
    }

    @NonNull
    public PropertyCategory getCategory() {
        return category;
    }

    @NonNull
    public String getTitleText() {
        return titleText;
    }

    @NonNull
    public String getContactPrefix() {
        return CONTACT_PREFIX;
    }

    public float getFixedTitleFontSizePx() {
        return FIXED_TITLE_FONT_SIZE_PX;
    }

    public float getFixedContactFontSizePx() {
        return FIXED_CONTACT_FONT_SIZE_PX;
    }

    public int getPrimaryColor() {
        return primaryColor;
    }

    public int getSecondaryAccentColor() {
        return secondaryAccentColor;
    }

    public int getSoftSurfaceColor() {
        return softSurfaceColor;
    }

    public int getCanvasBackgroundColor() {
        return canvasBackgroundColor;
    }

    public int getDetailsTextColor() {
        return detailsTextColor;
    }

    public float getOuterBorderRadius() {
        return outerBorderRadius;
    }

    public float getTitleCornerRadius() {
        return titleCornerRadius;
    }

    public float getDetailsCornerRadius() {
        return detailsCornerRadius;
    }

    public float getContactCornerRadius() {
        return contactCornerRadius;
    }
}
