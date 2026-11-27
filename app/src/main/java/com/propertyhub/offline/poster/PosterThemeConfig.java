package com.propertyhub.offline.poster;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyFieldCatalog;

/**
 * Category-specific visual theme matching Reference Image 1 (Navy House for Rent)
 * and Reference Image 2 (Dark Green Land for Sale), shared across the dynamic-height poster engine.
 */
public final class PosterThemeConfig {

    public static final float FIXED_TITLE_FONT_SIZE_PX = 86f;
    public static final float MIN_TITLE_FONT_SIZE_PX = 52f;
    public static final float FIXED_CONTACT_FONT_SIZE_PX = 78f;
    public static final float MIN_CONTACT_FONT_SIZE_PX = 42f;
    public static final String CONTACT_PREFIX = "যোগাযোগ: ";

    public static final String HOUSE_TITLE_TEXT = PropertyFieldCatalog.HOUSE_DEFAULT_TITLE;
    public static final String LAND_TITLE_TEXT = PropertyFieldCatalog.LAND_DEFAULT_TITLE;

    private final PropertyCategory category;
    private final String titleText;
    private final String contactPrefix;
    private final int primaryColor;
    private final int secondaryAccentColor;
    private final int softSurfaceColor;
    private final int cardBorderColor;
    private final int canvasBackgroundColor;
    private final int detailsTextColor;
    private final float outerBorderRadius;
    private final float titleCornerRadius;
    private final float detailsCornerRadius;
    private final float contactCornerRadius;

    private PosterThemeConfig(
            @NonNull PropertyCategory category,
            @NonNull String titleText,
            @NonNull String contactPrefix,
            int primaryColor,
            int secondaryAccentColor,
            int softSurfaceColor,
            int cardBorderColor,
            int canvasBackgroundColor,
            int detailsTextColor,
            float outerBorderRadius,
            float titleCornerRadius,
            float detailsCornerRadius,
            float contactCornerRadius
    ) {
        this.category = category;
        this.titleText = titleText;
        this.contactPrefix = contactPrefix;
        this.primaryColor = primaryColor;
        this.secondaryAccentColor = secondaryAccentColor;
        this.softSurfaceColor = softSurfaceColor;
        this.cardBorderColor = cardBorderColor;
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
                "যোগাযোগ: ",
                Color.parseColor("#002868"),
                Color.parseColor("#1D4ED8"),
                Color.parseColor("#F3F7FF"),
                Color.parseColor("#C7D7F2"),
                Color.WHITE,
                Color.parseColor("#002868"),
                46f,
                46f,
                38f,
                44f
        );
    }

    @NonNull
    public static PosterThemeConfig landForSale() {
        return new PosterThemeConfig(
                PropertyCategory.LAND_FOR_SALE,
                LAND_TITLE_TEXT,
                "যোগাযোগঃ ",
                Color.parseColor("#05661D"),
                Color.parseColor("#159A38"),
                Color.parseColor("#EFFBF3"),
                Color.parseColor("#BCE4C9"),
                Color.WHITE,
                Color.parseColor("#045718"),
                42f,
                44f,
                34f,
                44f
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
        return contactPrefix;
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

    public int getCardBorderColor() {
        return cardBorderColor;
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
