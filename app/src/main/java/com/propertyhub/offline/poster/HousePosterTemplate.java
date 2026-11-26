package com.propertyhub.offline.poster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyRecord;

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

        Typeface titleTypeface = PosterFontLoader.loadTitleTypeface(context);
        Typeface paragraphTypeface = PosterFontLoader.loadParagraphTypeface(context);

        DynamicPosterLayoutEngine.LayoutPlan layout = DynamicPosterLayoutEngine.solveLayout(
                theme,
                record.getDetails(),
                paragraphTypeface,
                width,
                height
        );

        // 1. Pure White Background & Navy Blue Rounded Outer Border
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

        // 2. Top Roof Silhouette + Symmetric Tapered Wings anchored to dynamic header
        drawTopRoofEmblem(canvas, width, layout.getHeaderRect(), navyBlue);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(navyBlue);

        // 3. Navy Blue Rounded Title Banner (FIXED Title font size — no auto-scaling)
        RectF titleRect = layout.getTitleRect();
        canvas.drawRoundRect(
                titleRect,
                theme.getTitleCornerRadius(),
                theme.getTitleCornerRadius(),
                fillPaint
        );

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTypeface(titleTypeface);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(theme.getFixedTitleFontSizePx());
        Paint.FontMetrics titleFm = titlePaint.getFontMetrics();
        float titleBaseline = titleRect.centerY() - (titleFm.ascent + titleFm.descent) / 2f;
        canvas.drawText(theme.getTitleText(), titleRect.centerX(), titleBaseline, titlePaint);

        // 4. Dynamic Middle White Details Card with Navy Blue Border
        RectF detailsCard = layout.getDetailsCardRect();
        fillPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(
                detailsCard,
                theme.getDetailsCornerRadius(),
                theme.getDetailsCornerRadius(),
                fillPaint
        );

        borderPaint.setStrokeWidth(6f);
        canvas.drawRoundRect(
                detailsCard,
                theme.getDetailsCornerRadius(),
                theme.getDetailsCornerRadius(),
                borderPaint
        );

        // Left Two-Story House + Tree Architectural Illustration (proportionally scaled to card)
        drawDetailsCardHouseIllustration(canvas, detailsCard, layout.getDividerX(), navyBlue);

        // Vertical Divider Line
        Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(navyBlue);
        dividerPaint.setStrokeWidth(5f);
        dividerPaint.setStrokeCap(Paint.Cap.ROUND);
        float dividerX = layout.getDividerX();
        float dividerInset = Math.min(38f, detailsCard.height() * 0.12f);
        canvas.drawLine(
                dividerX,
                detailsCard.top + dividerInset,
                dividerX,
                detailsCard.bottom - dividerInset,
                dividerPaint
        );

        // Right Dynamic Details Paragraphs (paragraph.ttf + Bold/Italic Markdown)
        RectF textRect = layout.getDetailsTextRect();
        PosterTextFitter.drawFitResult(
                canvas,
                layout.getDetailsFit(),
                textRect.left,
                textRect.top,
                Math.round(textRect.width()),
                Math.round(textRect.height()),
                !layout.isOverflowed()
        );

        // 5. Bottom Navy Blue Contact Banner + White Phone Circle + Fixed Mobile Number
        RectF contactRect = layout.getContactRect();
        fillPaint.setColor(navyBlue);
        canvas.drawRoundRect(
                contactRect,
                theme.getContactCornerRadius(),
                theme.getContactCornerRadius(),
                fillPaint
        );

        float phoneCircleRadius = Math.min(62f, contactRect.height() * 0.32f);
        float phoneCircleCx = contactRect.left + 108f;
        float phoneCircleCy = contactRect.centerY();
        fillPaint.setColor(Color.WHITE);
        canvas.drawCircle(phoneCircleCx, phoneCircleCy, phoneCircleRadius, fillPaint);

        drawNavyHandsetIcon(canvas, phoneCircleCx, phoneCircleCy, phoneCircleRadius, navyBlue);

        // Vertical White Divider inside Contact Banner
        dividerPaint.setColor(Color.WHITE);
        dividerPaint.setStrokeWidth(5f);
        float contactDividerX = contactRect.left + 206f;
        float contactDividerInset = Math.min(30f, contactRect.height() * 0.16f);
        canvas.drawLine(
                contactDividerX,
                contactRect.top + contactDividerInset,
                contactDividerX,
                contactRect.bottom - contactDividerInset,
                dividerPaint
        );

        // Fixed Contact Font Size ("যোগাযোগ: " + exact mobile number, no auto-resizing)
        String contactLine = theme.getContactPrefix() + record.getMobile();
        float mobileAreaLeft = contactDividerX + 30f;
        float mobileAreaRight = contactRect.right - 36f;
        float mobileAreaWidth = mobileAreaRight - mobileAreaLeft;

        TextPaint mobilePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mobilePaint.setColor(Color.WHITE);
        mobilePaint.setTypeface(paragraphTypeface);
        mobilePaint.setTextAlign(Paint.Align.CENTER);
        mobilePaint.setTextSize(theme.getFixedContactFontSizePx());
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
        float dy = headerRect.top - 32f;

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

    private static void drawDetailsCardHouseIllustration(
            @NonNull Canvas canvas,
            @NonNull RectF card,
            float dividerX,
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

        float paneWidth = dividerX - card.left;
        float scale = Math.min(
                1.08f,
                Math.min((paneWidth - 36f) / 294f, (card.height() - 44f) / 322f)
        );

        canvas.save();
        canvas.translate(card.left + paneWidth / 2f, card.centerY());
        canvas.scale(scale, scale);
        // Normalize original [x: 64..356 (mid 210), y: 454..772 (mid 613)] around origin
        canvas.translate(-210f, -613f);

        float cardTopRef = 408f;
        float baseBottom = 772f;

        // Curved ground arc under tree and house
        Path ground = new Path();
        ground.moveTo(64f, baseBottom);
        ground.quadTo(208f, baseBottom - 26f, 354f, baseBottom);
        ground.quadTo(208f, baseBottom - 12f, 64f, baseBottom);
        ground.close();
        canvas.drawPath(ground, navy);

        // Tree on the left
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

        // House body coordinates
        float houseLeft = 140f;
        float houseRight = 334f;
        float midWingX = 212f;
        float wingTop = cardTopRef + 114f;   // 522
        float gablePeakX = 274f;
        float gablePeakY = cardTopRef + 46f; // 454
        float eaveY = cardTopRef + 106f;     // 514
        float houseBottom = baseBottom - 10f;

        // Upper-left wing flat cornice + vertical wall outline
        navyStroke.setStrokeWidth(6f);
        canvas.drawLine(houseLeft - 8f, wingTop, midWingX + 4f, wingTop, navyStroke);
        canvas.drawRect(houseLeft, wingTop, midWingX, houseBottom - 4f, navyStroke);

        // Upper-left window frame with inner pane split
        RectF upperLeftWin = new RectF(houseLeft + 14f, wingTop + 18f, midWingX - 10f, wingTop + 72f);
        navyStroke.setStrokeWidth(4f);
        canvas.drawRect(upperLeftWin, navyStroke);
        canvas.drawLine(upperLeftWin.centerX(), upperLeftWin.top, upperLeftWin.centerX(), upperLeftWin.bottom, navyStroke);

        // Middle floor horizontal beam separating 1st & 2nd floor
        float floorSplitY = wingTop + 88f;
        navyStroke.setStrokeWidth(7f);
        canvas.drawLine(houseLeft - 10f, floorSplitY, houseRight + 10f, floorSplitY, navyStroke);

        // Right 2-story gable section walls
        navyStroke.setStrokeWidth(6f);
        canvas.drawLine(midWingX, eaveY - 8f, midWingX, houseBottom - 4f, navyStroke);
        canvas.drawLine(houseRight, eaveY - 8f, houseRight, houseBottom - 4f, navyStroke);

        // Main gable pitched roof + right chimney
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

        // Upper-right 2x2 window inside gable
        float gwSize = 20f;
        float gwGap = 6f;
        float gwLeft = gablePeakX - gwSize - gwGap / 2f;
        float gwTop = gablePeakY + 56f;
        canvas.drawRect(gwLeft, gwTop, gwLeft + gwSize, gwTop + gwSize, navy);
        canvas.drawRect(gwLeft + gwSize + gwGap, gwTop, gwLeft + 2f * gwSize + gwGap, gwTop + gwSize, navy);
        canvas.drawRect(gwLeft, gwTop + gwSize + gwGap, gwLeft + gwSize, gwTop + 2f * gwSize + gwGap, navy);
        canvas.drawRect(gwLeft + gwSize + gwGap, gwTop + gwSize + gwGap, gwLeft + 2f * gwSize + gwGap, gwTop + 2f * gwSize + gwGap, navy);

        // Lower-left outlined square window
        navyStroke.setStrokeWidth(4.5f);
        canvas.drawRect(houseLeft + 14f, floorSplitY + 20f, midWingX - 14f, floorSplitY + 68f, navyStroke);

        // Lower-right solid navy door with two tiny white handle dots
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
