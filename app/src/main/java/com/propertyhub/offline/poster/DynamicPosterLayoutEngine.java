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
 * Dynamic Auto-Height Poster Layout Engine (fixed width 1440px, content-driven dynamic height).
 * - House for Rent (Reference Image 1): single large central rounded panel with left illustration,
 *   vertical separator, and right continuous rich-text blocks (no mini cards).
 * - Land for Sale (Reference Image 2): main central rounded panel (left pasture illustration +
 *   vertical separator + right main fields), optional separate Dag | Khatian pill row,
 *   and optional Additional Information row below.
 * - Empty fields are completely omitted and consume zero space.
 */
public final class DynamicPosterLayoutEngine {

    public static final float OUTER_MARGIN_PX = 22f;
    public static final float CARD_SIDE_MARGIN_PX = 48f;
    public static final float SECTION_GAP_PX = 24f;
    public static final float CONTACT_HEIGHT_PX = 152f;

    // Legacy compatibility constants preserved for existing unit tests
    public static final float HEADER_TOP_PX = 38f;
    public static final float HEADER_HEIGHT_PX = 152f;
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

    public static final class TextBlockPlacement {
        public final PropertyFieldValue fieldValue;
        public final StaticLayout layout;
        public final float x;
        public final float y;

        public TextBlockPlacement(
                @NonNull PropertyFieldValue fieldValue,
                @NonNull StaticLayout layout,
                float x,
                float y
        ) {
            this.fieldValue = fieldValue;
            this.layout = layout;
            this.x = x;
            this.y = y;
        }
    }

    public static final class HouseDynamicPlan {
        public final int width;
        public final int totalHeight;
        public final RectF outerRect;
        public final float headerTop;
        public final RectF titleRect;
        public final StaticLayout titleLayout;
        public final RectF panelRect;
        public final float illustCenterX;
        public final float illustCenterY;
        public final float illustMaxW;
        public final float illustMaxH;
        public final float dividerX;
        public final boolean hasFields;
        public final List<TextBlockPlacement> textBlocks;
        public final RectF contactRect;

        public HouseDynamicPlan(
                int width,
                int totalHeight,
                @NonNull RectF outerRect,
                float headerTop,
                @NonNull RectF titleRect,
                @NonNull StaticLayout titleLayout,
                @NonNull RectF panelRect,
                float illustCenterX,
                float illustCenterY,
                float illustMaxW,
                float illustMaxH,
                float dividerX,
                boolean hasFields,
                @NonNull List<TextBlockPlacement> textBlocks,
                @NonNull RectF contactRect
        ) {
            this.width = width;
            this.totalHeight = totalHeight;
            this.outerRect = outerRect;
            this.headerTop = headerTop;
            this.titleRect = titleRect;
            this.titleLayout = titleLayout;
            this.panelRect = panelRect;
            this.illustCenterX = illustCenterX;
            this.illustCenterY = illustCenterY;
            this.illustMaxW = illustMaxW;
            this.illustMaxH = illustMaxH;
            this.dividerX = dividerX;
            this.hasFields = hasFields;
            this.textBlocks = textBlocks;
            this.contactRect = contactRect;
        }
    }

    public static final class LandDynamicPlan {
        public final int width;
        public final int totalHeight;
        public final RectF outerRect;
        public final float headerTop;
        public final RectF titleRect;
        public final StaticLayout titleLayout;
        public final RectF panelRect;
        public final RectF illustRect;
        public final float dividerX;
        public final boolean hasMainFields;
        public final List<TextBlockPlacement> mainBlocks;
        public final boolean hasDagKhatianRow;
        public final boolean hasDag;
        public final boolean hasKhatian;
        @NonNull
        public final RectF dagKhatianRect;
        @Nullable
        public final StaticLayout dagLayout;
        @Nullable
        public final StaticLayout khatianLayout;
        public final boolean hasAdditional;
        @Nullable
        public final StaticLayout additionalLayout;
        public final float additionalTopY;
        public final float additionalLeftX;
        public final RectF contactRect;

        public LandDynamicPlan(
                int width,
                int totalHeight,
                @NonNull RectF outerRect,
                float headerTop,
                @NonNull RectF titleRect,
                @NonNull StaticLayout titleLayout,
                @NonNull RectF panelRect,
                @NonNull RectF illustRect,
                float dividerX,
                boolean hasMainFields,
                @NonNull List<TextBlockPlacement> mainBlocks,
                boolean hasDagKhatianRow,
                boolean hasDag,
                boolean hasKhatian,
                @NonNull RectF dagKhatianRect,
                @Nullable StaticLayout dagLayout,
                @Nullable StaticLayout khatianLayout,
                boolean hasAdditional,
                @Nullable StaticLayout additionalLayout,
                float additionalTopY,
                float additionalLeftX,
                @NonNull RectF contactRect
        ) {
            this.width = width;
            this.totalHeight = totalHeight;
            this.outerRect = outerRect;
            this.headerTop = headerTop;
            this.titleRect = titleRect;
            this.titleLayout = titleLayout;
            this.panelRect = panelRect;
            this.illustRect = illustRect;
            this.dividerX = dividerX;
            this.hasMainFields = hasMainFields;
            this.mainBlocks = mainBlocks;
            this.hasDagKhatianRow = hasDagKhatianRow;
            this.hasDag = hasDag;
            this.hasKhatian = hasKhatian;
            this.dagKhatianRect = dagKhatianRect;
            this.dagLayout = dagLayout;
            this.khatianLayout = khatianLayout;
            this.hasAdditional = hasAdditional;
            this.additionalLayout = additionalLayout;
            this.additionalTopY = additionalTopY;
            this.additionalLeftX = additionalLeftX;
            this.contactRect = contactRect;
        }
    }

    private DynamicPosterLayoutEngine() {
    }

    @NonNull
    public static HouseDynamicPlan solveHouseDynamicLayout(
            @NonNull PosterThemeConfig theme,
            @NonNull String titleText,
            @NonNull List<PropertyFieldValue> nonEmptyVisibleFields,
            @Nullable Typeface titleTypeface,
            @Nullable Typeface paragraphTypeface,
            int canvasWidth
    ) {
        float cardLeft = CARD_SIDE_MARGIN_PX;
        float cardRight = canvasWidth - CARD_SIDE_MARGIN_PX;
        float cardWidth = cardRight - cardLeft;

        float headerTop = 36f;
        float titleTop = headerTop + 144f;

        String safeTitle = titleText.trim().isEmpty() ? theme.getTitleText() : titleText.trim();
        TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTypeface(titleTypeface);
        int titleInnerW = Math.round(cardWidth - 110f);

        float titleFontSize = PosterThemeConfig.FIXED_TITLE_FONT_SIZE_PX;
        StaticLayout titleLayout = buildStaticLayout(
                safeTitle, titlePaint, titleInnerW, titleFontSize, Layout.Alignment.ALIGN_CENTER, 1.12f);
        while (titleLayout.getLineCount() > 2 && titleFontSize > PosterThemeConfig.MIN_TITLE_FONT_SIZE_PX) {
            titleFontSize -= 2f;
            titleLayout = buildStaticLayout(
                    safeTitle, titlePaint, titleInnerW, titleFontSize, Layout.Alignment.ALIGN_CENTER, 1.12f);
        }
        float titleH = Math.max(158f, titleLayout.getHeight() + 58f);
        RectF titleRect = new RectF(cardLeft, titleTop, cardRight, titleTop + titleH);

        float panelTop = titleRect.bottom + SECTION_GAP_PX;
        float illustPaneW = 316f;
        float dividerX = cardLeft + illustPaneW;
        float textLeft = dividerX + 34f;
        float textRight = cardRight - 34f;
        int textMaxW = Math.max(120, Math.round(textRight - textLeft));

        int totalChars = 0;
        for (PropertyFieldValue fv : nonEmptyVisibleFields) {
            totalChars += fv.getPlainText().length();
        }
        float fontSize = nonEmptyVisibleFields.size() <= 2 ? 48f : 44f;
        if (totalChars > 500) {
            fontSize = 38f;
        }

        TextPaint bodyPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(theme.getDetailsTextColor());
        bodyPaint.setTypeface(paragraphTypeface);
        bodyPaint.setTextSize(fontSize);

        float fieldGap = 20f;
        List<StaticLayout> rawLayouts = new ArrayList<>(nonEmptyVisibleFields.size());
        float textTotalH = 0f;
        for (int i = 0; i < nonEmptyVisibleFields.size(); i++) {
            PropertyFieldValue fv = nonEmptyVisibleFields.get(i);
            SpannableStringBuilder inlineSpannable = buildInlineFieldSpannable(fv, paragraphTypeface);
            StaticLayout layout = StaticLayout.Builder
                    .obtain(inlineSpannable, 0, inlineSpannable.length(), bodyPaint, textMaxW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.18f)
                    .setIncludePad(true)
                    .build();
            rawLayouts.add(layout);
            textTotalH += layout.getHeight();
            if (i < nonEmptyVisibleFields.size() - 1) {
                textTotalH += fieldGap;
            }
        }

        boolean hasFields = !nonEmptyVisibleFields.isEmpty();
        float minPanelH = hasFields ? 316f : 256f;
        float padV = 34f;
        float panelH = Math.max(minPanelH, textTotalH + padV * 2f);
        RectF panelRect = new RectF(cardLeft, panelTop, cardRight, panelTop + panelH);

        List<TextBlockPlacement> placements = new ArrayList<>(rawLayouts.size());
        float innerAvailH = panelH - padV * 2f;
        float cursorY = panelRect.top + padV + Math.max(0f, (innerAvailH - textTotalH) / 2f);
        for (int i = 0; i < nonEmptyVisibleFields.size(); i++) {
            StaticLayout l = rawLayouts.get(i);
            placements.add(new TextBlockPlacement(nonEmptyVisibleFields.get(i), l, textLeft, cursorY));
            cursorY += l.getHeight() + fieldGap;
        }

        float contactTop = panelRect.bottom + SECTION_GAP_PX;
        RectF contactRect = new RectF(cardLeft, contactTop, cardRight, contactTop + CONTACT_HEIGHT_PX);
        int totalHeight = Math.round(contactRect.bottom + 46f);
        RectF outerRect = new RectF(
                OUTER_MARGIN_PX,
                OUTER_MARGIN_PX,
                canvasWidth - OUTER_MARGIN_PX,
                totalHeight - OUTER_MARGIN_PX
        );

        float illustCenterX = hasFields ? (cardLeft + illustPaneW / 2f) : (canvasWidth / 2f);
        float illustCenterY = panelRect.centerY();
        float illustMaxW = hasFields ? (illustPaneW - 24f) : (cardWidth - 80f);
        float illustMaxH = hasFields ? Math.min(280f, panelH - 40f) : (panelH - 36f);

        return new HouseDynamicPlan(
                canvasWidth,
                totalHeight,
                outerRect,
                headerTop,
                titleRect,
                titleLayout,
                panelRect,
                illustCenterX,
                illustCenterY,
                illustMaxW,
                illustMaxH,
                dividerX,
                hasFields,
                placements,
                contactRect
        );
    }

    @NonNull
    public static LandDynamicPlan solveLandDynamicLayout(
            @NonNull PosterThemeConfig theme,
            @NonNull String titleText,
            @NonNull List<PropertyFieldValue> nonEmptyVisibleFields,
            @Nullable Typeface titleTypeface,
            @Nullable Typeface paragraphTypeface,
            int canvasWidth
    ) {
        float cardLeft = CARD_SIDE_MARGIN_PX;
        float cardRight = canvasWidth - CARD_SIDE_MARGIN_PX;
        float cardWidth = cardRight - cardLeft;

        float headerTop = 34f;
        float titleTop = headerTop + 128f;

        String safeTitle = titleText.trim().isEmpty() ? theme.getTitleText() : titleText.trim();
        TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTypeface(titleTypeface);
        int titleInnerW = Math.round(cardWidth - 110f);

        float titleFontSize = 88f;
        StaticLayout titleLayout = buildStaticLayout(
                safeTitle, titlePaint, titleInnerW, titleFontSize, Layout.Alignment.ALIGN_CENTER, 1.12f);
        while (titleLayout.getLineCount() > 2 && titleFontSize > PosterThemeConfig.MIN_TITLE_FONT_SIZE_PX) {
            titleFontSize -= 2f;
            titleLayout = buildStaticLayout(
                    safeTitle, titlePaint, titleInnerW, titleFontSize, Layout.Alignment.ALIGN_CENTER, 1.12f);
        }
        float titleH = Math.max(158f, titleLayout.getHeight() + 58f);
        RectF titleRect = new RectF(cardLeft, titleTop, cardRight, titleTop + titleH);

        PropertyFieldValue dagField = null;
        PropertyFieldValue khatianField = null;
        PropertyFieldValue additionalField = null;
        List<PropertyFieldValue> mainPanelFields = new ArrayList<>();

        for (PropertyFieldValue fv : nonEmptyVisibleFields) {
            String id = fv.getFieldId();
            if ("dag_number".equals(id)) {
                dagField = fv;
            } else if ("khatian_number".equals(id)) {
                khatianField = fv;
            } else if ("additional_info".equals(id)) {
                additionalField = fv;
            } else {
                mainPanelFields.add(fv);
            }
        }

        float panelTop = titleRect.bottom + 20f;
        float illustPad = 18f;
        float illustW = 368f;
        float illustH = 258f;
        float dividerX = cardLeft + illustPad + illustW + 18f;
        float textLeft = dividerX + 28f;
        float textRight = cardRight - 28f;
        int textMaxW = Math.max(120, Math.round(textRight - textLeft));

        int totalMainChars = 0;
        for (PropertyFieldValue fv : mainPanelFields) {
            totalMainChars += fv.getPlainText().length();
        }
        float mainFontSize = (mainPanelFields.size() <= 4 && totalMainChars <= 180) ? 45f : 41f;
        if (totalMainChars > 460) {
            mainFontSize = 36f;
        }

        TextPaint bodyPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(theme.getDetailsTextColor());
        bodyPaint.setTypeface(paragraphTypeface);
        bodyPaint.setTextSize(mainFontSize);

        float mainFieldGap = 12f;
        List<StaticLayout> mainLayouts = new ArrayList<>(mainPanelFields.size());
        float mainTextTotalH = 0f;
        for (int i = 0; i < mainPanelFields.size(); i++) {
            SpannableStringBuilder sb = buildInlineFieldSpannable(mainPanelFields.get(i), paragraphTypeface);
            StaticLayout layout = StaticLayout.Builder
                    .obtain(sb, 0, sb.length(), bodyPaint, textMaxW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.16f)
                    .setIncludePad(true)
                    .build();
            mainLayouts.add(layout);
            mainTextTotalH += layout.getHeight();
            if (i < mainPanelFields.size() - 1) {
                mainTextTotalH += mainFieldGap;
            }
        }

        boolean hasMainFields = !mainPanelFields.isEmpty();
        float panelPadV = 22f;
        float panelH = Math.max(illustH + panelPadV * 2f, mainTextTotalH + panelPadV * 2f);
        RectF panelRect = new RectF(cardLeft, panelTop, cardRight, panelTop + panelH);

        float illustLeft = hasMainFields ? (cardLeft + illustPad) : (canvasWidth / 2f - illustW / 2f);
        float illustTop = panelRect.top + (panelH - illustH) / 2f;
        RectF illustRect = new RectF(illustLeft, illustTop, illustLeft + illustW, illustTop + illustH);

        List<TextBlockPlacement> mainBlocks = new ArrayList<>(mainLayouts.size());
        float innerAvailH = panelH - panelPadV * 2f;
        float curY = panelRect.top + panelPadV + Math.max(0f, (innerAvailH - mainTextTotalH) / 2f);
        for (int i = 0; i < mainPanelFields.size(); i++) {
            StaticLayout l = mainLayouts.get(i);
            mainBlocks.add(new TextBlockPlacement(mainPanelFields.get(i), l, textLeft, curY));
            curY += l.getHeight() + mainFieldGap;
        }

        float cursorBottomY = panelRect.bottom;

        boolean hasDag = dagField != null && !dagField.isEmpty();
        boolean hasKhatian = khatianField != null && !khatianField.isEmpty();
        boolean hasDagKhatianRow = hasDag || hasKhatian;
        RectF dagKhatianRect = new RectF();
        StaticLayout dagLayout = null;
        StaticLayout khatianLayout = null;

        if (hasDagKhatianRow) {
            float dagTop = cursorBottomY + 18f;
            int sideMaxW = Math.round((hasDag && hasKhatian) ? (cardWidth / 2f - 44f) : (cardWidth - 72f));
            TextPaint dagPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dagPaint.setColor(theme.getDetailsTextColor());
            dagPaint.setTypeface(paragraphTypeface);
            dagPaint.setTextSize(44f);

            int maxDagH = 0;
            if (hasDag) {
                SpannableStringBuilder sb = buildInlineFieldSpannable(dagField, paragraphTypeface);
                dagLayout = StaticLayout.Builder
                        .obtain(sb, 0, sb.length(), dagPaint, sideMaxW)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.12f)
                        .setIncludePad(true)
                        .build();
                maxDagH = Math.max(maxDagH, dagLayout.getHeight());
            }
            if (hasKhatian) {
                SpannableStringBuilder sb = buildInlineFieldSpannable(khatianField, paragraphTypeface);
                khatianLayout = StaticLayout.Builder
                        .obtain(sb, 0, sb.length(), dagPaint, sideMaxW)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.12f)
                        .setIncludePad(true)
                        .build();
                maxDagH = Math.max(maxDagH, khatianLayout.getHeight());
            }
            float rowH = Math.max(76f, maxDagH + 22f);
            dagKhatianRect = new RectF(cardLeft, dagTop, cardRight, dagTop + rowH);
            cursorBottomY = dagKhatianRect.bottom;
        }

        boolean hasAdditional = additionalField != null && !additionalField.isEmpty();
        StaticLayout additionalLayout = null;
        float addTopY = 0f;
        float addLeftX = cardLeft + 22f;

        if (hasAdditional) {
            addTopY = cursorBottomY + 16f;
            float addFontSize = additionalField.getPlainText().length() > 260 ? 36f : 40f;
            TextPaint addPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            addPaint.setColor(theme.getDetailsTextColor());
            addPaint.setTypeface(paragraphTypeface);
            addPaint.setTextSize(addFontSize);

            int addMaxW = Math.round(cardWidth - 44f);
            SpannableStringBuilder sb = buildInlineFieldSpannable(additionalField, paragraphTypeface);
            additionalLayout = StaticLayout.Builder
                    .obtain(sb, 0, sb.length(), addPaint, addMaxW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1.15f)
                    .setIncludePad(true)
                    .build();
            if (additionalLayout.getLineCount() > 2) {
                additionalLayout = StaticLayout.Builder
                        .obtain(sb, 0, sb.length(), addPaint, addMaxW)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.15f)
                        .setIncludePad(true)
                        .build();
            }
            cursorBottomY = addTopY + additionalLayout.getHeight();
        }

        float contactTop = cursorBottomY + 18f;
        RectF contactRect = new RectF(cardLeft, contactTop, cardRight, contactTop + CONTACT_HEIGHT_PX);
        int totalHeight = Math.round(contactRect.bottom + 44f);
        RectF outerRect = new RectF(
                OUTER_MARGIN_PX,
                OUTER_MARGIN_PX,
                canvasWidth - OUTER_MARGIN_PX,
                totalHeight - OUTER_MARGIN_PX
        );

        return new LandDynamicPlan(
                canvasWidth,
                totalHeight,
                outerRect,
                headerTop,
                titleRect,
                titleLayout,
                panelRect,
                illustRect,
                dividerX,
                hasMainFields,
                mainBlocks,
                hasDagKhatianRow,
                hasDag,
                hasKhatian,
                dagKhatianRect,
                dagLayout,
                khatianLayout,
                hasAdditional,
                additionalLayout,
                addTopY,
                addLeftX,
                contactRect
        );
    }

    @NonNull
    private static SpannableStringBuilder buildInlineFieldSpannable(
            @NonNull PropertyFieldValue fv,
            @Nullable Typeface paragraphTypeface
    ) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String label = fv.getLabel().trim();
        if (!label.isEmpty()) {
            sb.append(label).append("ঃ ");
        }
        sb.append(PosterTextFitter.toSpannable(fv.getSegments(), paragraphTypeface));
        return sb;
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
     * Pure numeric geometry helper preserved for JVM unit test compatibility.
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

        float cardLeft = 56f;
        float cardRight = canvasWidth - 56f;

        float stackBottom = canvasHeight - STACK_BOTTOM_MARGIN_PX;
        float stackBudget = stackBottom - STACK_TOP_PX;

        float maxDetailsH = getMaxDetailsCardHeight(canvasHeight);
        float detailsH = clamp(desiredDetailsCardHeight, MIN_DETAILS_HEIGHT_PX, maxDetailsH);

        float titleH = MIN_TITLE_HEIGHT_PX;
        float contactH = 158f;

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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
