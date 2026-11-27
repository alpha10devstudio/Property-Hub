package com.propertyhub.offline.poster;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;

import java.io.ByteArrayOutputStream;

public class PosterRenderer {

    public static final int POSTER_WIDTH = 1440;
    public static final int JPEG_QUALITY = 92;

    @Nullable
    private final Context appContext;

    public PosterRenderer() {
        this.appContext = null;
    }

    public PosterRenderer(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    public PosterRenderResult renderPoster(@NonNull PropertyRecord record) {
        Bitmap bitmap = null;
        try {
            int dynamicHeight;
            boolean overflowed;

            if (record.getCategory() == PropertyCategory.HOUSE_FOR_RENT) {
                DynamicPosterLayoutEngine.HouseDynamicPlan plan =
                        HousePosterTemplate.measurePlan(appContext, record, POSTER_WIDTH);
                dynamicHeight = DynamicPosterLayoutEngine.clampPosterHeight(plan.totalHeight);
                bitmap = Bitmap.createBitmap(POSTER_WIDTH, dynamicHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                overflowed = HousePosterTemplate.render(appContext, canvas, record, plan);
            } else {
                DynamicPosterLayoutEngine.LandDynamicPlan plan =
                        LandPosterTemplate.measurePlan(appContext, record, POSTER_WIDTH);
                dynamicHeight = DynamicPosterLayoutEngine.clampPosterHeight(plan.totalHeight);
                bitmap = Bitmap.createBitmap(POSTER_WIDTH, dynamicHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                overflowed = LandPosterTemplate.render(appContext, canvas, record, plan);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(384 * 1024);
            boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            if (!compressed) {
                return PosterRenderResult.error("JPEG compression failed.");
            }
            return PosterRenderResult.success(out.toByteArray(), overflowed, POSTER_WIDTH, dynamicHeight);
        } catch (OutOfMemoryError oom) {
            return PosterRenderResult.error("Insufficient memory to render poster.");
        } catch (Exception e) {
            String msg = e.getMessage();
            return PosterRenderResult.error(msg != null ? msg : "Poster rendering error.");
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }
}
