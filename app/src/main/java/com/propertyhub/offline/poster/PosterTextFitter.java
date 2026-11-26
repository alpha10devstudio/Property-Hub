package com.propertyhub.offline.poster;

import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    /**
     * Preserves paragraph.ttf while guaranteeing crisp bold and italic rendering
     * even when paragraph.ttf is a single-weight Typeface resource.
     */
    private static final class MarkdownStyleSpan extends MetricAffectingSpan {
        @Nullable
        private final Typeface baseTypeface;
        private final boolean bold;
        private final boolean italic;

        MarkdownStyleSpan(@Nullable Typeface baseTypeface, boolean bold, boolean italic) {
            this.baseTypeface = baseTypeface;
            this.bold = bold;
            this.italic = italic;
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            applyStyle(ds);
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint ds) {
            applyStyle(ds);
        }

        private void applyStyle(@NonNull TextPaint paint) {
            int style = (bold ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0);
            if (baseTypeface != null) {
                Typeface styled = Typeface.create(baseTypeface, style);
                paint.setTypeface(styled);
                if (bold && !styled.isBold()) {
                    paint.setFakeBoldText(true);
                }
                if (italic && !styled.isItalic()) {
                    paint.setTextSkewX(-0.24f);
                }
            } else {
                paint.setTypeface(Typeface.create(paint.getTypeface(), style));
                if (bold) {
                    paint.setFakeBoldText(true);
                }
                if (italic) {
                    paint.setTextSkewX(-0.24f);
                }
            }
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
        List<List<DetailsParagraphParser.StyledSegment>> styledParagraphs =
                DetailsParagraphParser.parseStyledParagraphs(rawDetails);
        return fitStyledParagraphs(
                styledParagraphs,
                paint,
                maxWidthPx,
                maxHeightPx,
                startTextSizePx,
                minTextSizePx
        );
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
        List<List<DetailsParagraphParser.StyledSegment>> styled = new ArrayList<>(paragraphs.size());
        for (String paragraph : paragraphs) {
            styled.add(DetailsParagraphParser.parseInlineFormatting(paragraph));
        }
        return fitStyledParagraphs(
                styled,
                paint,
                maxWidthPx,
                maxHeightPx,
                startTextSizePx,
                minTextSizePx
        );
    }

    @NonNull
    public static FitResult fitStyledParagraphs(
            @NonNull List<List<DetailsParagraphParser.StyledSegment>> paragraphs,
            @NonNull TextPaint paint,
            int maxWidthPx,
            int maxHeightPx,
            float startTextSizePx,
            float minTextSizePx
    ) {
        List<List<DetailsParagraphParser.StyledSegment>> safeParagraphs = paragraphs.isEmpty()
                ? Collections.singletonList(
                        Collections.singletonList(new DetailsParagraphParser.StyledSegment("-", false, false)))
                : paragraphs;

        int safeWidth = Math.max(120, maxWidthPx);
        float size = startTextSizePx;
        FitResult bestCandidate = null;
        Typeface baseTypeface = paint.getTypeface();

        while (size >= minTextSizePx) {
            float lineSpacingMult = 1.12f;
            float paragraphGap = Math.max(10f, size * 0.42f);
            FitResult candidate = buildStyledCandidate(
                    safeParagraphs,
                    paint,
                    baseTypeface,
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
        FitResult tightCandidate = buildStyledCandidate(
                safeParagraphs,
                paint,
                baseTypeface,
                safeWidth,
                minTextSizePx,
                1.05f,
                tightGap
        );
        if (tightCandidate.getTotalHeightPx() <= maxHeightPx) {
            return tightCandidate;
        }

        FitResult fallback = bestCandidate != null ? tightCandidate : buildStyledCandidate(
                safeParagraphs,
                paint,
                baseTypeface,
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
    public static FitResult buildStyledCandidate(
            @NonNull List<List<DetailsParagraphParser.StyledSegment>> paragraphs,
            @NonNull TextPaint paint,
            @Nullable Typeface baseTypeface,
            int widthPx,
            float textSizePx,
            float lineSpacingMult,
            float paragraphGapPx
    ) {
        paint.setTextSize(textSizePx);
        List<StaticLayout> layouts = new ArrayList<>(paragraphs.size());
        float totalHeight = 0f;

        for (int i = 0; i < paragraphs.size(); i++) {
            CharSequence charSequence = toSpannable(paragraphs.get(i), baseTypeface);
            StaticLayout layout = StaticLayout.Builder
                    .obtain(charSequence, 0, charSequence.length(), paint, widthPx)
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

    @NonNull
    private static SpannableStringBuilder toSpannable(
            @NonNull List<DetailsParagraphParser.StyledSegment> segments,
            @Nullable Typeface baseTypeface
    ) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (DetailsParagraphParser.StyledSegment segment : segments) {
            int start = ssb.length();
            ssb.append(segment.getText());
            int end = ssb.length();
            if (end > start && (segment.isBold() || segment.isItalic())) {
                ssb.setSpan(
                        new MarkdownStyleSpan(baseTypeface, segment.isBold(), segment.isItalic()),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
        if (ssb.length() == 0) {
            ssb.append("-");
        }
        return ssb;
    }

    public static boolean hasHorizontalWordClip(@NonNull FitResult candidate, int maxWidthPx) {
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
        // Slight vertical bleed margin (6px) preserves tall Bengali diacritics inside card padding
        canvas.clipRect(x - 4f, y - 6f, x + maxWidthPx + 4f, y + maxHeightPx + 6f);

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
