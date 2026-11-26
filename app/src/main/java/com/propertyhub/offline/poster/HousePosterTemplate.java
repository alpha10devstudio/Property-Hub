package com.propertyhub.offline.poster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyFieldValue;
import com.propertyhub.offline.model.PropertyRecord;

import java.util.List;

/**
 * 1440x1920 (3:4 Portrait) House for Rent Poster Template.
 * Navy Blue + White real estate theme with pitched roof emblem, editable Title banner,
 * architectural house illustration showcase, dynamic non-empty field cards (with B/I formatting),
 * and balanced bottom Contact card.
 */
public final class HousePosterTemplate {

    private HousePosterTemplate() {
    }

    public static boolean render(
            @Nullable Context context,
            @NonNull Canvas canvas,
            @NonNull PropertyRecord record,
            int width,
            int height
    ) {
        PosterThemeConfig theme = PosterThemeConfig.houseForRent();
        int navyBlue = theme.getPrimaryColor();
        int softSurface = theme.getSoftSurfaceColor();
        int cardBorder = theme.getCardBorderColor();

        Typeface titleTypeface = PosterFontLoader.loadTitleTypeface(context);
        Typeface paragraphTypeface = PosterFontLoader.loadParagraphTypeface(context);

        List<PropertyFieldValue> visibleFields = record.getPosterVisibleFieldValues();

        DynamicPosterLayoutEngine.PortraitLayoutPlan layout = DynamicPosterLayoutEngine.solvePortraitLayout(
                theme,
                record.getTitle(),
                visibleFields,
                titleTypeface,
                paragraphTypeface,
                width,
                height,
                false,
                true
        );

        // 1. Crisp White Background & Navy Blue Rounded Outer Border
        canvas.drawColor(theme.getCanvasBackgroundColor());

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(navyBlue);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(7f);
        canvas.drawRoundRect(
                layout.getOuterRect(),
                theme.getOuterBorderRadius(),
                theme.getOuterBorderRadius(),
                borderPaint
        );

        // 2. Top Roof Silhouette + Symmetric Tapered Wings
        drawTopRoofEmblem(canvas, width, layout.getHeaderRect(), navyBlue);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        // 3. Navy Blue Rounded Title Banner (Editable Title rendered in title.ttf)
        RectF titleRect = layout.getTitleRect();
        fillPaint.setColor(navyBlue);
        canvas.drawRoundRect(
                titleRect,
                theme.getTitleCornerRadius(),
                theme.getTitleCornerRadius(),
                fillPaint
        );

        StaticLayout titleLayout = layout.getTitleLayout();
        canvas.save();
        float titleTextLeft = titleRect.centerX() - titleLayout.getWidth() / 2f;
        float titleTextTop = titleRect.centerY() - titleLayout.getHeight() / 2f;
        canvas.translate(titleTextLeft, titleTextTop);
        titleLayout.draw(canvas);
        canvas.restore();

        // 4. Middle Outer Frame + Showcase Strip + Dynamic Field Cards
        RectF bodyFrame = layout.getBodyFrameRect();
        fillPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(bodyFrame, theme.getDetailsCornerRadius(), theme.getDetailsCornerRadius(), fillPaint);

        borderPaint.setColor(navyBlue);
        borderPaint.setStrokeWidth(5f);
        canvas.drawRoundRect(bodyFrame, theme.getDetailsCornerRadius(), theme.getDetailsCornerRadius(), borderPaint);

        // Showcase Strip inside Body Frame with Two-Story House Illustration
        drawHouseShowcaseStrip(canvas, layout.getShowcaseRect(), navyBlue, softSurface, cardBorder);

        // Render every non-empty dynamic field card (empty fields are omitted completely)
        drawHouseFieldCards(canvas, layout, navyBlue, softSurface, cardBorder, paragraphTypeface);

        // 5. Bottom Navy Blue Contact Banner + White Phone Circle + Mobile Number
        RectF contactRect = layout.getContactRect();
        fillPaint.setColor(navyBlue);
        canvas.drawRoundRect(
                contactRect,
                theme.getContactCornerRadius(),
                theme.getContactCornerRadius(),
                fillPaint
        );

        float phoneCircleRadius = Math.min(54f, contactRect.height() * 0.34f);
        float phoneCircleCx = contactRect.left + 106f;
        float phoneCircleCy = contactRect.centerY();
        fillPaint.setColor(Color.WHITE);
        canvas.drawCircle(phoneCircleCx, phoneCircleCy, phoneCircleRadius, fillPaint);

        drawNavyHandsetIcon(canvas, phoneCircleCx, phoneCircleCy, phoneCircleRadius, navyBlue);

        Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(Color.WHITE);
        dividerPaint.setStrokeWidth(4.5f);
        dividerPaint.setStrokeCap(Paint.Cap.ROUND);
        float contactDividerX = contactRect.left + 196f;
        float contactDividerInset = contactRect.height() * 0.22f;
        canvas.drawLine(
                contactDividerX,
                contactRect.top + contactDividerInset,
                contactDividerX,
                contactRect.bottom - contactDividerInset,
                dividerPaint
        );

        String contactLine = theme.getContactPrefix() + record.getMobile();
        float mobileAreaLeft = contactDividerX + 26f;
        float mobileAreaRight = contactRect.right - 34f;
        float mobileAreaWidth = mobileAreaRight - mobileAreaLeft;

        TextPaint mobilePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mobilePaint.setColor(Color.WHITE);
        mobilePaint.setTypeface(Typeface.create(paragraphTypeface, Typeface.BOLD));
        mobilePaint.setFakeBoldText(true);
        mobilePaint.setTextAlign(Paint.Align.CENTER);
        float fittedContactSize = PosterTextFitter.fitSingleLineTextSize(
                contactLine,
                mobilePaint,
                mobileAreaWidth - 16f,
                theme.getFixedContactFontSizePx(),
                PosterThemeConfig.MIN_CONTACT_FONT_SIZE_PX
        );
        mobilePaint.setTextSize(fittedContactSize);
        Paint.FontMetrics mobileFm = mobilePaint.getFontMetrics();
        float mobileBaseline = contactRect.centerY() - (mobileFm.ascent + mobileFm.descent) / 2f;
        canvas.drawText(
                contactLine,
                mobileAreaLeft + mobileAreaWidth / 2f,
                mobileBaseline,
                mobilePaint
        );

        return layout.isOverflowed();
    }

    private static void drawHouseShowcaseStrip(
            @NonNull Canvas canvas,
            @NonNull RectF showcaseRect,
            int navyBlue,
            int softSurface,
            int cardBorder
    ) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(softSurface);
        canvas.drawRoundRect(showcaseRect, 24f, 24f, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(cardBorder);
        stroke.setStrokeWidth(3f);
        canvas.drawRoundRect(showcaseRect, 24f, 24f, stroke);

        // Decorative architectural side rules flanking centered Two-Story House graphic
        stroke.setColor(navyBlue);
        stroke.setAlpha(65);
        stroke.setStrokeWidth(4f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        float midY = showcaseRect.centerY();
        canvas.drawLine(showcaseRect.left + 44f, midY, showcaseRect.centerX() - 175f, midY, stroke);
        canvas.drawLine(showcaseRect.centerX() + 175f, midY, showcaseRect.right - 44f, midY, stroke);

        stroke.setAlpha(35);
        stroke.setStrokeWidth(2.5f);
        canvas.drawLine(showcaseRect.left + 84f, midY + 20f, showcaseRect.centerX() - 175f, midY + 20f, stroke);
        canvas.drawLine(showcaseRect.centerX() + 175f, midY + 20f, showcaseRect.right - 84f, midY + 20f, stroke);

        drawHouseArchitecturalGraphic(canvas, showcaseRect, navyBlue);
    }

    private static void drawHouseFieldCards(
            @NonNull Canvas canvas,
            @NonNull DynamicPosterLayoutEngine.PortraitLayoutPlan layout,
            int navyBlue,
            int softSurface,
            int cardBorder,
            @Nullable Typeface paragraphTypeface
    ) {
        Paint cardFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardFill.setStyle(Paint.Style.FILL);
        cardFill.setColor(softSurface);

        Paint cardStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardStroke.setStyle(Paint.Style.STROKE);
        cardStroke.setColor(cardBorder);
        cardStroke.setStrokeWidth(3f);

        Paint accentBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        accentBar.setStyle(Paint.Style.FILL);
        accentBar.setColor(navyBlue);

        Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(navyBlue);

        for (DynamicPosterLayoutEngine.FieldCardPlacement card : layout.getFieldCards()) {
            RectF r = card.getCardRect();
            canvas.drawRoundRect(r, 22f, 22f, cardFill);
            canvas.drawRoundRect(r, 22f, 22f, cardStroke);

            // Clean Navy top-left icon dot & accent stripe
            canvas.drawRoundRect(
                    new RectF(r.left + 2f, r.top + 16f, r.left + 10f, r.bottom - 16f),
                    4f, 4f, accentBar
            );

            RectF labelRect = card.getLabelRect();
            RectF valueRect = card.getValueRect();

            // Field Label + small vector indicator
            drawHouseFieldIcon(
                    canvas,
                    card.getFieldValue().getFieldId(),
                    labelRect.left + 12f,
                    labelRect.centerY(),
                    11f,
                    navyBlue
            );

            canvas.save();
            canvas.translate(labelRect.left + 32f, labelRect.top);
            card.getLabelLayout().draw(canvas);
            canvas.restore();

            // Rich Text Value (paragraph.ttf + Bold/Italic)
            canvas.save();
            canvas.clipRect(valueRect.left - 4f, valueRect.top - 4f, valueRect.right + 4f, r.bottom - 6f);
            canvas.translate(valueRect.left, valueRect.top);
            card.getValueLayout().draw(canvas);
            canvas.restore();
        }
    }

    private static void drawHouseFieldIcon(
            @NonNull Canvas canvas,
            @NonNull String fieldId,
            float cx,
            float cy,
            float radius,
            int navyBlue
    ) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(navyBlue);
        if ("address".equals(fieldId)) {
            p.setStyle(Paint.Style.FILL);
            Path pin = new Path();
            pin.addCircle(cx, cy - radius * 0.2f, radius * 0.65f, Path.Direction.CW);
            pin.moveTo(cx - radius * 0.52f, cy + radius * 0.1f);
            pin.lineTo(cx, cy + radius * 0.95f);
            pin.lineTo(cx + radius * 0.52f, cy + radius * 0.1f);
            pin.close();
            canvas.drawPath(pin, p);
        } else {
            p.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(
                    new RectF(cx - radius * 0.65f, cy - radius * 0.65f, cx + radius * 0.65f, cy + radius * 0.65f),
                    3.5f, 3.5f, p
            );
        }
    }

    private static void drawTopRoofEmblem(
            @NonNull Canvas canvas,
            int width,
            @NonNull RectF headerRect,
            int navyBlue
    ) {
        Paint navyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        navyPaint.setColor(navyBlue);
        navyPaint.setStyle(Paint.Style.FILL);

        float cx = width / 2f;
        float dy = headerRect.top - 22f;

        canvas.save();
        canvas.translate(0f, dy);

        // Left horizontal tapered wing
        Path leftWing = new Path();
        leftWing.moveTo(96f, 114f);
        leftWing.lineTo(430f, 106f);
        leftWing.arcTo(new RectF(422f, 106f, 440f, 122f), -90f, 180f, false);
        leftWing.lineTo(96f, 114f);
        leftWing.close();
        canvas.drawPath(leftWing, navyPaint);

        // Right horizontal tapered wing
        Path rightWing = new Path();
        rightWing.moveTo(width - 96f, 114f);
        rightWing.lineTo(width - 430f, 106f);
        rightWing.arcTo(new RectF(width - 440f, 106f, width - 422f, 122f), -90f, -180f, false);
        rightWing.lineTo(width - 96f, 114f);
        rightWing.close();
        canvas.drawPath(rightWing, navyPaint);

        // Left angled roof accent beam
        Path leftBeam = new Path();
        leftBeam.moveTo(438f, 150f);
        leftBeam.lineTo(618f, 48f);
        leftBeam.lineTo(632f, 56f);
        leftBeam.lineTo(466f, 150f);
        leftBeam.close();
        canvas.drawPath(leftBeam, navyPaint);

        // Main pitched roof + right chimney
        Path mainRoof = new Path();
        mainRoof.moveTo(482f, 150f);
        mainRoof.lineTo(720f, 32f);
        mainRoof.lineTo(824f, 84f);
        mainRoof.lineTo(824f, 54f);
        mainRoof.lineTo(858f, 54f);
        mainRoof.lineTo(858f, 101f);
        mainRoof.lineTo(960f, 150f);
        mainRoof.lineTo(890f, 150f);
        mainRoof.lineTo(720f, 64f);
        mainRoof.lineTo(548f, 150f);
        mainRoof.close();
        canvas.drawPath(mainRoof, navyPaint);

        // Centered 2x2 roof window panes
        float winSize = 18f;
        float gap = 8f;
        float startX = cx - winSize - gap / 2f;
        float startY = 98f;
        canvas.drawRoundRect(new RectF(startX, startY, startX + winSize, startY + winSize), 2f, 2f, navyPaint);
        canvas.drawRoundRect(new RectF(startX + winSize + gap, startY, startX + 2f * winSize + gap, startY + winSize), 2f, 2f, navyPaint);
        canvas.drawRoundRect(new RectF(startX, startY + winSize + gap, startX + winSize, startY + 2f * winSize + gap), 2f, 2f, navyPaint);
        canvas.drawRoundRect(new RectF(startX + winSize + gap, startY + winSize + gap, startX + 2f * winSize + gap, startY + 2f * winSize + gap), 2f, 2f, navyPaint);

        canvas.restore();
    }

    private static void drawHouseArchitecturalGraphic(
            @NonNull Canvas canvas,
            @NonNull RectF showcaseRect,
            int navyBlue
    ) {
        Paint navy = new Paint(Paint.ANTI_ALIAS_FLAG);
        navy.setColor(navyBlue);
        navy.setStyle(Paint.Style.FILL);

        Paint navyStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        navyStroke.setColor(navyBlue);
        navyStroke.setStyle(Paint.Style.STROKE);
        navyStroke.setStrokeCap(Paint.Cap.ROUND);
        navyStroke.setStrokeJoin(Paint.Join.ROUND);

        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);
        white.setStyle(Paint.Style.FILL);

        float scale = Math.min(
                0.70f,
                Math.min((showcaseRect.width() - 40f) / 310f, (showcaseRect.height() - 24f) / 322f)
        );

        canvas.save();
        canvas.translate(showcaseRect.centerX(), showcaseRect.centerY());
        canvas.scale(scale, scale);
        canvas.translate(-210f, -613f);

        float cardTopRef = 408f;
        float baseBottom = 772f;

        Path ground = new Path();
        ground.moveTo(64f, baseBottom);
        ground.quadTo(208f, baseBottom - 26f, 354f, baseBottom);
        ground.quadTo(208f, baseBottom - 12f, 64f, baseBottom);
        ground.close();
        canvas.drawPath(ground, navy);

        float treeCx = 100f;
        float trunkBottom = baseBottom - 8f;
        canvas.drawRect(treeCx - 6f, trunkBottom - 54f, treeCx + 6f, trunkBottom, navy);
        canvas.drawCircle(treeCx, trunkBottom - 86f, 30f, navy);
        canvas.drawCircle(treeCx - 16f, trunkBottom - 68f, 20f, navy);
        canvas.drawCircle(treeCx + 16f, trunkBottom - 68f, 20f, navy);

        Paint whiteLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteLine.setColor(Color.WHITE);
        whiteLine.setStrokeWidth(3f);
        whiteLine.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(treeCx, trunkBottom - 54f, treeCx - 10f, trunkBottom - 68f, whiteLine);
        canvas.drawLine(treeCx, trunkBottom - 58f, treeCx + 9f, trunkBottom - 70f, whiteLine);

        float houseLeft = 140f;
        float houseRight = 334f;
        float midWingX = 212f;
        float wingTop = cardTopRef + 114f;
        float gablePeakX = 274f;
        float gablePeakY = cardTopRef + 46f;
        float eaveY = cardTopRef + 106f;
        float houseBottom = baseBottom - 10f;

        navyStroke.setStrokeWidth(6f);
        canvas.drawLine(houseLeft - 8f, wingTop, midWingX + 4f, wingTop, navyStroke);
        canvas.drawRect(houseLeft, wingTop, midWingX, houseBottom - 4f, navyStroke);

        RectF upperLeftWin = new RectF(houseLeft + 14f, wingTop + 18f, midWingX - 10f, wingTop + 72f);
        navyStroke.setStrokeWidth(4f);
        canvas.drawRect(upperLeftWin, navyStroke);
        canvas.drawLine(upperLeftWin.centerX(), upperLeftWin.top, upperLeftWin.centerX(), upperLeftWin.bottom, navyStroke);

        float floorSplitY = wingTop + 88f;
        navyStroke.setStrokeWidth(7f);
        canvas.drawLine(houseLeft - 10f, floorSplitY, houseRight + 10f, floorSplitY, navyStroke);

        navyStroke.setStrokeWidth(6f);
        canvas.drawLine(midWingX, eaveY - 8f, midWingX, houseBottom - 4f, navyStroke);
        canvas.drawLine(houseRight, eaveY - 8f, houseRight, houseBottom - 4f, navyStroke);

        Path roof = new Path();
        roof.moveTo(192f, eaveY);
        roof.lineTo(gablePeakX, gablePeakY);
        roof.lineTo(312f, gablePeakY + 24f);
        roof.lineTo(312f, gablePeakY + 4f);
        roof.lineTo(330f, gablePeakY + 4f);
        roof.lineTo(330f, gablePeakY + 34f);
        roof.lineTo(houseRight + 22f, eaveY);
        roof.lineTo(houseRight + 6f, eaveY + 12f);
        roof.lineTo(gablePeakX, gablePeakY + 18f);
        roof.lineTo(206f, eaveY + 12f);
        roof.close();
        canvas.drawPath(roof, navy);

        float gwSize = 20f;
        float gwGap = 6f;
        float gwLeft = gablePeakX - gwSize - gwGap / 2f;
        float gwTop = gablePeakY + 56f;
        canvas.drawRect(gwLeft, gwTop, gwLeft + gwSize, gwTop + gwSize, navy);
        canvas.drawRect(gwLeft + gwSize + gwGap, gwTop, gwLeft + 2f * gwSize + gwGap, gwTop + gwSize, navy);
        canvas.drawRect(gwLeft, gwTop + gwSize + gwGap, gwLeft + gwSize, gwTop + 2f * gwSize + gwGap, navy);
        canvas.drawRect(gwLeft + gwSize + gwGap, gwTop + gwSize + gwGap, gwLeft + 2f * gwSize + gwGap, gwTop + 2f * gwSize + gwGap, navy);

        navyStroke.setStrokeWidth(4.5f);
        canvas.drawRect(houseLeft + 14f, floorSplitY + 20f, midWingX - 14f, floorSplitY + 68f, navyStroke);

        RectF doorRect = new RectF(midWingX + 42f, floorSplitY + 20f, houseRight - 18f, houseBottom - 4f);
        canvas.drawRect(doorRect, navy);
        canvas.drawCircle(doorRect.centerX() - 9f, doorRect.centerY() + 2f, 3.2f, white);
        canvas.drawCircle(doorRect.centerX() + 9f, doorRect.centerY() + 2f, 3.2f, white);

        canvas.restore();
    }

    private static void drawNavyHandsetIcon(
            @NonNull Canvas canvas,
            float cx,
            float cy,
            float circleRadius,
            int navyBlue
    ) {
        Paint phonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        phonePaint.setColor(navyBlue);
        phonePaint.setStyle(Paint.Style.FILL);

        float s = circleRadius / 64f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(s, s);

        Path handset = new Path();
        handset.moveTo(-22f, -34f);
        handset.quadTo(-34f, -32f, -34f, -18f);
        handset.cubicTo(-34f, 6f, -6f, 34f, 18f, 34f);
        handset.quadTo(32f, 34f, 34f, 22f);
        handset.lineTo(34f, 12f);
        handset.quadTo(34f, 6f, 26f, 4f);
        handset.lineTo(14f, 2f);
        handset.quadTo(9f, 1f, 6f, 5f);
        handset.lineTo(1f, 11f);
        handset.quadTo(-8f, 6f, -11f, -1f);
        handset.lineTo(-5f, -6f);
        handset.quadTo(-1f, -9f, -2f, -14f);
        handset.lineTo(-4f, -26f);
        handset.quadTo(-6f, -34f, -12f, -34f);
        handset.close();

        canvas.drawPath(handset, phonePaint);
        canvas.restore();
    }
}
