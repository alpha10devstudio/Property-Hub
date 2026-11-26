package com.propertyhub.offline.poster;

import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PosterTextFitter {

    public static final class FitResult {
        private final List<StaticLayout> paragraphLayouts;
        private final float paragraphGapPx;
        private final int totalHeightPx;
        private final float textSizePx;
        private final boolean overflowed;

        public FitResult(
                @NonNull List<StaticLayout> paragraphLayouts,
                float paragraphGapPx,
                int totalHeightPx,
                float textSizePx,
                boolean overflowed
        ) {
            this.paragraphLayouts = paragraphLayouts;
            this.paragraphGapPx = paragraphGapPx;
            this.totalHeightPx = totalHeightPx;
            this.textSizePx = textSizePx;
            this.overflowed = overflowed;
        }

        @NonNull
        public List<StaticLayout> getParagraphLayouts() {
            return paragraphLayouts;
        }

        public float getParagraphGapPx() {
            return paragraphGapPx;
        }

        public int getTotalHeightPx() {
            return totalHeightPx;
        }

        public float getTextSizePx() {
            return textSizePx;
        }

        public boolean isOverflowed() {
            return overflowed;
        }
    }

    private PosterTextFitter() {
    }

    @NonNull
    public static FitResult fitDetailsText(
            @NonNull String rawDetails,
            @NonNull TextPaint paint,
            int maxWidthPx,
            int maxHeightPx,
            float startTextSizePx,
            float minTextSizePx
    ) {
        List<String> paragraphs = DetailsParagraphParser.parse(rawDetails);
        return fitParagraphs(paragraphs, paint, maxWidthPx, maxHeightPx, startTextSizePx, minTextSizePx);
    }

    @NonNull
    public static FitResult fitParagraphs(
            @NonNull List<String> paragraphs,
            @NonNull TextPaint paint,
            int maxWidthPx,
            int maxHeightPx,
            float startTextSizePx,
            float minTextSizePx
    ) {
        List<String> safeParagraphs = paragraphs.isEmpty()
                ? Collections.singletonList("-")
                : paragraphs;

        int safeWidth = Math.max(120, maxWidthPx);
        float size = startTextSizePx;
        FitResult bestCandidate = null;

        while (size >= minTextSizePx) {
            float lineSpacingMult = 1.12f;
            float paragraphGap = Math.max(10f, size * 0.42f);
            FitResult candidate = buildCandidate(
                    safeParagraphs,
                    paint,
                    safeWidth,
                    size,
                    lineSpacingMult,
                    paragraphGap
            );
            bestCandidate = candidate;
            if (candidate.getTotalHeightPx() <= maxHeightPx && !hasHorizontalWordClip(candidate, safeWidth)) {
                return candidate;
            }
            size -= 2f;
        }

        // At minimum font size, try slightly tighter paragraph gap (still visibly larger than line spacing)
        paint.setTextSize(minTextSizePx);
        float tightGap = Math.max(8f, minTextSizePx * 0.30f);
        FitResult tightCandidate = buildCandidate(
                safeParagraphs,
                paint,
                safeWidth,
                minTextSizePx,
                1.05f,
                tightGap
        );
        if (tightCandidate.getTotalHeightPx() <= maxHeightPx) {
            return tightCandidate;
        }

        // Still overflowing at minimum font size -> report overflow warning cleanly
        FitResult fallback = bestCandidate != null ? tightCandidate : buildCandidate(
                safeParagraphs,
                paint,
                safeWidth,
                minTextSizePx,
                1.05f,
                tightGap
        );
        return new FitResult(
                fallback.getParagraphLayouts(),
                fallback.getParagraphGapPx(),
                fallback.getTotalHeightPx(),
                fallback.getTextSizePx(),
                true
        );
    }

    @NonNull
    private static FitResult buildCandidate(
            @NonNull List<String> paragraphs,
            @NonNull TextPaint paint,
            int widthPx,
            float textSizePx,
            float lineSpacingMult,
            float paragraphGapPx
    ) {
        paint.setTextSize(textSizePx);
        List<StaticLayout> layouts = new ArrayList<>(paragraphs.size());
        float totalHeight = 0f;

        for (int i = 0; i < paragraphs.size(); i++) {
            String para = paragraphs.get(i);
            StaticLayout layout = StaticLayout.Builder
                    .obtain(para, 0, para.length(), paint, widthPx)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, lineSpacingMult)
                    .setIncludePad(true)
                    .build();
            layouts.add(layout);
            totalHeight += layout.getHeight();
            if (i < paragraphs.size() - 1) {
                totalHeight += paragraphGapPx;
            }
        }

        return new FitResult(layouts, paragraphGapPx, Math.round(totalHeight), textSizePx, false);
    }

    private static boolean hasHorizontalWordClip(@NonNull FitResult candidate, int maxWidthPx) {
        for (StaticLayout layout : candidate.getParagraphLayouts()) {
            for (int line = 0; line < layout.getLineCount(); line++) {
                if (layout.getLineWidth(line) > maxWidthPx + 2f) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void drawFitResult(
            @NonNull Canvas canvas,
            @NonNull FitResult fitResult,
            float x,
            float y,
            int maxWidthPx,
            int maxHeightPx
    ) {
        drawFitResult(canvas, fitResult, x, y, maxWidthPx, maxHeightPx, false);
    }

    public static void drawFitResult(
            @NonNull Canvas canvas,
            @NonNull FitResult fitResult,
            float x,
            float y,
            int maxWidthPx,
            int maxHeightPx,
            boolean centerVertically
    ) {
        canvas.save();
        // Slight vertical bleed margin (4px) preserves tall Bengali diacritics while staying inside card padding
        canvas.clipRect(x - 2f, y - 4f, x + maxWidthPx + 2f, y + maxHeightPx + 4f);

        float startY = y;
        if (centerVertically && fitResult.getTotalHeightPx() < maxHeightPx) {
            startY = y + (maxHeightPx - fitResult.getTotalHeightPx()) / 2f;
        }

        float currentY = startY;
        List<StaticLayout> layouts = fitResult.getParagraphLayouts();
        for (int i = 0; i < layouts.size(); i++) {
            StaticLayout layout = layouts.get(i);
            canvas.save();
            canvas.translate(x, currentY);
            layout.draw(canvas);
            canvas.restore();
            currentY += layout.getHeight() + fitResult.getParagraphGapPx();
        }

        canvas.restore();
    }

    public static float fitSingleLineTextSize(
            @NonNull String text,
            @NonNull TextPaint paint,
            float maxWidthPx,
            float startTextSizePx,
            float minTextSizePx
    ) {
        float size = startTextSizePx;
        paint.setTextSize(size);
        while (paint.measureText(text) > maxWidthPx && size > minTextSizePx) {
            size -= 1.5f;
            paint.setTextSize(size);
        }
        return Math.max(minTextSizePx, size);
    }
}
