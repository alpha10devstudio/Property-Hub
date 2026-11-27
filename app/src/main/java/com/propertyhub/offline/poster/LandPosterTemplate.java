package com.propertyhub.offline.poster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyFieldValue;
import com.propertyhub.offline.model.PropertyRecord;

import java.util.List;

/**
 * Reference Image 2 Exact Land for Sale Poster Template (Dynamic Auto Height).
 * Dark Green + White design featuring:
 * - Single clean green rounded outer border
 * - Top location pin over curved contour arcs + 2 flanking trees + tapered horizontal bars
 * - Large dark-green rounded title banner with white title.ttf text
 * - Central green-bordered panel (left lush green pasture illustration + vertical divider + right text)
 * - Optional separate green-bordered Dag | Khatian pill row below
 * - Optional Additional Information text cleanly below Dag/Khatian row
 * - Bottom dark-green contact banner with white phone badge, separator, and contact number
 */
public final class LandPosterTemplate {

    private LandPosterTemplate() {
    }

    @NonNull
    public static DynamicPosterLayoutEngine.LandDynamicPlan measurePlan(
            @Nullable Context context,
            @NonNull PropertyRecord record,
            int width
    ) {
        PosterThemeConfig theme = PosterThemeConfig.landForSale();
        Typeface titleTypeface = PosterFontLoader.loadTitleTypeface(context);
        Typeface paragraphTypeface = PosterFontLoader.loadParagraphTypeface(context);
        List<PropertyFieldValue> visibleFields = record.getPosterVisibleFieldValues();

        return DynamicPosterLayoutEngine.solveLandDynamicLayout(
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
            @NonNull DynamicPosterLayoutEngine.LandDynamicPlan plan
    ) {
        PosterThemeConfig theme = PosterThemeConfig.landForSale();
        int green = theme.getPrimaryColor();
        Typeface titleTypeface = PosterFontLoader.loadTitleTypeface(context);

        // 1. Crisp White Canvas & Green Rounded Outer Border
        canvas.drawColor(theme.getCanvasBackgroundColor());

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(green);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(7f);
        canvas.drawRoundRect(
                plan.outerRect,
                theme.getOuterBorderRadius(),
                theme.getOuterBorderRadius(),
                borderPaint
        );

        // 2. Top Location Pin + Curved Contours + Flanking Trees + Tapered Bars
        drawTopLandHeaderEmblem(canvas, plan.width, plan.headerTop, green);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        // 3. Dark Green Title Banner
        fillPaint.setColor(green);
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

        // 4. Main Central Green-Bordered Panel
        fillPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(
                plan.panelRect,
                theme.getDetailsCornerRadius(),
                theme.getDetailsCornerRadius(),
                fillPaint
        );
        borderPaint.setColor(green);
        borderPaint.setStrokeWidth(5f);
        canvas.drawRoundRect(
                plan.panelRect,
                theme.getDetailsCornerRadius(),
                theme.getDetailsCornerRadius(),
                borderPaint
        );

        drawLandPastureScene(canvas, plan.illustRect, green);

        if (plan.hasMainFields) {
            Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dividerPaint.setColor(green);
            dividerPaint.setStrokeWidth(4.5f);
            dividerPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(
                    plan.dividerX,
                    plan.panelRect.top + 20f,
                    plan.dividerX,
                    plan.panelRect.bottom - 20f,
                    dividerPaint
            );

            canvas.save();
            canvas.clipRect(
                    plan.dividerX + 6f,
                    plan.panelRect.top + 12f,
                    plan.panelRect.right - 12f,
                    plan.panelRect.bottom - 12f
            );
            for (DynamicPosterLayoutEngine.TextBlockPlacement block : plan.mainBlocks) {
                canvas.save();
                canvas.translate(block.x, block.y);
                block.layout.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
        }

        // 5. Optional Separate Rounded Green-Bordered Dag & Khatian Row
        if (plan.hasDagKhatianRow) {
            RectF dkRect = plan.dagKhatianRect;
            fillPaint.setColor(Color.WHITE);
            canvas.drawRoundRect(dkRect, 28f, 28f, fillPaint);

            borderPaint.setColor(green);
            borderPaint.setStrokeWidth(4.5f);
            canvas.drawRoundRect(dkRect, 28f, 28f, borderPaint);

            canvas.save();
            canvas.clipRect(
                    plan.dagKhatianRect.left + 8f,
                    plan.dagKhatianRect.top + 6f,
                    plan.dagKhatianRect.right - 8f,
                    plan.dagKhatianRect.bottom - 6f
            );
            if (plan.hasDag && plan.hasKhatian) {
                Paint midDiv = new Paint(Paint.ANTI_ALIAS_FLAG);
                midDiv.setColor(green);
                midDiv.setStrokeWidth(4f);
                canvas.drawLine(
                        plan.width / 2f,
                        dkRect.top + 14f,
                        plan.width / 2f,
                        dkRect.bottom - 14f,
                        midDiv
                );

                if (plan.dagLayout != null) {
                    canvas.save();
                    float leftSlotCenter = (dkRect.left + 16f + plan.width / 2f - 8f) / 2f;
                    canvas.translate(
                            leftSlotCenter - plan.dagLayout.getWidth() / 2f,
                            dkRect.centerY() - plan.dagLayout.getHeight() / 2f
                    );
                    plan.dagLayout.draw(canvas);
                    canvas.restore();
                }

                if (plan.khatianLayout != null) {
                    canvas.save();
                    float rightSlotCenter = (plan.width / 2f + 8f + dkRect.right - 16f) / 2f;
                    canvas.translate(
                            rightSlotCenter - plan.khatianLayout.getWidth() / 2f,
                            dkRect.centerY() - plan.khatianLayout.getHeight() / 2f
                    );
                    plan.khatianLayout.draw(canvas);
                    canvas.restore();
                }
            } else {
                StaticLayout singleLayout = plan.hasDag ? plan.dagLayout : plan.khatianLayout;
                if (singleLayout != null) {
                    canvas.save();
                    canvas.translate(
                            dkRect.centerX() - singleLayout.getWidth() / 2f,
                            dkRect.centerY() - singleLayout.getHeight() / 2f
                    );
                    singleLayout.draw(canvas);
                    canvas.restore();
                }
            }
            canvas.restore();
        }

        // 6. Optional Additional Info Text Below Dag/Khatian Row (Directly on White Canvas)
        if (plan.hasAdditional && plan.additionalLayout != null) {
            float clipBottom = Math.max(plan.additionalTopY, plan.contactRect.top - 8f);
            canvas.save();
            canvas.clipRect(
                    plan.panelRect.left,
                    plan.additionalTopY - 6f,
                    plan.panelRect.right,
                    clipBottom
            );
            float tx = plan.additionalLayout.getLineCount() <= 2
                    ? (plan.width / 2f - plan.additionalLayout.getWidth() / 2f)
                    : plan.additionalLeftX;
            canvas.translate(tx, plan.additionalTopY);
            plan.additionalLayout.draw(canvas);
            canvas.restore();
        }

        // 7. Bottom Dark Green Contact Banner
        RectF contactRect = plan.contactRect;
        fillPaint.setColor(green);
        canvas.drawRoundRect(
                contactRect,
                theme.getContactCornerRadius(),
                theme.getContactCornerRadius(),
                fillPaint
        );

        float phoneR = 52f;
        float phoneCx = contactRect.left + 104f;
        float phoneCy = contactRect.centerY();
        fillPaint.setColor(Color.WHITE);
        canvas.drawCircle(phoneCx, phoneCy, phoneR, fillPaint);

        drawGreenHandsetIcon(canvas, phoneCx, phoneCy, phoneR, green);

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
        mobilePaint.setTypeface(Typeface.create(titleTypeface, Typeface.BOLD));
        mobilePaint.setFakeBoldText(true);
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

    private static void drawTopLandHeaderEmblem(
            @NonNull Canvas canvas,
            int width,
            float topY,
            int green
    ) {
        float cx = width / 2f;
        canvas.save();
        canvas.translate(0f, topY);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(green);

        Path leftBar = new Path();
        leftBar.moveTo(80f, 74f);
        leftBar.lineTo(448f, 66f);
        leftBar.arcTo(new RectF(440f, 66f, 456f, 82f), -90f, 180f, false);
        leftBar.lineTo(80f, 74f);
        leftBar.close();
        canvas.drawPath(leftBar, fill);

        Path rightBar = new Path();
        rightBar.moveTo(width - 80f, 74f);
        rightBar.lineTo(width - 448f, 66f);
        rightBar.arcTo(new RectF(width - 456f, 66f, width - 440f, 82f), -90f, -180f, false);
        rightBar.lineTo(width - 80f, 74f);
        rightBar.close();
        canvas.drawPath(rightBar, fill);

        Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setColor(green);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        float baseLineY = 124f;
        float[][] arcs = {
                {280f, 52f, 4.5f},
                {215f, 42f, 5f},
                {155f, 32f, 5.5f},
                {96f, 20f, 5f}
        };
        for (float[] a : arcs) {
            arcPaint.setStrokeWidth(a[2]);
            Path p = new Path();
            p.moveTo(cx - a[0], baseLineY);
            p.quadTo(cx, baseLineY - a[1] * 2f, cx + a[0], baseLineY);
            canvas.drawPath(p, arcPaint);
        }

        drawMiniHeaderTree(canvas, cx - 182f, baseLineY - 8f, 1.0f, green);
        drawMiniHeaderTree(canvas, cx + 182f, baseLineY - 12f, 0.92f, green);

        float pinCy = 36f;
        float pinR = 32f;

        Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.FILL);
        Path halo = new Path();
        float hr = pinR + 4f;
        halo.addCircle(cx, pinCy, hr, Path.Direction.CW);
        halo.moveTo(cx - hr * 0.85f, pinCy + hr * 0.45f);
        halo.lineTo(cx, baseLineY - 2f);
        halo.lineTo(cx + hr * 0.85f, pinCy + hr * 0.45f);
        halo.close();
        canvas.drawPath(halo, whitePaint);

        Path pin = new Path();
        pin.addCircle(cx, pinCy, pinR, Path.Direction.CW);
        pin.moveTo(cx - pinR * 0.85f, pinCy + pinR * 0.44f);
        pin.lineTo(cx, baseLineY - 6f);
        pin.lineTo(cx + pinR * 0.85f, pinCy + pinR * 0.44f);
        pin.close();
        canvas.drawPath(pin, fill);

        canvas.drawCircle(cx, pinCy, 15f, whitePaint);
        canvas.drawCircle(cx, pinCy, 7.5f, fill);

        canvas.restore();
    }

    private static void drawMiniHeaderTree(
            @NonNull Canvas canvas,
            float tx,
            float ty,
            float scale,
            int green
    ) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(green);
        p.setStyle(Paint.Style.FILL);

        canvas.save();
        canvas.translate(tx, ty);
        canvas.scale(scale, scale);
        canvas.drawRect(-4f, -34f, 4f, 0f, p);
        canvas.drawCircle(0f, -56f, 20f, p);
        canvas.drawCircle(-14f, -44f, 14f, p);
        canvas.drawCircle(14f, -44f, 14f, p);
        canvas.drawCircle(-6f, -68f, 13f, p);
        canvas.drawCircle(8f, -66f, 12f, p);
        canvas.restore();
    }

    private static void drawLandPastureScene(
            @NonNull Canvas canvas,
            @NonNull RectF box,
            int darkGreen
    ) {
        float left = box.left;
        float top = box.top;
        float width = box.width();
        float height = box.height();
        float horizonY = top + height * 0.37f;

        canvas.save();
        Path clip = new Path();
        clip.addRoundRect(box, 10f, 10f, Path.Direction.CW);
        canvas.clipPath(clip);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);

        // Sky gradient
        fill.setShader(new LinearGradient(
                0f, top, 0f, horizonY,
                Color.parseColor("#D7E8F7"),
                Color.parseColor("#F5F9FD"),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(left, top, box.right, horizonY + 6f, fill);
        fill.setShader(null);

        // Clouds
        fill.setColor(Color.argb(210, 255, 255, 255));
        float[][] clouds = {
                {0.14f, 0.22f, 28f},
                {0.20f, 0.25f, 22f},
                {0.48f, 0.16f, 30f},
                {0.56f, 0.20f, 24f},
                {0.72f, 0.18f, 26f},
                {0.86f, 0.23f, 24f}
        };
        for (float[] c : clouds) {
            canvas.drawCircle(left + width * c[0], top + height * c[1], c[2], fill);
        }

        // Distant treeline silhouette
        fill.setColor(Color.parseColor("#2F8F3B"));
        Path treeline = new Path();
        treeline.moveTo(left, horizonY + 4f);
        for (int i = 0; i <= 14; i++) {
            float px = left + (width * i) / 14f;
            float py = horizonY - 12f - (i % 3) * 8f - (i % 2) * 5f;
            treeline.lineTo(px, py);
        }
        treeline.lineTo(box.right, horizonY + 4f);
        treeline.close();
        canvas.drawPath(treeline, fill);

        drawSceneryTree(canvas, left + width * 0.28f, horizonY + 2f, 1.02f);
        drawSceneryTree(canvas, left + width * 0.08f, horizonY + 3f, 0.58f);
        drawSceneryTree(canvas, left + width * 0.72f, horizonY + 1f, 0.42f);
        drawSceneryTree(canvas, left + width * 0.91f, horizonY + 2f, 0.74f);

        fill.setColor(Color.parseColor("#187526"));
        for (int i = 0; i < 16; i++) {
            float bx = left + (width * (i + 0.5f)) / 16f;
            canvas.drawCircle(bx, horizonY + 2f, 12f + (i % 3) * 4f, fill);
        }

        // Perspective grass meadow
        fill.setShader(new LinearGradient(
                0f, horizonY, 0f, box.bottom,
                Color.parseColor("#5CD43C"),
                Color.parseColor("#2B9D1E"),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(left, horizonY, box.right, box.bottom, fill);
        fill.setShader(null);

        float fieldH = box.bottom - horizonY;
        float[] stripeFracs = {0.06f, 0.14f, 0.24f, 0.36f, 0.50f, 0.67f, 0.86f};
        for (int i = 0; i < stripeFracs.length; i++) {
            float y1 = horizonY + fieldH * stripeFracs[i];
            float next = i + 1 < stripeFracs.length ? stripeFracs[i + 1] : 1.0f;
            float y2 = horizonY + fieldH * next;
            fill.setColor(i % 2 == 0 ? Color.argb(96, 130, 235, 84) : Color.argb(50, 20, 128, 22));
            canvas.drawRect(left, y1, box.right, y2, fill);
        }

        Paint furrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        furrowPaint.setColor(Color.argb(56, 25, 130, 22));
        furrowPaint.setStrokeWidth(2f);
        float vanishX = left + width * 0.5f;
        for (int k = -4; k <= 4; k++) {
            canvas.drawLine(vanishX + k * 14f, horizonY, vanishX + k * 56f, box.bottom, furrowPaint);
        }

        // White wooden perspective fences
        drawPerspectiveFence(canvas, box, horizonY, true);
        drawPerspectiveFence(canvas, box, horizonY, false);

        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        outline.setColor(darkGreen);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(2f);
        canvas.drawRoundRect(
                new RectF(left + 1f, top + 1f, box.right - 1f, box.bottom - 1f),
                9f, 9f, outline
        );

        canvas.restore();
    }

    private static void drawSceneryTree(@NonNull Canvas canvas, float tx, float baseY, float scale) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.translate(tx, baseY);
        canvas.scale(scale, scale);

        p.setColor(Color.parseColor("#27591E"));
        canvas.drawRect(-6f, -34f, 6f, 2f, p);

        p.setColor(Color.parseColor("#0D5E1D"));
        canvas.drawCircle(0f, -64f, 34f, p);
        canvas.drawCircle(-26f, -48f, 24f, p);
        canvas.drawCircle(26f, -48f, 24f, p);

        p.setColor(Color.parseColor("#229932"));
        canvas.drawCircle(-8f, -72f, 24f, p);
        canvas.drawCircle(16f, -64f, 20f, p);
        canvas.drawCircle(-22f, -52f, 17f, p);
        canvas.drawCircle(20f, -48f, 16f, p);

        p.setColor(Color.parseColor("#5FD148"));
        canvas.drawCircle(-10f, -78f, 13f, p);
        canvas.drawCircle(10f, -70f, 10f, p);

        canvas.restore();
    }

    private static void drawPerspectiveFence(
            @NonNull Canvas canvas,
            @NonNull RectF box,
            float horizonY,
            boolean isLeft
    ) {
        float startX = isLeft ? box.left - 8f : box.right + 8f;
        float endX = isLeft ? (box.left + box.width() * 0.42f) : (box.left + box.width() * 0.58f);
        float startBottomY = box.top + box.height() * 0.86f;
        float endBottomY = horizonY + 6f;
        float startH = box.height() * 0.32f;
        float endH = box.height() * 0.06f;

        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(72, 8, 70, 18));
        shadowPaint.setStrokeWidth(6f);
        canvas.drawLine(startX, startBottomY + 2f, endX, endBottomY + 1f, shadowPaint);

        Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        railPaint.setColor(Color.WHITE);
        railPaint.setStrokeWidth(5f);

        Paint railEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        railEdge.setColor(Color.argb(64, 16, 95, 32));
        railEdge.setStrokeWidth(1.5f);

        for (float hRatio : new float[]{0.38f, 0.78f}) {
            canvas.drawLine(startX, startBottomY - startH * hRatio, endX, endBottomY - endH * hRatio, railPaint);
            canvas.drawLine(startX, startBottomY - startH * hRatio + 3f, endX, endBottomY - endH * hRatio + 1.5f, railEdge);
        }

        Paint postFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        postFill.setColor(Color.WHITE);
        postFill.setStyle(Paint.Style.FILL);

        Paint postStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        postStroke.setColor(Color.argb(72, 16, 95, 32));
        postStroke.setStyle(Paint.Style.STROKE);
        postStroke.setStrokeWidth(1f);

        for (float t : new float[]{0.95f, 0.84f, 0.70f, 0.53f, 0.34f, 0.14f}) {
            float px = startX + (endX - startX) * t;
            float pyBottom = startBottomY + (endBottomY - startBottomY) * t;
            float postH = startH + (endH - startH) * t;
            float postW = Math.max(3f, 10f * (1f - t * 0.7f));
            RectF postRect = new RectF(px - postW / 2f, pyBottom - postH, px + postW / 2f, pyBottom);
            canvas.drawRect(postRect, postFill);
            canvas.drawRect(postRect, postStroke);
        }
    }

    private static void drawGreenHandsetIcon(
            @NonNull Canvas canvas,
            float cx,
            float cy,
            float circleRadius,
            int green
    ) {
        Paint phonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        phonePaint.setColor(green);
        phonePaint.setStyle(Paint.Style.FILL);

        float s = circleRadius / 62f;
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
