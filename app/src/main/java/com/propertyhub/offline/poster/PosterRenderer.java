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
    public static final int POSTER_HEIGHT = 1920;
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
            bitmap = Bitmap.createBitmap(POSTER_WIDTH, POSTER_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            boolean overflowed;
            if (record.getCategory() == PropertyCategory.HOUSE_FOR_RENT) {
                overflowed = HousePosterTemplate.render(appContext, canvas, record, POSTER_WIDTH, POSTER_HEIGHT);
            } else {
                overflowed = LandPosterTemplate.render(appContext, canvas, record, POSTER_WIDTH, POSTER_HEIGHT);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(384 * 1024);
            boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            if (!compressed) {
                return PosterRenderResult.error("JPEG compression failed.");
            }
            return PosterRenderResult.success(out.toByteArray(), overflowed);
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
