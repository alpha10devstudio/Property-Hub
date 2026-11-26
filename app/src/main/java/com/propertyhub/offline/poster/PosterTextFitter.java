package com.propertyhub.offline.poster;

import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;

public final class PosterTextFitter {

    public static final class FitResult {
        private final StaticLayout layout;
        private final boolean overflowed;

        public FitResult(@NonNull StaticLayout layout, boolean overflowed) {
            this.layout = layout;
            this.overflowed = overflowed;
        }

        @NonNull
        public StaticLayout getLayout() {
            return layout;
        }

        public boolean isOverflowed() {
            return overflowed;
        }
    }

    private PosterTextFitter() {
    }

    @NonNull
    public static FitResult fitDetailsText(
            @NonNull String text,
            @NonNull TextPaint paint,
            int maxWidthPx,
            int maxHeightPx,
            float startTextSizePx,
            float minTextSizePx
    ) {
        String safeText = text.trim().isEmpty() ? "-" : text;
        float size = startTextSizePx;
        StaticLayout chosen = null;
        boolean fits = false;

        while (size >= minTextSizePx) {
            paint.setTextSize(size);
            StaticLayout layout = StaticLayout.Builder
                    .obtain(safeText, 0, safeText.length(), paint, Math.max(100, maxWidthPx))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.24f)
                    .setIncludePad(false)
                    .build();

            chosen = layout;
            if (layout.getHeight() <= maxHeightPx) {
                fits = true;
                break;
            }
            size -= 2f;
        }

        if (chosen == null) {
            paint.setTextSize(minTextSizePx);
            chosen = StaticLayout.Builder
                    .obtain(safeText, 0, safeText.length(), paint, Math.max(100, maxWidthPx))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(false)
                    .build();
        }

        return new FitResult(chosen, !fits);
    }

    public static void drawFitResult(
            @NonNull Canvas canvas,
            @NonNull FitResult fitResult,
            float x,
            float y,
            int maxWidthPx,
            int maxHeightPx
    ) {
        canvas.save();
        canvas.clipRect(x, y, x + maxWidthPx, y + maxHeightPx);
        canvas.translate(x, y);
        fitResult.getLayout().draw(canvas);
        canvas.restore();
    }
}
