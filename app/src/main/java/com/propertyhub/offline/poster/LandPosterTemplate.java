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
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyRecord;

public final class LandPosterTemplate {

    private LandPosterTemplate() {
    }

    public static boolean render(
            @Nullable Context context,
            @NonNull Canvas canvas,
            @NonNull PropertyRecord record,
            int width,
            int height
    ) {
        Typeface titleTypeface = PosterFontLoader.loadTitleTypeface(context);
        Typeface paragraphTypeface = PosterFontLoader.loadParagraphTypeface(context);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 1. Clean Light Mint/Ivory Canvas Base (Asymmetric Editorial Split)
        canvas.drawColor(Color.parseColor("#F4FAF6"));

        // 2. Left Deep Forest-to-Emerald Column (460px wide)
        float sidebarWidth = 468f;
        paint.setShader(new LinearGradient(
                0f, 0f, sidebarWidth, height,
                Color.parseColor("#04331A"),
                Color.parseColor("#0B7A3B"),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, sidebarWidth, height, paint);
        paint.setShader(null);

        // Topographic contour arcs on green sidebar (native Canvas curves)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(Color.argb(32, 167, 243, 208));
        canvas.drawCircle(60f, height * 0.54f, 220f, paint);
        canvas.drawCircle(60f, height * 0.54f, 300f, paint);
        canvas.drawCircle(60f, height * 0.54f, 380f, paint);
        paint.setStyle(Paint.Style.FILL);

        // 3. Left Sidebar Header & Land Map-Pin Emblem
        drawLocationPinEmblem(canvas, 56f, 64f, 92f);

        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(Color.parseColor("#A7F3D0"));
        brandPaint.setTypeface(titleTypeface);
        brandPaint.setLetterSpacing(0.14f);
        brandPaint.setTextSize(22f);
        canvas.drawText("PROPERTY HUB", 168f, 102f, brandPaint);

        TextPaint tagPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tagPaint.setColor(Color.parseColor("#D1FAE5"));
        tagPaint.setTypeface(paragraphTypeface);
        tagPaint.setTextSize(18f);
        canvas.drawText("Collect • Generate • Share", 168f, 134f, tagPaint);

        // Main Headline stacked in sidebar
        TextPaint headlinePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        headlinePaint.setColor(Color.WHITE);
        headlinePaint.setTypeface(titleTypeface);
        headlinePaint.setTextSize(68f);
        canvas.drawText("LAND", 56f, 304f, headlinePaint);
        canvas.drawText("FOR SALE", 56f, 382f, headlinePaint);

        // Emerald accent bar & supporting heading
        paint.setColor(Color.parseColor("#34D399"));
        canvas.drawRoundRect(new RectF(56f, 412f, 186f, 420f), 4f, 4f, paint);

        TextPaint subHeading = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subHeading.setColor(Color.parseColor("#ECFDF5"));
        subHeading.setTypeface(titleTypeface);
        subHeading.setLetterSpacing(0.14f);
        subHeading.setTextSize(24f);
        canvas.drawText("LAND AVAILABLE", 56f, 466f, subHeading);

        // 4. Left-Bottom Dedicated Green Contact Owner Card
        RectF contactBox = new RectF(44f, height - 300f, sidebarWidth - 44f, height - 58f);
        paint.setColor(Color.parseColor("#052E16"));
        canvas.drawRoundRect(contactBox, 26f, 26f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(Color.parseColor("#34D399"));
        canvas.drawRoundRect(contactBox, 26f, 26f, paint);
        paint.setStyle(Paint.Style.FILL);

        TextPaint contactLabel = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        contactLabel.setColor(Color.parseColor("#6EE7B7"));
        contactLabel.setTypeface(paragraphTypeface);
        contactLabel.setLetterSpacing(0.12f);
        contactLabel.setTextSize(21f);
        canvas.drawText("CONTACT OWNER", contactBox.left + 32f, contactBox.top + 56f, contactLabel);

        // Phone badge + Mobile Number (auto-fit if very long)
        TextPaint mobilePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mobilePaint.setColor(Color.WHITE);
        mobilePaint.setTypeface(paragraphTypeface);
        float maxMobileW = contactBox.width() - 64f;
        PosterTextFitter.fitSingleLineTextSize(record.getMobile(), mobilePaint, maxMobileW, 44f, 20f);
        canvas.drawText(record.getMobile(), contactBox.left + 32f, contactBox.top + 132f, mobilePaint);

        TextPaint recordMeta = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        recordMeta.setColor(Color.parseColor("#A7F3D0"));
        recordMeta.setTypeface(paragraphTypeface);
        recordMeta.setTextSize(19f);
        canvas.drawText(record.getRecordId() + "  ·  " + record.getDate(),
                contactBox.left + 32f, contactBox.bottom - 34f, recordMeta);

        // 5. Right Side Land Information Framed Panel
        RectF panelRect = new RectF(sidebarWidth + 52f, 58f, width - 56f, height - 58f);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(panelRect, 28f, 28f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(Color.parseColor("#BBF7D0"));
        canvas.drawRoundRect(panelRect, 28f, 28f, paint);

        // Surveyor Corner Brackets inside right panel
        paint.setColor(Color.parseColor("#059669"));
        paint.setStrokeWidth(6f);
        float br = 28f;
        // Top-left bracket
        canvas.drawLine(panelRect.left + 28f, panelRect.top + 28f, panelRect.left + 28f + br, panelRect.top + 28f, paint);
        canvas.drawLine(panelRect.left + 28f, panelRect.top + 28f, panelRect.left + 28f, panelRect.top + 28f + br, paint);
        // Bottom-right bracket
        canvas.drawLine(panelRect.right - 28f, panelRect.bottom - 28f, panelRect.right - 28f - br, panelRect.bottom - 28f, paint);
        canvas.drawLine(panelRect.right - 28f, panelRect.bottom - 28f, panelRect.right - 28f, panelRect.bottom - 28f - br, paint);
        paint.setStyle(Paint.Style.FILL);

        // Panel Header
        TextPaint panelTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        panelTitle.setColor(Color.parseColor("#065F46"));
        panelTitle.setTypeface(titleTypeface);
        panelTitle.setTextSize(34f);
        canvas.drawText("LAND INFORMATION", panelRect.left + 58f, panelRect.top + 92f, panelTitle);

        paint.setColor(Color.parseColor("#10B981"));
        canvas.drawRect(panelRect.left + 58f, panelRect.top + 114f, panelRect.left + 240f, panelRect.top + 120f, paint);

        paint.setColor(Color.parseColor("#E2E8F0"));
        canvas.drawRect(panelRect.left + 240f, panelRect.top + 116f, panelRect.right - 58f, panelRect.top + 118f, paint);

        // Complete Original Land Details
        TextPaint detailsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        detailsPaint.setColor(Color.parseColor("#092E19"));
        detailsPaint.setTypeface(paragraphTypeface);

        int boxLeft = (int) (panelRect.left + 58f);
        int boxTop = (int) (panelRect.top + 150f);
        int boxWidth = (int) (panelRect.width() - 116f);
        int boxHeight = (int) (panelRect.bottom - 66f - boxTop);

        PosterTextFitter.FitResult fit = PosterTextFitter.fitDetailsText(
                record.getDetails(),
                detailsPaint,
                boxWidth,
                boxHeight,
                40f,
                16f
        );
        PosterTextFitter.drawFitResult(canvas, fit, boxLeft, boxTop, boxWidth, boxHeight);

        return fit.isOverflowed();
    }

    private static void drawLocationPinEmblem(@NonNull Canvas canvas, float left, float top, float size) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.argb(42, 255, 255, 255));
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size), 22f, 22f, bg);

        Paint pin = new Paint(Paint.ANTI_ALIAS_FLAG);
        pin.setColor(Color.WHITE);
        float cx = left + size / 2f;
        float cy = top + size / 2f - 6f;

        Path p = new Path();
        p.addCircle(cx, cy, 18f, Path.Direction.CW);
        canvas.drawPath(p, pin);

        Path tip = new Path();
        tip.moveTo(cx - 14f, cy + 9f);
        tip.lineTo(cx, cy + 32f);
        tip.lineTo(cx + 14f, cy + 9f);
        tip.close();
        canvas.drawPath(tip, pin);

        Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
        hole.setColor(Color.parseColor("#065F46"));
        canvas.drawCircle(cx, cy, 7.5f, hole);
    }
}
