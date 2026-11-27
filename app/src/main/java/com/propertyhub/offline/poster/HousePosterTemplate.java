package com.propertyhub.offline.poster;

import android.content.Context;
import android.graphics.Bitmap;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference Image 1 Exact House for Rent Poster Template (Dynamic Auto Height).
 * Navy Blue + White design featuring:
 * - Outer navy rounded border
 * - Top roof silhouette + 2x2 windows + tapered horizontal lines
 * - Large navy rounded title banner with title font
 * - Single large central rounded panel with left house illustration, vertical separator, and right text
 * - Bottom navy contact banner with white phone circle, separator, and contact font
 */
public final class HousePosterTemplate {

    private static final int HEADER_BITMAP_HEIGHT = 148;
    private static final Object CACHE_LOCK = new Object();
    private static final Map<String, Bitmap> ASSET_CACHE = new LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > 6;
        }
    };

    private static final Path HANDSET_PATH = createHandsetPath();

    private HousePosterTemplate() {
    }

    private static Path createHandsetPath() {
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
        return handset;
    }

    @NonNull
    public static DynamicPosterLayoutEngine.HouseDynamicPlan measurePlan(
            @Nullable Context context,
            @NonNull PropertyRecord record,
            int width
    ) {
        PosterThemeConfig theme = PosterThemeConfig.houseForRent();
        Typeface titleTypeface = PosterFontLoader.loadTitleTypeface(context);
        Typeface paragraphTypeface = PosterFontLoader.loadParagraphTypeface(context);
        List<PropertyFieldValue> visibleFields = record.getPosterVisibleFieldValues();

        return DynamicPosterLayoutEngine.solveHouseDynamicLayout(
                theme,
                record.getTitle(),
                visibleFields,
                titleTypeface,
                paragraphTypeface,
                width
        );
    }

    public static boolean render(
            @Nullable Context context,
            @NonNull Canvas canvas,
            @NonNull PropertyRecord record,
            @NonNull DynamicPosterLayoutEngine.HouseDynamicPlan plan
    ) {
        PosterThemeConfig theme = PosterThemeConfig.houseForRent();
        int navyBlue = theme.getPrimaryColor();
        Typeface contactTypeface = PosterFontLoader.loadContactTypeface(context);

        // 1. Crisp White Background & Navy Blue Rounded Outer Border
        canvas.drawColor(theme.getCanvasBackgroundColor());

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(navyBlue);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(7f);
        canvas.drawRoundRect(
                plan.outerRect,
                theme.getOuterBorderRadius(),
                theme.getOuterBorderRadius(),
                borderPaint
        );

        // 2. Top Roof Silhouette + Tapered Horizontal Lines (cached bitmap)
        Bitmap headerBitmap = getOrCreateTopRoofBitmap(plan.width, navyBlue);
        canvas.drawBitmap(headerBitmap, 0f, plan.headerTop, null);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        // 3. Navy Blue Rounded Title Banner
        fillPaint.setColor(navyBlue);
        canvas.drawRoundRect(
                plan.titleRect,
                theme.getTitleCornerRadius(),
                theme.getTitleCornerRadius(),
                fillPaint
        );

        StaticLayout titleLayout = plan.titleLayout;
        canvas.save();
        float titleTextLeft = plan.titleRect.centerX() - titleLayout.getWidth() / 2f;
        float titleTextTop = plan.titleRect.centerY() - titleLayout.getHeight() / 2f;
        canvas.translate(titleTextLeft, titleTextTop);
        titleLayout.draw(canvas);
        canvas.restore();

        // 4. Single Large Central Rounded Panel (No sub-cards)
        fillPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(
                plan.panelRect,
                theme.getDetailsCornerRadius(),
                theme.getDetailsCornerRadius(),
                fillPaint
        );
        borderPaint.setColor(navyBlue);
        borderPaint.setStrokeWidth(5.5f);
        canvas.drawRoundRect(
                plan.panelRect,
                theme.getDetailsCornerRadius(),
                theme.getDetailsCornerRadius(),
                borderPaint
        );

        Bitmap houseIllust = getOrCreateHouseGraphicBitmap(plan.illustMaxW, plan.illustMaxH, navyBlue);
        canvas.drawBitmap(
                houseIllust,
                plan.illustCenterX - houseIllust.getWidth() / 2f,
                plan.illustCenterY - houseIllust.getHeight() / 2f,
                null
        );

        if (plan.hasFields) {
            Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dividerPaint.setColor(navyBlue);
            dividerPaint.setStrokeWidth(4.5f);
            dividerPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(
                    plan.dividerX,
                    plan.panelRect.top + 26f,
                    plan.dividerX,
                    plan.panelRect.bottom - 26f,
                    dividerPaint
            );

            canvas.save();
            canvas.clipRect(
                    plan.dividerX + 6f,
                    plan.panelRect.top + 14f,
                    plan.panelRect.right - 14f,
                    plan.panelRect.bottom - 14f
            );
            for (DynamicPosterLayoutEngine.TextBlockPlacement block : plan.textBlocks) {
                canvas.save();
                canvas.translate(block.x, block.y);
                block.layout.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
        }

        // 5. Bottom Navy Blue Contact Banner
        RectF contactRect = plan.contactRect;
        fillPaint.setColor(navyBlue);
        canvas.drawRoundRect(
                contactRect,
                theme.getContactCornerRadius(),
                theme.getContactCornerRadius(),
                fillPaint
        );

        float phoneCircleRadius = 52f;
        float phoneCircleCx = contactRect.left + 104f;
        float phoneCircleCy = contactRect.centerY();
        fillPaint.setColor(Color.WHITE);
        canvas.drawCircle(phoneCircleCx, phoneCircleCy, phoneCircleRadius, fillPaint);

        drawNavyHandsetIcon(canvas, phoneCircleCx, phoneCircleCy, phoneCircleRadius, navyBlue);

        Paint contactDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        contactDividerPaint.setColor(Color.WHITE);
        contactDividerPaint.setStrokeWidth(4.5f);
        contactDividerPaint.setStrokeCap(Paint.Cap.ROUND);
        float contactDividerX = contactRect.left + 194f;
        canvas.drawLine(
                contactDividerX,
                contactRect.top + 26f,
                contactDividerX,
                contactRect.bottom - 26f,
                contactDividerPaint
        );

        String contactLine = theme.getContactPrefix() + record.getMobile().trim();
        float mobileAreaLeft = contactDividerX + 28f;
        float mobileAreaRight = contactRect.right - 36f;
        float mobileAreaWidth = mobileAreaRight - mobileAreaLeft;

        TextPaint mobilePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mobilePaint.setColor(Color.WHITE);
        Typeface contactStyled = PosterFontLoader.getStyledVariant(contactTypeface, true, false);
        mobilePaint.setTypeface(contactStyled);
        mobilePaint.setFakeBoldText(!contactStyled.isBold());
        mobilePaint.setTextAlign(Paint.Align.CENTER);
        float fittedContactSize = PosterTextFitter.fitSingleLineTextSize(
                contactLine,
                mobilePaint,
                mobileAreaWidth - 12f,
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

        return plan.overflowed;
    }

    @NonNull
    private static Bitmap getOrCreateTopRoofBitmap(int width, int navyBlue) {
        String key = "house_header_" + width + "_" + navyBlue;
        synchronized (CACHE_LOCK) {
            Bitmap cached = ASSET_CACHE.get(key);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
            Bitmap bmp = Bitmap.createBitmap(width, HEADER_BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            drawTopRoofEmblem(c, width, 0f, navyBlue);
            ASSET_CACHE.put(key, bmp);
            return bmp;
        }
    }

    @NonNull
    private static Bitmap getOrCreateHouseGraphicBitmap(float maxW, float maxH, int navyBlue) {
        int qW = Math.max(64, Math.round(maxW));
        int qH = Math.max(64, Math.round(maxH));
        String key = "house_illust_" + qW + "x" + qH + "_" + navyBlue;
        synchronized (CACHE_LOCK) {
            Bitmap cached = ASSET_CACHE.get(key);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
            Bitmap bmp = Bitmap.createBitmap(qW, qH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            drawHouseArchitecturalGraphic(c, qW / 2f, qH / 2f, qW, qH, navyBlue);
            ASSET_CACHE.put(key, bmp);
            return bmp;
        }
    }

    private static void drawTopRoofEmblem(
            @NonNull Canvas canvas,
            int width,
            float topY,
            int navyBlue
    ) {
        Paint navyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        navyPaint.setColor(navyBlue);
        navyPaint.setStyle(Paint.Style.FILL);

        float cx = width / 2f;
        canvas.save();
        canvas.translate(0f, topY);

        Path leftWing = new Path();
        leftWing.moveTo(96f, 98f);
        leftWing.lineTo(426f, 91f);
        leftWing.arcTo(new RectF(419f, 91f, 433f, 105f), -90f, 180f, false);
        leftWing.lineTo(96f, 98f);
        leftWing.close();
        canvas.drawPath(leftWing, navyPaint);

        Path rightWing = new Path();
        rightWing.moveTo(width - 96f, 98f);
        rightWing.lineTo(width - 426f, 91f);
        rightWing.arcTo(new RectF(width - 433f, 91f, width - 419f, 105f), -90f, -180f, false);
        rightWing.lineTo(width - 96f, 98f);
        rightWing.close();
        canvas.drawPath(rightWing, navyPaint);

        Path leftBeam = new Path();
        leftBeam.moveTo(436f, 134f);
        leftBeam.lineTo(618f, 36f);
        leftBeam.lineTo(632f, 44f);
        leftBeam.lineTo(466f, 134f);
        leftBeam.close();
        canvas.drawPath(leftBeam, navyPaint);

        Path mainRoof = new Path();
        mainRoof.moveTo(480f, 134f);
        mainRoof.lineTo(720f, 16f);
        mainRoof.lineTo(824f, 68f);
        mainRoof.lineTo(824f, 38f);
        mainRoof.lineTo(862f, 38f);
        mainRoof.lineTo(862f, 87f);
        mainRoof.lineTo(962f, 134f);
        mainRoof.lineTo(890f, 134f);
        mainRoof.lineTo(720f, 48f);
        mainRoof.lineTo(548f, 134f);
        mainRoof.close();
        canvas.drawPath(mainRoof, navyPaint);

        float winSize = 18f;
        float gap = 6f;
        float startX = cx - winSize - gap / 2f;
        float startY = 82f;
        canvas.drawRect(startX, startY, startX + winSize, startY + winSize, navyPaint);
        canvas.drawRect(startX + winSize + gap, startY, startX + 2f * winSize + gap, startY + winSize, navyPaint);
        canvas.drawRect(startX, startY + winSize + gap, startX + winSize, startY + 2f * winSize + gap, navyPaint);
        canvas.drawRect(startX + winSize + gap, startY + winSize + gap, startX + 2f * winSize + gap, startY + 2f * winSize + gap, navyPaint);

        canvas.restore();
    }

    private static void drawHouseArchitecturalGraphic(
            @NonNull Canvas canvas,
            float centerX,
            float centerY,
            float maxW,
            float maxH,
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

        float scale = Math.min(0.95f, Math.min((maxW - 24f) / 310f, (maxH - 20f) / 330f));
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(scale, scale);
        canvas.translate(-210f, -613f);

        Path ground = new Path();
        ground.moveTo(58f, 772f);
        ground.quadTo(208f, 742f, 362f, 772f);
        ground.quadTo(208f, 756f, 58f, 772f);
        ground.close();
        canvas.drawPath(ground, navy);

        canvas.drawRect(92f, 706f, 104f, 764f, navy);
        canvas.drawCircle(98f, 672f, 31f, navy);
        canvas.drawCircle(80f, 692f, 21f, navy);
        canvas.drawCircle(116f, 692f, 21f, navy);

        Paint whiteLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteLine.setColor(Color.WHITE);
        whiteLine.setStrokeWidth(3f);
        whiteLine.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(98f, 706f, 88f, 690f, whiteLine);
        canvas.drawLine(98f, 698f, 108f, 684f, whiteLine);

        navyStroke.setStrokeWidth(6.5f);
        canvas.drawRect(140f, 522f, 212f, 758f, navyStroke);
        canvas.drawLine(132f, 522f, 214f, 522f, navyStroke);
        canvas.drawLine(212f, 504f, 212f, 758f, navyStroke);
        canvas.drawLine(334f, 504f, 334f, 758f, navyStroke);
        canvas.drawLine(130f, 610f, 344f, 610f, navyStroke);

        Path roof = new Path();
        roof.moveTo(192f, 514f);
        roof.lineTo(274f, 452f);
        roof.lineTo(312f, 476f);
        roof.lineTo(312f, 456f);
        roof.lineTo(332f, 456f);
        roof.lineTo(332f, 488f);
        roof.lineTo(358f, 514f);
        roof.lineTo(340f, 526f);
        roof.lineTo(274f, 470f);
        roof.lineTo(206f, 526f);
        roof.close();
        canvas.drawPath(roof, navy);

        canvas.drawRect(251f, 508f, 271f, 528f, navy);
        canvas.drawRect(277f, 508f, 297f, 528f, navy);
        canvas.drawRect(251f, 534f, 271f, 554f, navy);
        canvas.drawRect(277f, 534f, 297f, 554f, navy);

        navyStroke.setStrokeWidth(4.5f);
        canvas.drawRect(154f, 538f, 200f, 592f, navyStroke);
        canvas.drawLine(177f, 538f, 177f, 592f, navyStroke);
        canvas.drawRect(154f, 628f, 198f, 676f, navyStroke);

        canvas.drawRect(252f, 628f, 314f, 758f, navy);
        canvas.drawCircle(274f, 694f, 3.4f, white);
        canvas.drawCircle(292f, 694f, 3.4f, white);

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

        float s = circleRadius / 62f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(s, s);
        canvas.drawPath(HANDSET_PATH, phonePaint);
        canvas.restore();
    }
}
