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

import java.util.List;

public final class HousePosterTemplate {

    private static final int NAVY_BLUE = Color.parseColor("#032668");
    private static final String TITLE_TEXT = "ফ্ল্যাট / বাসা ভাড়া হবে";
    private static final String CONTACT_PREFIX = "যোগাযোগ: ";

    private HousePosterTemplate() {
    }

    public static boolean render(
            @Nullable Context context,
            @NonNull Canvas canvas,
            @NonNull PropertyRecord record,
            int width,
            int height
    ) {
        // 1. Pure White Background & Navy Blue Rounded Outer Border
        canvas.drawColor(Color.WHITE);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(NAVY_BLUE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(7f);
        RectF outerRect = new RectF(16f, 16f, width - 16f, height - 16f);
        canvas.drawRoundRect(outerRect, 48f, 48f, borderPaint);

        // 2. Top Roof Silhouette + Symmetric Tapered Wings
        drawTopRoofEmblem(canvas, width);

        Typeface titleTypeface = PosterFontLoader.loadTitleTypeface(context);
        Typeface paragraphTypeface = PosterFontLoader.loadParagraphTypeface(context);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(NAVY_BLUE);

        // 3. Navy Blue Rounded Title Banner ("ফ্ল্যাট / বাসা ভাড়া হবে")
        RectF titleRect = new RectF(36f, 162f, width - 36f, 376f);
        canvas.drawRoundRect(titleRect, 52f, 52f, fillPaint);

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTypeface(titleTypeface);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        PosterTextFitter.fitSingleLineTextSize(
                TITLE_TEXT,
                titlePaint,
                titleRect.width() - 88f,
                124f,
                44f
        );
        Paint.FontMetrics titleFm = titlePaint.getFontMetrics();
        float titleBaseline = titleRect.centerY() - (titleFm.ascent + titleFm.descent) / 2f;
        canvas.drawText(TITLE_TEXT, titleRect.centerX(), titleBaseline, titlePaint);

        // 4. Middle White Details Card with Navy Blue Border
        RectF detailsCard = new RectF(40f, 408f, width - 40f, 810f);
        fillPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(detailsCard, 46f, 46f, fillPaint);

        borderPaint.setStrokeWidth(6f);
        canvas.drawRoundRect(detailsCard, 46f, 46f, borderPaint);

        // Left Two-Story House + Tree Architectural Illustration
        drawDetailsCardHouseIllustration(canvas, detailsCard);

        // Vertical Divider Line
        Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(NAVY_BLUE);
        dividerPaint.setStrokeWidth(5f);
        dividerPaint.setStrokeCap(Paint.Cap.ROUND);
        float dividerX = 374f;
        canvas.drawLine(dividerX, detailsCard.top + 38f, dividerX, detailsCard.bottom - 38f, dividerPaint);

        // Right Dynamic Details Paragraphs (paragraph.ttf, auto-scaled, no quotes/separator commas)
        TextPaint detailsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        detailsPaint.setColor(NAVY_BLUE);
        detailsPaint.setTypeface(paragraphTypeface);

        int boxLeft = 408;
        int boxTop = (int) (detailsCard.top + 26f);
        int boxWidth = (int) (detailsCard.right - 32f - boxLeft);
        int boxHeight = (int) (detailsCard.bottom - 26f - boxTop);

        List<String> paragraphs = DetailsParagraphParser.parse(record.getDetails());
        PosterTextFitter.FitResult fit = PosterTextFitter.fitParagraphs(
                paragraphs,
                detailsPaint,
                boxWidth,
                boxHeight,
                64f,
                18f
        );
        PosterTextFitter.drawFitResult(
                canvas,
                fit,
                boxLeft,
                boxTop,
                boxWidth,
                boxHeight,
                !fit.isOverflowed()
        );

        // 5. Bottom Navy Blue Contact Banner + White Phone Circle + Mobile Number
        RectF contactRect = new RectF(36f, 842f, width - 36f, 1042f);
        fillPaint.setColor(NAVY_BLUE);
        canvas.drawRoundRect(contactRect, 52f, 52f, fillPaint);

        float phoneCircleRadius = 64f;
        float phoneCircleCx = 144f;
        float phoneCircleCy = contactRect.centerY();
        fillPaint.setColor(Color.WHITE);
        canvas.drawCircle(phoneCircleCx, phoneCircleCy, phoneCircleRadius, fillPaint);

        drawNavyHandsetIcon(canvas, phoneCircleCx, phoneCircleCy, phoneCircleRadius);

        // Vertical White Divider inside Contact Banner
        dividerPaint.setColor(Color.WHITE);
        dividerPaint.setStrokeWidth(5f);
        float contactDividerX = 242f;
        canvas.drawLine(
                contactDividerX,
                contactRect.top + 30f,
                contactDividerX,
                contactRect.bottom - 30f,
                dividerPaint
        );

        // Contact Text & Exact Mobile Number (paragraph.ttf, auto-scaled, digits preserved verbatim)
        String contactLine = CONTACT_PREFIX + record.getMobile();
        float mobileAreaLeft = 272f;
        float mobileAreaRight = contactRect.right - 40f;
        float mobileAreaWidth = mobileAreaRight - mobileAreaLeft;

        TextPaint mobilePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mobilePaint.setColor(Color.WHITE);
        mobilePaint.setTypeface(paragraphTypeface);
        mobilePaint.setTextAlign(Paint.Align.CENTER);
        PosterTextFitter.fitSingleLineTextSize(
                contactLine,
                mobilePaint,
                mobileAreaWidth,
                92f,
                22f
        );
        Paint.FontMetrics mobileFm = mobilePaint.getFontMetrics();
        float mobileBaseline = contactRect.centerY() - (mobileFm.ascent + mobileFm.descent) / 2f;
        canvas.drawText(
                contactLine,
                mobileAreaLeft + mobileAreaWidth / 2f,
                mobileBaseline,
                mobilePaint
        );

        return fit.isOverflowed();
    }

    private static void drawTopRoofEmblem(@NonNull Canvas canvas, int width) {
        Paint navyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        navyPaint.setColor(NAVY_BLUE);
        navyPaint.setStyle(Paint.Style.FILL);

        float cx = width / 2f;

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

        // Left angled roof accent beam (signature detail from reference silhouette)
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
    }

    private static void drawDetailsCardHouseIllustration(@NonNull Canvas canvas, @NonNull RectF card) {
        Paint navy = new Paint(Paint.ANTI_ALIAS_FLAG);
        navy.setColor(NAVY_BLUE);
        navy.setStyle(Paint.Style.FILL);

        Paint navyStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        navyStroke.setColor(NAVY_BLUE);
        navyStroke.setStyle(Paint.Style.STROKE);
        navyStroke.setStrokeCap(Paint.Cap.ROUND);
        navyStroke.setStrokeJoin(Paint.Join.ROUND);

        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);
        white.setStyle(Paint.Style.FILL);

        float baseBottom = card.bottom - 38f; // ~772

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
        // Subtle white fork highlight on trunk
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
        float wingTop = card.top + 114f;   // 522
        float gablePeakX = 274f;
        float gablePeakY = card.top + 46f; // 454
        float eaveY = card.top + 106f;     // 514
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
    }

    private static void drawNavyHandsetIcon(@NonNull Canvas canvas, float cx, float cy, float circleRadius) {
        Paint phonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        phonePaint.setColor(NAVY_BLUE);
        phonePaint.setStyle(Paint.Style.FILL);

        // Classic tilted telephone receiver silhouette centered inside the white circle
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
