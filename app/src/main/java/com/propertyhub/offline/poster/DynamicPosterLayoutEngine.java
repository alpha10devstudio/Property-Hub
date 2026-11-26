package com.propertyhub.offline.poster;

import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyFieldValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified 3:4 Portrait (1440x1920) Dynamic Poster Layout Engine.
 * - Field-count agnostic: adapts seamlessly to House (2-3+ body fields), Land (1-7+ body fields),
 *   and any future configuration-driven fields.
 * - Empty fields are never passed in / never consume space.
 * - Title & Contact banners preserve consistent typography rather than bloating when content is short.
 */
public final class DynamicPosterLayoutEngine {

    public static final float OUTER_MARGIN_PX = 22f;
    public static final float CARD_SIDE_MARGIN_PX = 56f;
    public static final float COLUMN_GAP_PX = 22f;

    public static final float HEADER_TOP_PX = 38f;
    public static final float HEADER_HEIGHT_PX = 152f;
    public static final float TITLE_TOP_PX = 202f;
    public static final float TITLE_MIN_HEIGHT_PX = 160f;
    public static final float TITLE_MAX_HEIGHT_PX = 224f;

    public static final float CONTACT_HEIGHT_PX = 158f;
    public static final float CONTACT_BOTTOM_MARGIN_PX = 54f;
    public static final float SECTION_GAP_PX = 26f;

    public static final float SHOWCASE_MAX_HEIGHT_PX = 224f;
    public static final float SHOWCASE_MIN_HEIGHT_PX = 164f;

    public static final float START_VALUE_FONT_PX = 46f;
    public static final float MIN_VALUE_FONT_PX = 20f;

    // Legacy compatibility constants preserved for GeometryMetrics tests
    public static final float CARD_GAP_PX = 26f;
    public static final float STACK_TOP_PX = 202f;
    public static final float STACK_BOTTOM_MARGIN_PX = 54f;
    public static final float MIN_TITLE_HEIGHT_PX = 160f;
    public static final float MAX_TITLE_HEIGHT_PX = 224f;
    public static final float MIN_CONTACT_HEIGHT_PX = 150f;
    public static final float MAX_CONTACT_HEIGHT_PX = 186f;
    public static final float MIN_DETAILS_HEIGHT_PX = 340f;
    public static final float DETAILS_VERT_PADDING_PX = 28f;
    public static final float DETAILS_LEFT_PANE_WIDTH_PX = 320f;
    public static final float DETAILS_TEXT_SIDE_PADDING_PX = 32f;

    /**
     * Pure numeric layout geometry (Android-framework independent for fast JVM unit tests).
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

    public static final class FieldCardPlacement {
        private final PropertyFieldValue fieldValue;
        private final RectF cardRect;
        private final RectF labelRect;
        private final RectF valueRect;
        private final StaticLayout labelLayout;
        private final StaticLayout valueLayout;
        private final float labelFontSizePx;
        private final float valueFontSizePx;
        private final boolean fullWidth;

        public FieldCardPlacement(
                @NonNull PropertyFieldValue fieldValue,
                @NonNull RectF cardRect,
                @NonNull RectF labelRect,
                @NonNull RectF valueRect,
                @NonNull StaticLayout labelLayout,
                @NonNull StaticLayout valueLayout,
                float labelFontSizePx,
                float valueFontSizePx,
                boolean fullWidth
        ) {
            this.fieldValue = fieldValue;
            this.cardRect = cardRect;
            this.labelRect = labelRect;
            this.valueRect = valueRect;
            this.labelLayout = labelLayout;
            this.valueLayout = valueLayout;
            this.labelFontSizePx = labelFontSizePx;
            this.valueFontSizePx = valueFontSizePx;
            this.fullWidth = fullWidth;
        }

        @NonNull
        public PropertyFieldValue getFieldValue() {
            return fieldValue;
        }

        @NonNull
        public RectF getCardRect() {
            return cardRect;
        }

        @NonNull
        public RectF getLabelRect() {
            return labelRect;
        }

        @NonNull
        public RectF getValueRect() {
            return valueRect;
        }

        @NonNull
        public StaticLayout getLabelLayout() {
            return labelLayout;
        }

        @NonNull
        public StaticLayout getValueLayout() {
            return valueLayout;
        }

        public float getLabelFontSizePx() {
            return labelFontSizePx;
        }

        public float getValueFontSizePx() {
            return valueFontSizePx;
        }

        public boolean isFullWidth() {
            return fullWidth;
        }
    }

    public static final class PortraitLayoutPlan {
        private final RectF outerRect;
        private final RectF headerRect;
        private final RectF titleRect;
        private final StaticLayout titleLayout;
        private final float titleFontSizePx;
        private final RectF bodyFrameRect;
        private final RectF showcaseRect;
        private final List<FieldCardPlacement> fieldCards;
        private final RectF contactRect;
        private final float valueFontSizePx;
        private final boolean overflowed;

        public PortraitLayoutPlan(
                @NonNull RectF outerRect,
                @NonNull RectF headerRect,
                @NonNull RectF titleRect,
                @NonNull StaticLayout titleLayout,
                float titleFontSizePx,
                @NonNull RectF bodyFrameRect,
                @NonNull RectF showcaseRect,
                @NonNull List<FieldCardPlacement> fieldCards,
                @NonNull RectF contactRect,
                float valueFontSizePx,
                boolean overflowed
        ) {
            this.outerRect = outerRect;
            this.headerRect = headerRect;
            this.titleRect = titleRect;
            this.titleLayout = titleLayout;
            this.titleFontSizePx = titleFontSizePx;
            this.bodyFrameRect = bodyFrameRect;
            this.showcaseRect = showcaseRect;
            this.fieldCards = fieldCards;
            this.contactRect = contactRect;
            this.valueFontSizePx = valueFontSizePx;
            this.overflowed = overflowed;
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
        public StaticLayout getTitleLayout() {
            return titleLayout;
        }

        public float getTitleFontSizePx() {
            return titleFontSizePx;
        }

        @NonNull
        public RectF getBodyFrameRect() {
            return bodyFrameRect;
        }

        @NonNull
        public RectF getShowcaseRect() {
            return showcaseRect;
        }

        @NonNull
        public List<FieldCardPlacement> getFieldCards() {
            return fieldCards;
        }

        @NonNull
        public RectF getContactRect() {
            return contactRect;
        }

        public float getValueFontSizePx() {
            return valueFontSizePx;
        }

        public boolean isOverflowed() {
            return overflowed;
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

    /**
     * Pure numeric geometry helper usable across any canvas dimensions (including 1440x1920 Portrait).
     */
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

        float titleH = MIN_TITLE_HEIGHT_PX;
        float contactH = CONTACT_HEIGHT_PX;

        float totalStackH = titleH + CARD_GAP_PX + detailsH + CARD_GAP_PX + contactH;
        float slack = Math.max(0f, stackBudget - totalStackH);

        float titleTop = STACK_TOP_PX;
        float titleBottom = titleTop + titleH;

        float detailsTop = titleBottom + CARD_GAP_PX + slack * 0.25f;
        float detailsBottom = detailsTop + detailsH;

        float contactBottom = stackBottom;
        float contactTop = contactBottom - contactH;
        if (contactTop < detailsBottom + CARD_GAP_PX) {
            detailsTop = titleBottom + CARD_GAP_PX;
            detailsBottom = detailsTop + detailsH;
            contactTop = detailsBottom + CARD_GAP_PX;
            contactBottom = contactTop + contactH;
        }

        float headerTop = HEADER_TOP_PX;
        float headerBottom = Math.min(titleTop - 10f, headerTop + HEADER_HEIGHT_PX);

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

    /**
     * Field-count-agnostic portrait layout solver for 1440x1920 posters.
     */
    @NonNull
    public static PortraitLayoutPlan solvePortraitLayout(
            @NonNull PosterThemeConfig theme,
            @NonNull String titleText,
            @NonNull List<PropertyFieldValue> nonEmptyVisibleFields,
            @Nullable Typeface titleTypeface,
            @Nullable Typeface paragraphTypeface,
            int canvasWidth,
            int canvasHeight,
            boolean allowTwoColumnPairing,
            boolean wrapCardsInOuterFrame
    ) {
        RectF outerRect = new RectF(
                OUTER_MARGIN_PX,
                OUTER_MARGIN_PX,
                canvasWidth - OUTER_MARGIN_PX,
                canvasHeight - OUTER_MARGIN_PX
        );
        float cardLeft = CARD_SIDE_MARGIN_PX;
        float cardRight = canvasWidth - CARD_SIDE_MARGIN_PX;
        float cardWidth = cardRight - cardLeft;

        RectF headerRect = new RectF(cardLeft, HEADER_TOP_PX, cardRight, HEADER_TOP_PX + HEADER_HEIGHT_PX);

        // 1. Solve steady Title Banner (wraps gracefully up to 2 lines only if user enters a long custom title)
        String safeTitle = titleText.trim().isEmpty() ? theme.getTitleText() : titleText.trim();
        TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTypeface(titleTypeface);
        int titleInnerWidth = Math.round(cardWidth - 132f);

        float chosenTitleSize = PosterThemeConfig.FIXED_TITLE_FONT_SIZE_PX;
        StaticLayout titleLayout = buildStaticLayout(safeTitle, titlePaint, titleInnerWidth, chosenTitleSize, Layout.Alignment.ALIGN_CENTER, 1.08f);
        while ((titleLayout.getLineCount() > 2 || titleLayout.getHeight() > TITLE_MAX_HEIGHT_PX - 36f)
                && chosenTitleSize > PosterThemeConfig.MIN_TITLE_FONT_SIZE_PX) {
            chosenTitleSize -= 2f;
            titleLayout = buildStaticLayout(safeTitle, titlePaint, titleInnerWidth, chosenTitleSize, Layout.Alignment.ALIGN_CENTER, 1.08f);
        }
        float titleH = clamp(titleLayout.getHeight() + 56f, TITLE_MIN_HEIGHT_PX, TITLE_MAX_HEIGHT_PX);
        RectF titleRect = new RectF(cardLeft, TITLE_TOP_PX, cardRight, TITLE_TOP_PX + titleH);

        // 2. Anchor steady Contact Banner near bottom
        float contactBottom = canvasHeight - CONTACT_BOTTOM_MARGIN_PX;
        RectF contactRect = new RectF(cardLeft, contactBottom - CONTACT_HEIGHT_PX, cardRight, contactBottom);

        // 3. Middle available vertical zone
        float bodyOuterTop = titleRect.bottom + SECTION_GAP_PX;
        float bodyOuterBottom = contactRect.top - SECTION_GAP_PX;
        float frameInsetH = wrapCardsInOuterFrame ? 28f : 0f;
        float frameInsetV = wrapCardsInOuterFrame ? 26f : 0f;

        float contentLeft = cardLeft + frameInsetH;
        float contentRight = cardRight - frameInsetH;
        float contentWidth = contentRight - contentLeft;
        float maxContentHeight = (bodyOuterBottom - bodyOuterTop) - 2f * frameInsetV;

        List<PropertyFieldValue> fields = nonEmptyVisibleFields.isEmpty()
                ? Collections.emptyList()
                : nonEmptyVisibleFields;

        TextPaint labelPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(theme.getPrimaryColor());
        labelPaint.setTypeface(paragraphTypeface != null
                ? Typeface.create(paragraphTypeface, Typeface.BOLD)
                : Typeface.DEFAULT_BOLD);
        labelPaint.setFakeBoldText(true);

        TextPaint valuePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(theme.getDetailsTextColor());
        valuePaint.setTypeface(paragraphTypeface);

        // 4. Adaptive solver loop: find largest legible typography & spacing that fits zero-clipping
        float startSize = fields.size() <= 2 ? 46f : (fields.size() <= 4 ? 40f : 35f);
        float chosenValueSize = MIN_VALUE_FONT_PX;
        float chosenShowcaseH = SHOWCASE_MAX_HEIGHT_PX;
        float chosenShowcaseGap = 22f;
        List<RowPlan> chosenRows = Collections.emptyList();
        float chosenTotalStackH = 0f;
        boolean overflowed = false;

        for (float size = startSize; size >= MIN_VALUE_FONT_PX; size -= 2f) {
            float t = (size - MIN_VALUE_FONT_PX) / Math.max(1f, (START_VALUE_FONT_PX - MIN_VALUE_FONT_PX));
            float showcaseH = lerp(SHOWCASE_MIN_HEIGHT_PX, SHOWCASE_MAX_HEIGHT_PX, t);
            float showcaseGap = lerp(14f, 24f, t);
            float rowGap = lerp(12f, 24f, t);
            float padH = lerp(20f, 30f, t);
            float padV = lerp(14f, 24f, t);
            float labelGap = lerp(6f, 10f, t);
            float lineMult = lerp(1.06f, 1.16f, t);
            float labelSize = Math.max(18f, Math.min(28f, size * 0.64f));

            List<RowPlan> rows = measureRows(
                    fields,
                    contentWidth,
                    allowTwoColumnPairing,
                    labelPaint,
                    valuePaint,
                    paragraphTypeface,
                    labelSize,
                    size,
                    lineMult,
                    padH,
                    padV,
                    labelGap
            );

            float rowsH = computeRowsHeight(rows, rowGap);
            float totalH = showcaseH + (rows.isEmpty() ? 0f : showcaseGap + rowsH);

            chosenValueSize = size;
            chosenShowcaseH = showcaseH;
            chosenShowcaseGap = showcaseGap;
            chosenRows = rows;
            chosenTotalStackH = totalH;

            if (totalH <= maxContentHeight && !anyRowClipped(rows)) {
                overflowed = false;
                break;
            }
            if (size - 2f < MIN_VALUE_FONT_PX) {
                overflowed = totalH > maxContentHeight || anyRowClipped(rows);
            }
        }

        // 5. Distribute gracefully vertically inside [bodyOuterTop, bodyOuterBottom]
        float actualBodyFrameTop;
        float actualBodyFrameBottom;
        float contentStartY;

        if (wrapCardsInOuterFrame) {
            float desiredFrameH = Math.min(
                    bodyOuterBottom - bodyOuterTop,
                    Math.max(chosenTotalStackH + 2f * frameInsetV, (bodyOuterBottom - bodyOuterTop) * 0.68f)
            );
            float slackOuter = Math.max(0f, (bodyOuterBottom - bodyOuterTop) - desiredFrameH);
            actualBodyFrameTop = bodyOuterTop + slackOuter * 0.45f;
            actualBodyFrameBottom = actualBodyFrameTop + desiredFrameH;
            float innerH = actualBodyFrameBottom - actualBodyFrameTop - 2f * frameInsetV;
            float innerSlack = Math.max(0f, innerH - chosenTotalStackH);
            contentStartY = actualBodyFrameTop + frameInsetV + innerSlack * 0.35f;
        } else {
            float slack = Math.max(0f, maxContentHeight - chosenTotalStackH);
            actualBodyFrameTop = bodyOuterTop;
            actualBodyFrameBottom = bodyOuterBottom;
            contentStartY = bodyOuterTop + slack * 0.35f;
        }

        RectF bodyFrameRect = new RectF(cardLeft, actualBodyFrameTop, cardRight, actualBodyFrameBottom);
        RectF showcaseRect = new RectF(
                contentLeft,
                contentStartY,
                contentRight,
                contentStartY + chosenShowcaseH
        );

        float rowGap = lerp(
                12f,
                24f,
                (chosenValueSize - MIN_VALUE_FONT_PX) / Math.max(1f, (START_VALUE_FONT_PX - MIN_VALUE_FONT_PX))
        );
        // Slightly open row gap if plenty of room and multiple fields
        if (!overflowed && chosenRows.size() > 1) {
            float freeSlack = Math.max(0f, (actualBodyFrameBottom - frameInsetV) - (showcaseRect.bottom + chosenShowcaseGap + computeRowsHeight(chosenRows, rowGap)));
            float extraPerGap = Math.min(18f, freeSlack / (chosenRows.size() + 1));
            rowGap += extraPerGap;
        }

        List<FieldCardPlacement> placements = new ArrayList<>();
        float cursorY = showcaseRect.bottom + chosenShowcaseGap;
        for (RowPlan row : chosenRows) {
            float rowH = row.height;
            if (row.cells.size() == 1) {
                CellPlan cell = row.cells.get(0);
                placements.add(toPlacement(cell, contentLeft, cursorY, contentWidth, rowH, true));
            } else if (row.cells.size() == 2) {
                float colW = (contentWidth - COLUMN_GAP_PX) / 2f;
                placements.add(toPlacement(row.cells.get(0), contentLeft, cursorY, colW, rowH, false));
                placements.add(toPlacement(row.cells.get(1), contentLeft + colW + COLUMN_GAP_PX, cursorY, colW, rowH, false));
            }
            cursorY += rowH + rowGap;
        }

        return new PortraitLayoutPlan(
                outerRect,
                headerRect,
                titleRect,
                titleLayout,
                chosenTitleSize,
                bodyFrameRect,
                showcaseRect,
                placements,
                contactRect,
                chosenValueSize,
                overflowed
        );
    }

    private static FieldCardPlacement toPlacement(
            @NonNull CellPlan cell,
            float left,
            float top,
            float width,
            float height,
            boolean fullWidth
    ) {
        RectF cardRect = new RectF(left, top, left + width, top + height);
        float innerLeft = left + cell.padH;
        float innerRight = left + width - cell.padH;
        float labelTop = top + cell.padV;
        float labelBottom = labelTop + cell.labelLayout.getHeight();
        float valueTop = labelBottom + cell.labelGap;
        float valueBottom = top + height - cell.padV;

        return new FieldCardPlacement(
                cell.fieldValue,
                cardRect,
                new RectF(innerLeft, labelTop, innerRight, labelBottom),
                new RectF(innerLeft, valueTop, innerRight, Math.max(valueTop + 1f, valueBottom)),
                cell.labelLayout,
                cell.valueLayout,
                cell.labelSize,
                cell.valueSize,
                fullWidth
        );
    }

    private static final class CellPlan {
        final PropertyFieldValue fieldValue;
        final StaticLayout labelLayout;
        final StaticLayout valueLayout;
        final float labelSize;
        final float valueSize;
        final float padH;
        final float padV;
        final float labelGap;
        final float width;
        final float height;

        CellPlan(
                PropertyFieldValue fieldValue,
                StaticLayout labelLayout,
                StaticLayout valueLayout,
                float labelSize,
                float valueSize,
                float padH,
                float padV,
                float labelGap,
                float width,
                float height
        ) {
            this.fieldValue = fieldValue;
            this.labelLayout = labelLayout;
            this.valueLayout = valueLayout;
            this.labelSize = labelSize;
            this.valueSize = valueSize;
            this.padH = padH;
            this.padV = padV;
            this.labelGap = labelGap;
            this.width = width;
            this.height = height;
        }
    }

    private static final class RowPlan {
        final List<CellPlan> cells;
        final float height;

        RowPlan(List<CellPlan> cells, float height) {
            this.cells = cells;
            this.height = height;
        }
    }

    @NonNull
    private static List<RowPlan> measureRows(
            @NonNull List<PropertyFieldValue> fields,
            float totalWidth,
            boolean allowTwoCol,
            @NonNull TextPaint labelPaint,
            @NonNull TextPaint valuePaint,
            @Nullable Typeface paragraphTypeface,
            float labelSize,
            float valueSize,
            float lineMult,
            float padH,
            float padV,
            float labelGap
    ) {
        List<RowPlan> rows = new ArrayList<>();
        int i = 0;
        while (i < fields.size()) {
            PropertyFieldValue current = fields.get(i);
            boolean canPair = allowTwoCol
                    && fields.size() >= 3
                    && i + 1 < fields.size()
                    && isCompactSingleLine(current)
                    && isCompactSingleLine(fields.get(i + 1));

            if (canPair) {
                float halfW = (totalWidth - COLUMN_GAP_PX) / 2f;
                CellPlan c1 = measureCell(current, halfW, labelPaint, valuePaint, paragraphTypeface,
                        labelSize, valueSize, lineMult, padH, padV, labelGap);
                CellPlan c2 = measureCell(fields.get(i + 1), halfW, labelPaint, valuePaint, paragraphTypeface,
                        labelSize, valueSize, lineMult, padH, padV, labelGap);
                if (c1.valueLayout.getLineCount() <= 2 && c2.valueLayout.getLineCount() <= 2) {
                    List<CellPlan> pair = new ArrayList<>(2);
                    pair.add(c1);
                    pair.add(c2);
                    rows.add(new RowPlan(pair, Math.max(c1.height, c2.height)));
                    i += 2;
                    continue;
                }
            }

            CellPlan full = measureCell(current, totalWidth, labelPaint, valuePaint, paragraphTypeface,
                    labelSize, valueSize, lineMult, padH, padV, labelGap);
            rows.add(new RowPlan(Collections.singletonList(full), full.height));
            i++;
        }
        return rows;
    }

    @NonNull
    private static CellPlan measureCell(
            @NonNull PropertyFieldValue fv,
            float cardWidth,
            @NonNull TextPaint labelPaint,
            @NonNull TextPaint valuePaint,
            @Nullable Typeface paragraphTypeface,
            float labelSize,
            float valueSize,
            float lineMult,
            float padH,
            float padV,
            float labelGap
    ) {
        int innerWidth = Math.max(80, Math.round(cardWidth - 2f * padH));
        String labelTitle = fv.getLabel().trim().isEmpty() ? "তথ্য" : fv.getLabel().trim();
        StaticLayout labelLayout = buildStaticLayout(
                labelTitle,
                labelPaint,
                innerWidth,
                labelSize,
                Layout.Alignment.ALIGN_NORMAL,
                1.04f
        );

        valuePaint.setTextSize(valueSize);
        SpannableStringBuilder styledValue = PosterTextFitter.toSpannable(fv.getSegments(), paragraphTypeface);
        StaticLayout valueLayout = StaticLayout.Builder
                .obtain(styledValue, 0, styledValue.length(), valuePaint, innerWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineMult)
                .setIncludePad(true)
                .build();

        float totalH = padV * 2f + labelLayout.getHeight() + labelGap + valueLayout.getHeight();
        return new CellPlan(fv, labelLayout, valueLayout, labelSize, valueSize, padH, padV, labelGap, cardWidth, totalH);
    }

    private static boolean isCompactSingleLine(@NonNull PropertyFieldValue fv) {
        String t = fv.getPlainText().trim();
        return !t.isEmpty() && !t.contains("\n") && !t.contains("\r") && t.length() <= 26;
    }

    private static float computeRowsHeight(@NonNull List<RowPlan> rows, float rowGap) {
        float sum = 0f;
        for (int i = 0; i < rows.size(); i++) {
            sum += rows.get(i).height;
            if (i < rows.size() - 1) {
                sum += rowGap;
            }
        }
        return sum;
    }

    private static boolean anyRowClipped(@NonNull List<RowPlan> rows) {
        for (RowPlan row : rows) {
            for (CellPlan cell : row.cells) {
                float availW = cell.width - 2f * cell.padH + 4f;
                for (int l = 0; l < cell.valueLayout.getLineCount(); l++) {
                    if (cell.valueLayout.getLineWidth(l) > availW) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @NonNull
    private static StaticLayout buildStaticLayout(
            @NonNull CharSequence text,
            @NonNull TextPaint paint,
            int widthPx,
            float textSizePx,
            @NonNull Layout.Alignment alignment,
            float lineMult
    ) {
        paint.setTextSize(textSizePx);
        return StaticLayout.Builder
                .obtain(text, 0, text.length(), paint, Math.max(60, widthPx))
                .setAlignment(alignment)
                .setLineSpacing(0f, lineMult)
                .setIncludePad(true)
                .build();
    }

    private static float lerp(float a, float b, float t) {
        float clamped = clamp(t, 0f, 1f);
        return a + (b - a) * clamped;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
