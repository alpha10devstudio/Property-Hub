package com.propertyhub.offline.poster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
        PosterThemeConfig theme = PosterThemeConfig.landForSale();
        int forestGreen = theme.getPrimaryColor();
        int emeraldAccent = theme.getSecondaryAccentColor();
        int softMint = theme.getSoftSurfaceColor();

        Typeface titleTypeface = PosterFontLoader.loadTitleTypeface(context);
        Typeface paragraphTypeface = PosterFontLoader.loadParagraphTypeface(context);

        DynamicPosterLayoutEngine.LayoutPlan layout = DynamicPosterLayoutEngine.solveLayout(
                theme,
                record.getDetails(),
                paragraphTypeface,
                width,
                height
        );

        // 1. Crisp White Background & Double Surveyor Outer Border with Corner Brackets
        canvas.drawColor(theme.getCanvasBackgroundColor());
        drawLandSurveyOuterBorder(canvas, layout.getOuterRect(), theme.getOuterBorderRadius(), forestGreen, emeraldAccent);

        // 2. Top Location Pin + Surveyed Contour Arcs & Horizon Boundary Stakes Header
        drawTopLandHeaderEmblem(canvas, layout.getHeaderRect(), forestGreen, emeraldAccent);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        // 3. Architectural Forest Green Title Card ("জমি বিক্রি করা হবে") — FIXED Font Size
        RectF titleRect = layout.getTitleRect();
        fillPaint.setColor(forestGreen);
        canvas.drawRoundRect(
                titleRect,
                theme.getTitleCornerRadius(),
                theme.getTitleCornerRadius(),
                fillPaint
        );

        // Top and bottom emerald framing strips + diamond survey accents inside Title Card
        fillPaint.setColor(emeraldAccent);
        float stripWidth = Math.min(460f, titleRect.width() * 0.36f);
        float stripLeft = titleRect.centerX() - stripWidth / 2f;
        canvas.drawRoundRect(
                new RectF(stripLeft, titleRect.top + 8f, stripLeft + stripWidth, titleRect.top + 14f),
                3f, 3f, fillPaint
        );
        canvas.drawRoundRect(
                new RectF(stripLeft, titleRect.bottom - 14f, stripLeft + stripWidth, titleRect.bottom - 8f),
                3f, 3f, fillPaint
        );

        drawDiamondMarker(canvas, titleRect.left + 72f, titleRect.centerY(), 15f, emeraldAccent, Color.WHITE);
        drawDiamondMarker(canvas, titleRect.right - 72f, titleRect.centerY(), 15f, emeraldAccent, Color.WHITE);

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTypeface(titleTypeface);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(theme.getFixedTitleFontSizePx());
        Paint.FontMetrics titleFm = titlePaint.getFontMetrics();
        float titleBaseline = titleRect.centerY() - (titleFm.ascent + titleFm.descent) / 2f;
        canvas.drawText(theme.getTitleText(), titleRect.centerX(), titleBaseline, titlePaint);

        // 4. Dynamic Details Card with Mint Left Plot Zone, Surveyor Corners & Meridian Divider
        RectF detailsCard = layout.getDetailsCardRect();
        float detailsRadius = theme.getDetailsCornerRadius();

        fillPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(detailsCard, detailsRadius, detailsRadius, fillPaint);

        // Subtle Mint tint inside Left Illustration Pane (clipped cleanly to Details Card shape)
        canvas.save();
        Path cardClip = new Path();
        cardClip.addRoundRect(detailsCard, detailsRadius, detailsRadius, Path.Direction.CW);
        canvas.clipPath(cardClip);
        fillPaint.setColor(softMint);
        canvas.drawRect(detailsCard.left, detailsCard.top, layout.getDividerX(), detailsCard.bottom, fillPaint);
        canvas.restore();

        // Card Outline
        Paint cardBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBorder.setColor(forestGreen);
        cardBorder.setStyle(Paint.Style.STROKE);
        cardBorder.setStrokeWidth(5.5f);
        canvas.drawRoundRect(detailsCard, detailsRadius, detailsRadius, cardBorder);

        // Subtle inner emerald L-corner surveyor ticks on the right text side
        drawRightCornerBrackets(canvas, detailsCard, emeraldAccent);

        // Left Surveyed Land Parcel + Location Pin + Trees Illustration
        drawDetailsCardLandIllustration(
                canvas,
                detailsCard,
                layout.getDividerX(),
                forestGreen,
                emeraldAccent
        );

        // Meridian Vertical Divider with top/bottom emerald diamond anchor dots
        float dividerX = layout.getDividerX();
        float dividerInset = Math.min(34f, detailsCard.height() * 0.12f);
        Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(forestGreen);
        dividerPaint.setStrokeWidth(4.5f);
        dividerPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(
                dividerX,
                detailsCard.top + dividerInset,
                dividerX,
                detailsCard.bottom - dividerInset,
                dividerPaint
        );
        drawDiamondMarker(canvas, dividerX, detailsCard.top + dividerInset, 7.5f, emeraldAccent, Color.WHITE);
        drawDiamondMarker(canvas, dividerX, detailsCard.bottom - dividerInset, 7.5f, emeraldAccent, Color.WHITE);

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

        // 5. Bottom Framed Forest Green Contact Card + Rounded-Square Phone Badge + Fixed Mobile Number
        RectF contactRect = layout.getContactRect();
        fillPaint.setColor(forestGreen);
        canvas.drawRoundRect(
                contactRect,
                theme.getContactCornerRadius(),
                theme.getContactCornerRadius(),
                fillPaint
        );

        // Crisp emerald top edge trim bar inside Contact Card
        fillPaint.setColor(emeraldAccent);
        canvas.drawRoundRect(
                new RectF(contactRect.left + 260f, contactRect.top + 8f, contactRect.right - 64f, contactRect.top + 13f),
                2.5f, 2.5f, fillPaint
        );

        // Rounded-Square Shield White Phone Badge (distinct from House's circle)
        float badgeSize = Math.min(116f, contactRect.height() * 0.68f);
        float badgeCx = contactRect.left + 108f;
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

        // Dual Emerald/White Divider inside Contact Card
        float contactDividerX = contactRect.left + 206f;
        float contactDividerInset = Math.min(28f, contactRect.height() * 0.16f);
        dividerPaint.setColor(Color.parseColor("#A7F3D0"));
        dividerPaint.setStrokeWidth(4.5f);
        canvas.drawLine(
                contactDividerX,
                contactRect.top + contactDividerInset,
                contactDividerX,
                contactRect.bottom - contactDividerInset,
                dividerPaint
        );

        // Fixed Contact Font Size ("যোগাযোগ: " + exact mobile number)
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

        // Subtle inner survey frame line
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
        float inset = 18f;
        float arm = 22f;

        // Top-right L bracket
        canvas.drawLine(card.right - inset - arm, card.top + inset, card.right - inset, card.top + inset, p);
        canvas.drawLine(card.right - inset, card.top + inset, card.right - inset, card.top + inset + arm, p);

        // Bottom-right L bracket
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
        float bottomY = headerRect.bottom - 6f;

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        // Symmetric Horizon Boundary Bars with Diamond Survey Nodes
        float barY = bottomY - 28f;
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

        // Curved Survey Plot Ground Arc below Location Pin
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

        // Center Prominent Map Location Pin (with white inner ring & emerald dot)
        float pinCx = cx;
        float pinCircleY = headerRect.top + 38f;
        float pinRadius = 29f;
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

    private static void drawDetailsCardLandIllustration(
            @NonNull Canvas canvas,
            @NonNull RectF card,
            float dividerX,
            int forestGreen,
            int emeraldAccent
    ) {
        float paneWidth = dividerX - card.left;
        // Normalized coordinate space width=290, height=300 centered around (0,0)
        float scale = Math.min(
                1.08f,
                Math.min((paneWidth - 34f) / 292f, (card.height() - 42f) / 302f)
        );

        canvas.save();
        canvas.translate(card.left + paneWidth / 2f, card.centerY());
        canvas.scale(scale, scale);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);

        // 1. Soft horizon hill contour beneath the land plot
        fill.setColor(forestGreen);
        Path baseArc = new Path();
        baseArc.moveTo(-136f, 122f);
        baseArc.quadTo(0f, 96f, 136f, 122f);
        baseArc.quadTo(0f, 110f, -136f, 122f);
        baseArc.close();
        canvas.drawPath(baseArc, fill);

        // 2. Perspective Surveyed Land Parcel (trapezoid grid)
        // Rear corners (-86, 16) to (86, 16); Front corners (-128, 100) to (128, 100)
        Path plotSurface = new Path();
        plotSurface.moveTo(-84f, 18f);
        plotSurface.lineTo(84f, 18f);
        plotSurface.lineTo(126f, 100f);
        plotSurface.lineTo(-126f, 100f);
        plotSurface.close();

        fill.setColor(Color.WHITE);
        canvas.drawPath(plotSurface, fill);

        fill.setColor(Color.argb(38, 22, 163, 74));
        canvas.drawPath(plotSurface, fill);

        // Internal dashed parcel division grid lines
        Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dashPaint.setColor(emeraldAccent);
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(3f);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{9f, 7f}, 0f));
        canvas.drawLine(0f, 18f, 0f, 100f, dashPaint);
        canvas.drawLine(-106f, 58f, 106f, 58f, dashPaint);

        // Bold Forest Green Land Parcel Perimeter
        stroke.setColor(forestGreen);
        stroke.setStrokeWidth(5.5f);
        canvas.drawPath(plotSurface, stroke);

        // 3. Two Stylized Green Trees behind rear plot corners
        drawMiniLandTree(canvas, -104f, 20f, 0.92f, forestGreen, emeraldAccent);
        drawMiniLandTree(canvas, 104f, 20f, 0.85f, forestGreen, emeraldAccent);

        // 4. Four Survey Boundary Corner Stakes on parcel vertices
        drawBoundaryStake(canvas, -84f, 18f, forestGreen, emeraldAccent);
        drawBoundaryStake(canvas, 84f, 18f, forestGreen, emeraldAccent);
        drawBoundaryStake(canvas, -126f, 100f, forestGreen, emeraldAccent);
        drawBoundaryStake(canvas, 126f, 100f, forestGreen, emeraldAccent);

        // 5. Location Ripple & Map Pin hovering above parcel center
        stroke.setColor(emeraldAccent);
        stroke.setStrokeWidth(3f);
        canvas.drawOval(new RectF(-38f, 30f, 38f, 56f), stroke);

        float pinCx = 0f;
        float pinCy = -64f;
        float pinR = 36f;
        Path pin = new Path();
        pin.addCircle(pinCx, pinCy, pinR, Path.Direction.CW);
        pin.moveTo(pinCx - pinR * 0.85f, pinCy + pinR * 0.45f);
        pin.lineTo(pinCx, 36f);
        pin.lineTo(pinCx + pinR * 0.85f, pinCy + pinR * 0.45f);
        pin.close();

        fill.setColor(forestGreen);
        canvas.drawPath(pin, fill);

        fill.setColor(Color.WHITE);
        canvas.drawCircle(pinCx, pinCy, 17f, fill);

        fill.setColor(emeraldAccent);
        canvas.drawCircle(pinCx, pinCy, 9f, fill);

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
