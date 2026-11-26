package com.propertyhub.offline.poster;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

/**
 * Loads only the two project-bundled local poster fonts:
 * - title.ttf ("title" in res/font or assets)
 * - paragraph.ttf ("paragraph" in res/font or assets)
 */
public final class PosterFontLoader {

    private static volatile Typeface cachedTitleTypeface;
    private static volatile Typeface cachedParagraphTypeface;

    private PosterFontLoader() {
    }

    @NonNull
    public static Typeface loadTitleTypeface(@Nullable Context context) {
        if (cachedTitleTypeface != null) {
            return cachedTitleTypeface;
        }
        Typeface loaded = loadLocalFont(context, "title");
        if (loaded != null) {
            cachedTitleTypeface = loaded;
            return loaded;
        }
        return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    }

    @NonNull
    public static Typeface loadParagraphTypeface(@Nullable Context context) {
        if (cachedParagraphTypeface != null) {
            return cachedParagraphTypeface;
        }
        Typeface loaded = loadLocalFont(context, "paragraph");
        if (loaded != null) {
            cachedParagraphTypeface = loaded;
            return loaded;
        }
        return Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    }

    @Nullable
    private static Typeface loadLocalFont(@Nullable Context context, @NonNull String baseName) {
        if (context == null) {
            return null;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;

        // 1. Primary Android resource location: res/font/<baseName>.ttf
        try {
            int fontResId = appContext.getResources()
                    .getIdentifier(baseName, "font", appContext.getPackageName());
            if (fontResId != 0) {
                Typeface resFont = ResourcesCompat.getFont(appContext, fontResId);
                if (resFont != null) {
                    return resFont;
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Asset locations fallback (assets/<baseName>.ttf or assets/fonts/<baseName>.ttf)
        String[] assetCandidates = new String[]{
                baseName + ".ttf",
                "fonts/" + baseName + ".ttf",
                "font/" + baseName + ".ttf"
        };
        for (String path : assetCandidates) {
            try {
                return Typeface.createFromAsset(appContext.getAssets(), path);
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
