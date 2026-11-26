package com.propertyhub.offline.poster;

import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Shared 4:3 (1440x1080) Dynamic Layout Engine for all property poster categories.
 * Keeps Title and Mobile font sizes fixed, adjusts Details Card size and Details font size
 * based on text volume, maintains exact card gaps, and prevents overlaps or border overflow.
 */
public final class DynamicPosterLayoutEngine {

    public static final float OUTER_MARGIN_PX = 16f;
    public static final float CARD_SIDE_MARGIN_PX = 36f;
    public static final float CARD_GAP_PX = 26f;

    public static final float HEADER_HEIGHT_PX = 118f;
    public static final float STACK_TOP_PX = 158f;
    public static final float STACK_BOTTOM_MARGIN_PX = 34f;

    public static final float MIN_TITLE_HEIGHT_PX = 156f;
    public static final float MAX_TITLE_HEIGHT_PX = 224f;

    public static final float MIN_CONTACT_HEIGHT_PX = 148f;
    public static final float MAX_CONTACT_HEIGHT_PX = 204f;

    public static final float MIN_DETAILS_HEIGHT_PX = 256f;
    public static final float COMPACT_DETAILS_MAX_HEIGHT_PX = 348f;
    public static final float DETAILS_VERT_PADDING_PX = 32f;

    public static final float DETAILS_LEFT_PANE_WIDTH_PX = 336f;
    public static final float DETAILS_TEXT_SIDE_PADDING_PX = 34f;

    public static final float START_DETAILS_FONT_PX = 66f;
    public static final float COMPACT_DETAILS_MIN_FONT_PX = 50f;
    public static final float MIN_DETAILS_FONT_PX = 18f;

    /**
     * Pure numeric layout geometry (Android-framework independent for easy unit verification).
     */
    public static final class GeometryMetrics {
        public final float outerLeft;
        public final float outerTop;
        public final float outerRight;
        public final float outerBottom;

        public final float headerTop;
        public final float headerBottom;

        public final float cardLeft;
        public final float cardRight;

        public final float titleTop;
        public final float titleBottom;

        public final float detailsTop;
        public final float detailsBottom;

        public final float contactTop;
        public final float contactBottom;

        public final float dividerX;
        public final float detailsTextLeft;
        public final float detailsTextRight;
        public final float detailsTextTop;
        public final float detailsTextBottom;

        public GeometryMetrics(
                float outerLeft,
                float outerTop,
                float outerRight,
                float outerBottom,
                float headerTop,
                float headerBottom,
                float cardLeft,
                float cardRight,
                float titleTop,
                float titleBottom,
                float detailsTop,
                float detailsBottom,
                float contactTop,
                float contactBottom,
                float dividerX,
                float detailsTextLeft,
                float detailsTextRight,
                float detailsTextTop,
                float detailsTextBottom
        ) {
            this.outerLeft = outerLeft;
            this.outerTop = outerTop;
            this.outerRight = outerRight;
            this.outerBottom = outerBottom;
            this.headerTop = headerTop;
            this.headerBottom = headerBottom;
            this.cardLeft = cardLeft;
            this.cardRight = cardRight;
            this.titleTop = titleTop;
            this.titleBottom = titleBottom;
            this.detailsTop = detailsTop;
            this.detailsBottom = detailsBottom;
            this.contactTop = contactTop;
            this.contactBottom = contactBottom;
            this.dividerX = dividerX;
            this.detailsTextLeft = detailsTextLeft;
            this.detailsTextRight = detailsTextRight;
            this.detailsTextTop = detailsTextTop;
            this.detailsTextBottom = detailsTextBottom;
        }

        public float getDetailsTextWidth() {
            return detailsTextRight - detailsTextLeft;
        }

        public float getDetailsTextHeight() {
            return detailsTextBottom - detailsTextTop;
        }

        public float getTitleHeight() {
            return titleBottom - titleTop;
        }

        public float getDetailsCardHeight() {
            return detailsBottom - detailsTop;
        }

        public float getContactHeight() {
            return contactBottom - contactTop;
        }
    }

    public static final class LayoutPlan {
        private final GeometryMetrics metrics;
        private final RectF outerRect;
        private final RectF headerRect;
        private final RectF titleRect;
        private final RectF detailsCardRect;
        private final RectF detailsTextRect;
        private final RectF contactRect;
        private final PosterTextFitter.FitResult detailsFit;

        public LayoutPlan(
                @NonNull GeometryMetrics metrics,
                @NonNull PosterTextFitter.FitResult detailsFit
        ) {
            this.metrics = metrics;
            this.outerRect = new RectF(
                    metrics.outerLeft, metrics.outerTop, metrics.outerRight, metrics.outerBottom);
            this.headerRect = new RectF(
                    metrics.cardLeft, metrics.headerTop, metrics.cardRight, metrics.headerBottom);
            this.titleRect = new RectF(
                    metrics.cardLeft, metrics.titleTop, metrics.cardRight, metrics.titleBottom);
            this.detailsCardRect = new RectF(
                    metrics.cardLeft, metrics.detailsTop, metrics.cardRight, metrics.detailsBottom);
            this.detailsTextRect = new RectF(
                    metrics.detailsTextLeft,
                    metrics.detailsTextTop,
                    metrics.detailsTextRight,
                    metrics.detailsTextBottom
            );
            this.contactRect = new RectF(
                    metrics.cardLeft, metrics.contactTop, metrics.cardRight, metrics.contactBottom);
            this.detailsFit = detailsFit;
        }

        @NonNull
        public GeometryMetrics getMetrics() {
            return metrics;
        }

        @NonNull
        public RectF getOuterRect() {
            return outerRect;
        }

        @NonNull
        public RectF getHeaderRect() {
            return headerRect;
        }

        @NonNull
        public RectF getTitleRect() {
            return titleRect;
        }

        @NonNull
        public RectF getDetailsCardRect() {
            return detailsCardRect;
        }

        @NonNull
        public RectF getDetailsTextRect() {
            return detailsTextRect;
        }

        @NonNull
        public RectF getContactRect() {
            return contactRect;
        }

        @NonNull
        public PosterTextFitter.FitResult getDetailsFit() {
            return detailsFit;
        }

        public float getDividerX() {
            return metrics.dividerX;
        }

        public boolean isOverflowed() {
            return detailsFit.isOverflowed();
        }
    }

    private DynamicPosterLayoutEngine() {
    }

    public static float getMaxDetailsCardHeight(int canvasHeight) {
        float stackBudget = (canvasHeight - STACK_BOTTOM_MARGIN_PX) - STACK_TOP_PX;
        float cardsSumBudget = stackBudget - 2f * CARD_GAP_PX;
        return Math.max(
                MIN_DETAILS_HEIGHT_PX,
                cardsSumBudget - MIN_TITLE_HEIGHT_PX - MIN_CONTACT_HEIGHT_PX
        );
    }

    @NonNull
    public static GeometryMetrics computeGeometryMetrics(
            int canvasWidth,
            int canvasHeight,
            float desiredDetailsCardHeight
    ) {
        float outerLeft = OUTER_MARGIN_PX;
        float outerTop = OUTER_MARGIN_PX;
        float outerRight = canvasWidth - OUTER_MARGIN_PX;
        float outerBottom = canvasHeight - OUTER_MARGIN_PX;

        float cardLeft = CARD_SIDE_MARGIN_PX;
        float cardRight = canvasWidth - CARD_SIDE_MARGIN_PX;

        float stackBottom = canvasHeight - STACK_BOTTOM_MARGIN_PX;
        float stackBudget = stackBottom - STACK_TOP_PX;
        float cardsSumBudget = stackBudget - 2f * CARD_GAP_PX;

        float maxDetailsH = getMaxDetailsCardHeight(canvasHeight);
        float detailsH = clamp(desiredDetailsCardHeight, MIN_DETAILS_HEIGHT_PX, maxDetailsH);

        float remainingBudget = cardsSumBudget - detailsH;
        float extra = Math.max(0f, remainingBudget - MIN_TITLE_HEIGHT_PX - MIN_CONTACT_HEIGHT_PX);

        float titleH = Math.min(MAX_TITLE_HEIGHT_PX, MIN_TITLE_HEIGHT_PX + extra * 0.52f);
        float remainingForContact = extra - (titleH - MIN_TITLE_HEIGHT_PX);
        float contactH = Math.min(MAX_CONTACT_HEIGHT_PX, MIN_CONTACT_HEIGHT_PX + remainingForContact);

        float totalStackH = titleH + CARD_GAP_PX + detailsH + CARD_GAP_PX + contactH;
        float slack = Math.max(0f, stackBudget - totalStackH);

        float titleTop = STACK_TOP_PX + slack * 0.42f;
        float titleBottom = titleTop + titleH;

        float detailsTop = titleBottom + CARD_GAP_PX;
        float detailsBottom = detailsTop + detailsH;

        float contactTop = detailsBottom + CARD_GAP_PX;
        float contactBottom = contactTop + contactH;

        float minHeaderBottom = 26f + HEADER_HEIGHT_PX;
        float headerBottom = Math.max(minHeaderBottom, titleTop - 16f);
        float headerTop = headerBottom - HEADER_HEIGHT_PX;

        float dividerX = cardLeft + DETAILS_LEFT_PANE_WIDTH_PX;
        float detailsTextLeft = dividerX + DETAILS_TEXT_SIDE_PADDING_PX;
        float detailsTextRight = cardRight - DETAILS_TEXT_SIDE_PADDING_PX;
        float detailsTextTop = detailsTop + DETAILS_VERT_PADDING_PX;
        float detailsTextBottom = detailsBottom - DETAILS_VERT_PADDING_PX;

        return new GeometryMetrics(
                outerLeft,
                outerTop,
                outerRight,
                outerBottom,
                headerTop,
                headerBottom,
                cardLeft,
                cardRight,
                titleTop,
                titleBottom,
                detailsTop,
                detailsBottom,
                contactTop,
                contactBottom,
                dividerX,
                detailsTextLeft,
                detailsTextRight,
                detailsTextTop,
                detailsTextBottom
        );
    }

    @NonNull
    public static LayoutPlan solveLayout(
            @NonNull PosterThemeConfig theme,
            @NonNull String rawDetails,
            @Nullable Typeface paragraphTypeface,
            int canvasWidth,
            int canvasHeight
    ) {
        TextPaint detailsPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        detailsPaint.setColor(theme.getDetailsTextColor());
        detailsPaint.setTypeface(paragraphTypeface);

        GeometryMetrics maxMetrics = computeGeometryMetrics(
                canvasWidth,
                canvasHeight,
                getMaxDetailsCardHeight(canvasHeight)
        );
        int textWidthPx = Math.round(maxMetrics.getDetailsTextWidth());
        int compactMaxTextH = Math.round(COMPACT_DETAILS_MAX_HEIGHT_PX - 2f * DETAILS_VERT_PADDING_PX);
        int expandedMaxTextH = Math.round(
                getMaxDetailsCardHeight(canvasHeight) - 2f * DETAILS_VERT_PADDING_PX
        );

        List<List<DetailsParagraphParser.StyledSegment>> styledParagraphs =
                DetailsParagraphParser.parseStyledParagraphs(rawDetails);

        // Pass 1: Keep compact Details Card with large legible font when Details is concise
        PosterTextFitter.FitResult compactFit = PosterTextFitter.fitStyledParagraphs(
                styledParagraphs,
                detailsPaint,
                textWidthPx,
                compactMaxTextH,
                START_DETAILS_FONT_PX,
                COMPACT_DETAILS_MIN_FONT_PX
        );

        PosterTextFitter.FitResult chosenFit;
        float desiredCardH;

        if (!compactFit.isOverflowed() && compactFit.getTotalHeightPx() <= compactMaxTextH) {
            chosenFit = compactFit;
            desiredCardH = Math.max(
                    MIN_DETAILS_HEIGHT_PX,
                    chosenFit.getTotalHeightPx() + 2f * DETAILS_VERT_PADDING_PX
            );
        } else {
            // Pass 2: Expand Details Card smoothly while scaling Details font size down as needed
            chosenFit = PosterTextFitter.fitStyledParagraphs(
                    styledParagraphs,
                    detailsPaint,
                    textWidthPx,
                    expandedMaxTextH,
                    COMPACT_DETAILS_MIN_FONT_PX,
                    MIN_DETAILS_FONT_PX
            );
            float maxCardH = getMaxDetailsCardHeight(canvasHeight);
            if (chosenFit.isOverflowed()) {
                desiredCardH = maxCardH;
            } else {
                desiredCardH = clamp(
                        chosenFit.getTotalHeightPx() + 2f * DETAILS_VERT_PADDING_PX,
                        COMPACT_DETAILS_MAX_HEIGHT_PX,
                        maxCardH
                );
            }
        }

        GeometryMetrics finalMetrics = computeGeometryMetrics(canvasWidth, canvasHeight, desiredCardH);
        return new LayoutPlan(finalMetrics, chosenFit);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
