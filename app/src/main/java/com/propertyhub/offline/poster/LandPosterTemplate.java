package com.propertyhub.offline.poster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
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
 * 1440x1920 (3:4 Portrait) Land for Sale Poster Template.
 * Distinct Green + White surveyor composition with double border, location pin contour header,
 * stepped emerald-trimmed Title banner, surveyed parcel plot showcase, adaptive 1/2-column dynamic cards
 * (omitting any empty fields), and shield-badge Contact card.
 */
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
        PosterThemeConfig theme = PosterThemeConfig.landForSale();
        int forestGreen = theme.getPrimaryColor();
        int emeraldAccent = theme.getSecondaryAccentColor();
        int softMint = theme.getSoftSurfaceColor();
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
                true,
                false
        );

        // 1. Crisp White Background & Double Surveyor Outer Border
        canvas.drawColor(theme.getCanvasBackgroundColor());
        drawLandSurveyOuterBorder(canvas, layout.getOuterRect(), theme.getOuterBorderRadius(), forestGreen, emeraldAccent);

        // 2. Top Location Pin + Surveyed Contour Header Emblem
        drawTopLandHeaderEmblem(canvas, layout.getHeaderRect(), forestGreen, emeraldAccent);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        // 3. Architectural Forest Green Title Banner (Editable Title in title.ttf)
        RectF titleRect = layout.getTitleRect();
        fillPaint.setColor(forestGreen);
        canvas.drawRoundRect(
                titleRect,
                theme.getTitleCornerRadius(),
                theme.getTitleCornerRadius(),
                fillPaint
        );

        fillPaint.setColor(emeraldAccent);
        float stripWidth = Math.min(480f, titleRect.width() * 0.38f);
        float stripLeft = titleRect.centerX() - stripWidth / 2f;
        canvas.drawRoundRect(
                new RectF(stripLeft, titleRect.top + 8f, stripLeft + stripWidth, titleRect.top + 14f),
                3f, 3f, fillPaint
        );
        canvas.drawRoundRect(
                new RectF(stripLeft, titleRect.bottom - 14f, stripLeft + stripWidth, titleRect.bottom - 8f),
                3f, 3f, fillPaint
        );

        drawDiamondMarker(canvas, titleRect.left + 64f, titleRect.centerY(), 14f, emeraldAccent, Color.WHITE);
        drawDiamondMarker(canvas, titleRect.right - 64f, titleRect.centerY(), 14f, emeraldAccent, Color.WHITE);

        StaticLayout titleLayout = layout.getTitleLayout();
        canvas.save();
        float titleTextLeft = titleRect.centerX() - titleLayout.getWidth() / 2f;
        float titleTextTop = titleRect.centerY() - titleLayout.getHeight() / 2f;
        canvas.translate(titleTextLeft, titleTextTop);
        titleLayout.draw(canvas);
        canvas.restore();

        // 4. Surveyed Land Parcel Panorama Showcase + Adaptive Multi-Field Cards
        drawLandShowcaseStrip(canvas, layout.getShowcaseRect(), forestGreen, emeraldAccent, softMint, cardBorder);
        drawLandFieldCards(canvas, layout, forestGreen, emeraldAccent, softMint, cardBorder);

        // 5. Bottom Emerald-Trimmed Forest Green Contact Card + Shield Badge
        RectF contactRect = layout.getContactRect();
        fillPaint.setColor(forestGreen);
        canvas.drawRoundRect(
                contactRect,
                theme.getContactCornerRadius(),
                theme.getContactCornerRadius(),
                fillPaint
        );

        fillPaint.setColor(emeraldAccent);
        canvas.drawRoundRect(
                new RectF(contactRect.left + 250f, contactRect.top + 8f, contactRect.right - 60f, contactRect.top + 13f),
                2.5f, 2.5f, fillPaint
        );

        float badgeSize = Math.min(108f, contactRect.height() * 0.68f);
        float badgeCx = contactRect.left + 106f;
        float badgeCy = contactRect.centerY();
        RectF badgeRect = new RectF(
                badgeCx - badgeSize / 2f,
                badgeCy - badgeSize / 2f,
                badgeCx + badgeSize / 2f,
                badgeCy + badgeSize / 2f
        );
        fillPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(badgeRect, 24f, 24f, fillPaint);

        Paint badgeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeStroke.setColor(Color.parseColor("#86EFAC"));
        badgeStroke.setStyle(Paint.Style.STROKE);
        badgeStroke.setStrokeWidth(3f);
        canvas.drawRoundRect(
                new RectF(badgeRect.left + 5f, badgeRect.top + 5f, badgeRect.right - 5f, badgeRect.bottom - 5f),
                19f, 19f, badgeStroke
        );

        drawGreenHandsetIcon(canvas, badgeCx, badgeCy, badgeSize * 0.50f, forestGreen);

        Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(Color.parseColor("#A7F3D0"));
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

    private static void drawLandShowcaseStrip(
            @NonNull Canvas canvas,
            @NonNull RectF showcaseRect,
            int forestGreen,
            int emeraldAccent,
            int softMint,
            int cardBorder
    ) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(softMint);
        canvas.drawRoundRect(showcaseRect, 24f, 24f, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(forestGreen);
        stroke.setStrokeWidth(4f);
        canvas.drawRoundRect(showcaseRect, 24f, 24f, stroke);

        drawRightCornerBrackets(canvas, showcaseRect, emeraldAccent);

        // Decorative dashed surveyor contour lines flanking centered Land Parcel Plot graphic
        Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        dash.setColor(emeraldAccent);
        dash.setAlpha(110);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(3f);
        dash.setPathEffect(new DashPathEffect(new float[]{12f, 10f}, 0f));

        float midY = showcaseRect.centerY();
        canvas.drawLine(showcaseRect.left + 44f, midY - 14f, showcaseRect.centerX() - 185f, midY - 14f, dash);
        canvas.drawLine(showcaseRect.centerX() + 185f, midY - 14f, showcaseRect.right - 44f, midY - 14f, dash);
        canvas.drawLine(showcaseRect.left + 76f, midY + 18f, showcaseRect.centerX() - 185f, midY + 18f, dash);
        canvas.drawLine(showcaseRect.centerX() + 185f, midY + 18f, showcaseRect.right - 76f, midY + 18f, dash);

        drawLandParcelIllustration(canvas, showcaseRect, forestGreen, emeraldAccent);
    }

    private static void drawLandFieldCards(
            @NonNull Canvas canvas,
            @NonNull DynamicPosterLayoutEngine.PortraitLayoutPlan layout,
            int forestGreen,
            int emeraldAccent,
            int softMint,
            int cardBorder
    ) {
        Paint cardFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardFill.setStyle(Paint.Style.FILL);
        cardFill.setColor(Color.WHITE);

        Paint softHeaderTint = new Paint(Paint.ANTI_ALIAS_FLAG);
        softHeaderTint.setStyle(Paint.Style.FILL);
        softHeaderTint.setColor(softMint);

        Paint cardStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardStroke.setStyle(Paint.Style.STROKE);
        cardStroke.setColor(forestGreen);
        cardStroke.setAlpha(185);
        cardStroke.setStrokeWidth(3.5f);

        Paint emeraldStripe = new Paint(Paint.ANTI_ALIAS_FLAG);
        emeraldStripe.setStyle(Paint.Style.FILL);
        emeraldStripe.setColor(emeraldAccent);

        for (DynamicPosterLayoutEngine.FieldCardPlacement card : layout.getFieldCards()) {
            RectF r = card.getCardRect();
            canvas.drawRoundRect(r, 20f, 20f, cardFill);

            // Soft mint tint behind label header band inside each Land field card
            RectF labelRect = card.getLabelRect();
            RectF valueRect = card.getValueRect();
            float headerBandBottom = Math.min(r.bottom - 8f, labelRect.bottom + 8f);
            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(r, 20f, 20f, Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawRect(r.left, r.top, r.right, headerBandBottom, softHeaderTint);
            canvas.restore();

            canvas.drawRoundRect(r, 20f, 20f, cardStroke);

            // Left emerald vertical accent notch
            canvas.drawRoundRect(
                    new RectF(r.left + 2f, r.top + 14f, r.left + 10f, r.bottom - 14f),
                    4f, 4f, emeraldStripe
            );

            // Small diamond survey marker beside field label
            drawDiamondMarker(canvas, labelRect.left + 10f, labelRect.centerY(), 8.5f, forestGreen, Color.WHITE);

            canvas.save();
            canvas.translate(labelRect.left + 28f, labelRect.top);
            card.getLabelLayout().draw(canvas);
            canvas.restore();

            // Rich text value (paragraph.ttf + Bold/Italic)
            canvas.save();
            canvas.clipRect(valueRect.left - 4f, valueRect.top - 4f, valueRect.right + 4f, r.bottom - 6f);
            canvas.translate(valueRect.left, valueRect.top);
            card.getValueLayout().draw(canvas);
            canvas.restore();
        }
    }

    private static void drawLandSurveyOuterBorder(
            @NonNull Canvas canvas,
            @NonNull RectF outerRect,
            float radius,
            int forestGreen,
            int emeraldAccent
    ) {
        Paint outerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerStroke.setColor(forestGreen);
        outerStroke.setStyle(Paint.Style.STROKE);
        outerStroke.setStrokeWidth(6.5f);
        canvas.drawRoundRect(outerRect, radius, radius, outerStroke);

        Paint innerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerStroke.setColor(emeraldAccent);
        innerStroke.setStyle(Paint.Style.STROKE);
        innerStroke.setStrokeWidth(2f);
        RectF inner = new RectF(
                outerRect.left + 10f,
                outerRect.top + 10f,
                outerRect.right - 10f,
                outerRect.bottom - 10f
        );
        canvas.drawRoundRect(inner, Math.max(8f, radius - 6f), Math.max(8f, radius - 6f), innerStroke);
    }

    private static void drawRightCornerBrackets(
            @NonNull Canvas canvas,
            @NonNull RectF card,
            int accentColor
    ) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(accentColor);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setStrokeCap(Paint.Cap.ROUND);
        float inset = 16f;
        float arm = 20f;

        canvas.drawLine(card.right - inset - arm, card.top + inset, card.right - inset, card.top + inset, p);
        canvas.drawLine(card.right - inset, card.top + inset, card.right - inset, card.top + inset + arm, p);

        canvas.drawLine(card.right - inset - arm, card.bottom - inset, card.right - inset, card.bottom - inset, p);
        canvas.drawLine(card.right - inset, card.bottom - inset, card.right - inset, card.bottom - inset - arm, p);
    }

    private static void drawTopLandHeaderEmblem(
            @NonNull Canvas canvas,
            @NonNull RectF headerRect,
            int forestGreen,
            int emeraldAccent
    ) {
        float cx = headerRect.centerX();
        float bottomY = headerRect.bottom - 8f;

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        float barY = bottomY - 34f;
        float innerGap = 158f;
        float barOuter = 540f;

        stroke.setColor(forestGreen);
        stroke.setStrokeWidth(6f);
        canvas.drawLine(cx - barOuter, barY, cx - innerGap, barY, stroke);
        canvas.drawLine(cx + innerGap, barY, cx + barOuter, barY, stroke);

        stroke.setColor(emeraldAccent);
        stroke.setStrokeWidth(3f);
        canvas.drawLine(cx - barOuter + 44f, barY + 12f, cx - innerGap - 18f, barY + 12f, stroke);
        canvas.drawLine(cx + innerGap + 18f, barY + 12f, cx + barOuter - 44f, barY + 12f, stroke);

        drawDiamondMarker(canvas, cx - innerGap, barY, 10f, forestGreen, Color.WHITE);
        drawDiamondMarker(canvas, cx + innerGap, barY, 10f, forestGreen, Color.WHITE);
        drawDiamondMarker(canvas, cx - barOuter, barY, 7f, emeraldAccent, Color.WHITE);
        drawDiamondMarker(canvas, cx + barOuter, barY, 7f, emeraldAccent, Color.WHITE);

        stroke.setColor(forestGreen);
        stroke.setStrokeWidth(5f);
        Path contourOuter = new Path();
        contourOuter.moveTo(cx - 122f, bottomY);
        contourOuter.quadTo(cx, bottomY - 34f, cx + 122f, bottomY);
        canvas.drawPath(contourOuter, stroke);

        stroke.setColor(emeraldAccent);
        stroke.setStrokeWidth(3.5f);
        Path contourInner = new Path();
        contourInner.moveTo(cx - 82f, bottomY + 4f);
        contourInner.quadTo(cx, bottomY - 16f, cx + 82f, bottomY + 4f);
        canvas.drawPath(contourInner, stroke);

        float pinCx = cx;
        float pinCircleY = headerRect.top + 48f;
        float pinRadius = 31f;
        float pinTipY = bottomY - 14f;

        Path pinBody = new Path();
        pinBody.addCircle(pinCx, pinCircleY, pinRadius, Path.Direction.CW);
        pinBody.moveTo(pinCx - pinRadius * 0.86f, pinCircleY + pinRadius * 0.45f);
        pinBody.lineTo(pinCx, pinTipY);
        pinBody.lineTo(pinCx + pinRadius * 0.86f, pinCircleY + pinRadius * 0.45f);
        pinBody.close();

        fill.setColor(forestGreen);
        canvas.drawPath(pinBody, fill);

        fill.setColor(Color.WHITE);
        canvas.drawCircle(pinCx, pinCircleY, 14f, fill);

        fill.setColor(emeraldAccent);
        canvas.drawCircle(pinCx, pinCircleY, 7.5f, fill);
    }

    private static void drawLandParcelIllustration(
            @NonNull Canvas canvas,
            @NonNull RectF showcaseRect,
            int forestGreen,
            int emeraldAccent
    ) {
        float scale = Math.min(
                0.76f,
                Math.min((showcaseRect.width() - 40f) / 310f, (showcaseRect.height() - 22f) / 260f)
        );

        canvas.save();
        canvas.translate(showcaseRect.centerX(), showcaseRect.centerY() + 10f);
        canvas.scale(scale, scale);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);

        fill.setColor(forestGreen);
        Path baseArc = new Path();
        baseArc.moveTo(-136f, 114f);
        baseArc.quadTo(0f, 88f, 136f, 114f);
        baseArc.quadTo(0f, 102f, -136f, 114f);
        baseArc.close();
        canvas.drawPath(baseArc, fill);

        Path plotSurface = new Path();
        plotSurface.moveTo(-84f, 12f);
        plotSurface.lineTo(84f, 12f);
        plotSurface.lineTo(126f, 94f);
        plotSurface.lineTo(-126f, 94f);
        plotSurface.close();

        fill.setColor(Color.WHITE);
        canvas.drawPath(plotSurface, fill);

        fill.setColor(Color.argb(38, 22, 163, 74));
        canvas.drawPath(plotSurface, fill);

        Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dashPaint.setColor(emeraldAccent);
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(3f);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{9f, 7f}, 0f));
        canvas.drawLine(0f, 12f, 0f, 94f, dashPaint);
        canvas.drawLine(-106f, 52f, 106f, 52f, dashPaint);

        stroke.setColor(forestGreen);
        stroke.setStrokeWidth(5.5f);
        canvas.drawPath(plotSurface, stroke);

        drawMiniLandTree(canvas, -104f, 14f, 0.90f, forestGreen, emeraldAccent);
        drawMiniLandTree(canvas, 104f, 14f, 0.85f, forestGreen, emeraldAccent);

        drawBoundaryStake(canvas, -84f, 12f, forestGreen, emeraldAccent);
        drawBoundaryStake(canvas, 84f, 12f, forestGreen, emeraldAccent);
        drawBoundaryStake(canvas, -126f, 94f, forestGreen, emeraldAccent);
        drawBoundaryStake(canvas, 126f, 94f, forestGreen, emeraldAccent);

        stroke.setColor(emeraldAccent);
        stroke.setStrokeWidth(3f);
        canvas.drawOval(new RectF(-36f, 24f, 36f, 50f), stroke);

        float pinCx = 0f;
        float pinCy = -64f;
        float pinR = 34f;
        Path pin = new Path();
        pin.addCircle(pinCx, pinCy, pinR, Path.Direction.CW);
        pin.moveTo(pinCx - pinR * 0.85f, pinCy + pinR * 0.45f);
        pin.lineTo(pinCx, 30f);
        pin.lineTo(pinCx + pinR * 0.85f, pinCy + pinR * 0.45f);
        pin.close();

        fill.setColor(forestGreen);
        canvas.drawPath(pin, fill);

        fill.setColor(Color.WHITE);
        canvas.drawCircle(pinCx, pinCy, 16f, fill);

        fill.setColor(emeraldAccent);
        canvas.drawCircle(pinCx, pinCy, 8.5f, fill);

        canvas.restore();
    }

    private static void drawMiniLandTree(
            @NonNull Canvas canvas,
            float baseX,
            float baseY,
            float scale,
            int forestGreen,
            int emeraldAccent
    ) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.translate(baseX, baseY);
        canvas.scale(scale, scale);

        p.setColor(forestGreen);
        canvas.drawRect(-4.5f, -40f, 4.5f, 0f, p);
        canvas.drawCircle(0f, -64f, 22f, p);
        p.setColor(emeraldAccent);
        canvas.drawCircle(-11f, -50f, 14f, p);
        canvas.drawCircle(11f, -50f, 14f, p);

        canvas.restore();
    }

    private static void drawBoundaryStake(
            @NonNull Canvas canvas,
            float x,
            float y,
            int forestGreen,
            int emeraldAccent
    ) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(forestGreen);
        canvas.drawCircle(x, y, 8.5f, p);
        p.setColor(Color.WHITE);
        canvas.drawCircle(x, y, 4.5f, p);
        p.setColor(emeraldAccent);
        canvas.drawCircle(x, y, 2.2f, p);
    }

    private static void drawDiamondMarker(
            @NonNull Canvas canvas,
            float cx,
            float cy,
            float radius,
            int outerColor,
            int innerColor
    ) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);

        Path outer = new Path();
        outer.moveTo(cx, cy - radius);
        outer.lineTo(cx + radius, cy);
        outer.lineTo(cx, cy + radius);
        outer.lineTo(cx - radius, cy);
        outer.close();
        p.setColor(outerColor);
        canvas.drawPath(outer, p);

        float innerR = radius * 0.45f;
        Path inner = new Path();
        inner.moveTo(cx, cy - innerR);
        inner.lineTo(cx + innerR, cy);
        inner.lineTo(cx, cy + innerR);
        inner.lineTo(cx - innerR, cy);
        inner.close();
        p.setColor(innerColor);
        canvas.drawPath(inner, p);
    }

    private static void drawGreenHandsetIcon(
            @NonNull Canvas canvas,
            float cx,
            float cy,
            float iconRadius,
            int forestGreen
    ) {
        Paint phonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        phonePaint.setColor(forestGreen);
        phonePaint.setStyle(Paint.Style.FILL);

        float s = iconRadius / 56f;
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
