package com.propertyhub.offline.poster;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads poster fonts cleanly from app/src/main/res/font (or assets fallback) with caching:
 * - title.ttf ("title"): poster titles and headers
 * - paragraph.ttf ("paragraph"): body descriptions, details, labels, and notes
 * - contact.ttf / contact.otf ("contact"): contact label & mobile number (falls back cleanly to title font if absent)
 */
public final class PosterFontLoader {

    private static volatile Typeface cachedTitleTypeface;
    private static volatile Typeface cachedParagraphTypeface;
    private static volatile Typeface cachedContactTypeface;
    private static volatile Boolean cachedHasDedicatedContact;

    private static final Map<String, Typeface> STYLED_VARIANT_CACHE = new ConcurrentHashMap<>();

    private PosterFontLoader() {
    }

    public static void preloadAll(@Nullable Context context) {
        loadTitleTypeface(context);
        loadParagraphTypeface(context);
        loadContactTypeface(context);
    }

    @NonNull
    public static Typeface loadTitleTypeface(@Nullable Context context) {
        Typeface existing = cachedTitleTypeface;
        if (existing != null) {
            return existing;
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
        Typeface existing = cachedParagraphTypeface;
        if (existing != null) {
            return existing;
        }
        Typeface loaded = loadLocalFont(context, "paragraph");
        if (loaded != null) {
            cachedParagraphTypeface = loaded;
            return loaded;
        }
        return Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    }

    @NonNull
    public static Typeface loadContactTypeface(@Nullable Context context) {
        Typeface existing = cachedContactTypeface;
        if (existing != null) {
            return existing;
        }
        Typeface loaded = loadLocalFont(context, "contact");
        if (loaded != null) {
            cachedHasDedicatedContact = Boolean.TRUE;
            cachedContactTypeface = loaded;
            return loaded;
        }
        cachedHasDedicatedContact = Boolean.FALSE;
        return loadTitleTypeface(context);
    }

    public static boolean hasDedicatedContactFont(@Nullable Context context) {
        Boolean flag = cachedHasDedicatedContact;
        if (flag != null) {
            return flag;
        }
        loadContactTypeface(context);
        Boolean resolved = cachedHasDedicatedContact;
        return resolved != null && resolved;
    }

    /**
     * Returns a cached Typeface.create(...) variant so repeated layout/draw passes
     * do not recreate Typeface objects per span or paragraph.
     */
    @NonNull
    public static Typeface getStyledVariant(
            @Nullable Typeface baseTypeface,
            boolean bold,
            boolean italic
    ) {
        int style = (bold ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0);
        if (style == Typeface.NORMAL && baseTypeface != null) {
            return baseTypeface;
        }
        int baseIdentity = System.identityHashCode(baseTypeface);
        String key = baseIdentity + "_" + style;
        Typeface cached = STYLED_VARIANT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Typeface created = baseTypeface != null
                ? Typeface.create(baseTypeface, style)
                : Typeface.defaultFromStyle(style);
        STYLED_VARIANT_CACHE.put(key, created);
        return created;
    }

    @Nullable
    private static Typeface loadLocalFont(@Nullable Context context, @NonNull String baseName) {
        if (context == null) {
            return null;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;

        // 1. Primary Android resource location: res/font/<baseName>.(ttf|otf)
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

        // 2. Asset locations fallback (assets/<baseName>.ttf/.otf or assets/fonts/<baseName>.ttf/.otf)
        String[] assetCandidates = new String[]{
                baseName + ".ttf",
                baseName + ".otf",
                "fonts/" + baseName + ".ttf",
                "fonts/" + baseName + ".otf",
                "font/" + baseName + ".ttf",
                "font/" + baseName + ".otf"
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
