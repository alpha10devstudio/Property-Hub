package com.propertyhub.offline.poster;

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

import com.propertyhub.offline.model.PropertyRecord;

public final class HousePosterTemplate {

    private HousePosterTemplate() {
    }

    public static boolean render(
            @NonNull Canvas canvas,
            @NonNull PropertyRecord record,
            int width,
            int height
    ) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 1. Deep Navy to Royal Blue Background Gradient
        paint.setShader(new LinearGradient(
                0f, 0f, width, height,
                Color.parseColor("#061E54"),
                Color.parseColor("#0E43B3"),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        // 2. Subtle Architectural Geometric Beams
        paint.setColor(Color.argb(22, 255, 255, 255));
        Path beam1 = new Path();
        beam1.moveTo(width * 0.62f, 0f);
        beam1.lineTo(width, 0f);
        beam1.lineTo(width, height * 0.52f);
        beam1.lineTo(width * 0.45f, 0f);
        beam1.close();
        canvas.drawPath(beam1, paint);

        paint.setColor(Color.argb(16, 147, 197, 253));
        canvas.drawCircle(width - 90f, 110f, 220f, paint);
        canvas.drawCircle(110f, height - 80f, 170f, paint);

        // 3. Top Header Section (House Icon + Main Heading + Property Hub Brand)
        drawHouseIcon(canvas, 84f, 70f, 96f);

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        titlePaint.setTextSize(62f);
        canvas.drawText("HOUSE FOR RENT", 210f, 126f, titlePaint);

        TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.parseColor("#93C5FD"));
        subPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        subPaint.setLetterSpacing(0.14f);
        subPaint.setTextSize(24f);
        canvas.drawText("PROPERTY AVAILABLE", 212f, 166f, subPaint);

        // Top-right Property Hub brand badge
        RectF brandPill = new RectF(width - 370f, 72f, width - 84f, 164f);
        paint.setColor(Color.argb(38, 255, 255, 255));
        canvas.drawRoundRect(brandPill, 20f, 20f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(90, 191, 219, 254));
        canvas.drawRoundRect(brandPill, 20f, 20f, paint);
        paint.setStyle(Paint.Style.FILL);

        TextPaint brandTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandTitle.setColor(Color.WHITE);
        brandTitle.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        brandTitle.setTextSize(26f);
        brandTitle.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("PROPERTY HUB", brandPill.centerX(), brandPill.top + 42f, brandTitle);

        TextPaint brandTag = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandTag.setColor(Color.parseColor("#DBEAFE"));
        brandTag.setTextSize(17f);
        brandTag.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Collect • Generate • Share", brandPill.centerX(), brandPill.top + 72f, brandTag);

        // 4. White Property Details Card (Middle)
        RectF cardRect = new RectF(84f, 214f, width - 84f, height - 224f);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(cardRect, 32f, 32f, paint);

        // Top electric blue accent strip inside card
        paint.setColor(Color.parseColor("#1D4ED8"));
        RectF accentStrip = new RectF(cardRect.left + 44f, cardRect.top, cardRect.right - 44f, cardRect.top + 8f);
        canvas.drawRoundRect(accentStrip, 4f, 4f, paint);

        // "PROPERTY DETAILS" heading + record date
        TextPaint sectionHeading = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        sectionHeading.setColor(Color.parseColor("#0B2A6F"));
        sectionHeading.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        sectionHeading.setTextSize(32f);
        canvas.drawText("PROPERTY DETAILS", cardRect.left + 52f, cardRect.top + 68f, sectionHeading);

        TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        metaPaint.setColor(Color.parseColor("#64748B"));
        metaPaint.setTextSize(21f);
        metaPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(record.getRecordId() + "  |  " + record.getDate(),
                cardRect.right - 52f, cardRect.top + 66f, metaPaint);

        // Hairline divider
        paint.setColor(Color.parseColor("#E2E8F0"));
        canvas.drawRect(cardRect.left + 52f, cardRect.top + 92f, cardRect.right - 52f, cardRect.top + 94f, paint);

        // Original Details text with dynamic intelligent sizing
        TextPaint detailsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        detailsPaint.setColor(Color.parseColor("#0F172A"));
        detailsPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        int textBoxLeft = (int) (cardRect.left + 54f);
        int textBoxTop = (int) (cardRect.top + 122f);
        int textBoxWidth = (int) (cardRect.width() - 108f);
        int textBoxHeight = (int) (cardRect.bottom - 34f - textBoxTop);

        PosterTextFitter.FitResult fit = PosterTextFitter.fitDetailsText(
                record.getDetails(),
                detailsPaint,
                textBoxWidth,
                textBoxHeight,
                40f,
                16f
        );
        PosterTextFitter.drawFitResult(canvas, fit, textBoxLeft, textBoxTop, textBoxWidth, textBoxHeight);

        // 5. Bottom Dedicated "CONTACT OWNER" Electric Blue Panel
        RectF contactRect = new RectF(84f, height - 188f, width - 84f, height - 56f);
        paint.setShader(new LinearGradient(
                contactRect.left, contactRect.top, contactRect.right, contactRect.bottom,
                Color.parseColor("#0A3EA8"),
                Color.parseColor("#1D62E8"),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(contactRect, 26f, 26f, paint);
        paint.setShader(null);

        // Contact Owner label on left
        TextPaint contactLabel = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        contactLabel.setColor(Color.parseColor("#BFDBFE"));
        contactLabel.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        contactLabel.setLetterSpacing(0.12f);
        contactLabel.setTextSize(22f);
        canvas.drawText("CONTACT OWNER", contactRect.left + 44f, contactRect.centerY() + 8f, contactLabel);

        // Phone vector icon + prominent white mobile number on right
        drawPhoneIcon(canvas, contactRect.centerX() - 36f, contactRect.centerY() - 22f, 44f);

        TextPaint mobilePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mobilePaint.setColor(Color.WHITE);
        mobilePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        mobilePaint.setTextSize(50f);
        mobilePaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(record.getMobile(), contactRect.right - 44f, contactRect.centerY() + 18f, mobilePaint);

        return fit.isOverflowed();
    }

    private static void drawHouseIcon(@NonNull Canvas canvas, float left, float top, float size) {
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.argb(40, 255, 255, 255));
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size), 24f, 24f, bgPaint);

        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setColor(Color.WHITE);
        iconPaint.setStyle(Paint.Style.FILL);

        float cx = left + size / 2f;
        float cy = top + size / 2f;

        Path roof = new Path();
        roof.moveTo(cx, cy - 28f);
        roof.lineTo(cx + 30f, cy - 2f);
        roof.lineTo(cx + 22f, cy - 2f);
        roof.lineTo(cx + 22f, cy + 26f);
        roof.lineTo(cx - 22f, cy + 26f);
        roof.lineTo(cx - 22f, cy - 2f);
        roof.lineTo(cx - 30f, cy - 2f);
        roof.close();
        canvas.drawPath(roof, iconPaint);

        Paint doorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        doorPaint.setColor(Color.parseColor("#0B3B9D"));
        canvas.drawRect(cx - 8f, cy + 5f, cx + 8f, cy + 26f, doorPaint);
    }

    private static void drawPhoneIcon(@NonNull Canvas canvas, float left, float top, float size) {
        Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        circle.setColor(Color.argb(48, 255, 255, 255));
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, circle);

        Paint phonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        phonePaint.setColor(Color.WHITE);
        phonePaint.setStyle(Paint.Style.STROKE);
        phonePaint.setStrokeWidth(4.5f);
        phonePaint.setStrokeCap(Paint.Cap.ROUND);
        float cx = left + size / 2f;
        float cy = top + size / 2f;
        Path p = new Path();
        p.moveTo(cx - 9f, cy - 10f);
        p.quadTo(cx - 11f, cy + 6f, cx + 9f, cy + 10f);
        canvas.drawPath(p, phonePaint);
        canvas.drawCircle(cx - 8f, cy - 9f, 3.2f, circle);
    }
}
