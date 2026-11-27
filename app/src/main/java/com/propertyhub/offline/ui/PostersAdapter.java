package com.propertyhub.offline.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.propertyhub.offline.R;
import com.propertyhub.offline.model.PosterFileItem;
import com.propertyhub.offline.model.PropertyCategory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PostersAdapter extends RecyclerView.Adapter<PostersAdapter.PosterViewHolder> {

    public interface PosterActionListener {
        void onViewPoster(@NonNull PosterFileItem item);
        void onSharePoster(@NonNull PosterFileItem item);
        void onRegeneratePoster(@NonNull PosterFileItem item);
        void onDeletePoster(@NonNull PosterFileItem item);
    }

    private final List<PosterFileItem> items = new ArrayList<>();
    private final PosterActionListener listener;
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> thumbCache;

    public PostersAdapter(@NonNull PosterActionListener listener) {
        this.listener = listener;
        final int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024L);
        final int cacheKb = Math.min(12 * 1024, maxKb / 8);
        this.thumbCache = new LruCache<String, Bitmap>(cacheKb) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public void submitList(@NonNull List<PosterFileItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void evictCacheForUri(@NonNull Uri uri) {
        thumbCache.remove(uri.toString());
    }

    public void shutdown() {
        thumbExecutor.shutdown();
        thumbCache.evictAll();
    }

    @NonNull
    @Override
    public PosterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_poster_card, parent, false);
        return new PosterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PosterViewHolder holder, int position) {
        PosterFileItem item = items.get(position);
        holder.bind(item, listener, thumbCache, thumbExecutor, mainHandler);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class PosterViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivThumb;
        private final TextView tvRecordId;
        private final TextView tvCategory;
        private final TextView tvMeta;
        private final Button btnView;
        private final Button btnShare;
        private final Button btnRegenerate;
        private final Button btnDelete;

        PosterViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivPosterThumb);
            tvRecordId = itemView.findViewById(R.id.tvPosterRecordId);
            tvCategory = itemView.findViewById(R.id.tvPosterCategory);
            tvMeta = itemView.findViewById(R.id.tvPosterMeta);
            btnView = itemView.findViewById(R.id.btnPosterView);
            btnShare = itemView.findViewById(R.id.btnPosterShare);
            btnRegenerate = itemView.findViewById(R.id.btnPosterRegenerate);
            btnDelete = itemView.findViewById(R.id.btnPosterDelete);
        }

        void bind(
                @NonNull PosterFileItem item,
                @NonNull PosterActionListener listener,
                @NonNull LruCache<String, Bitmap> cache,
                @NonNull ExecutorService executor,
                @NonNull Handler mainHandler
        ) {
            tvRecordId.setText(item.getRecordId());
            tvCategory.setText(item.getCategory().getDisplayName());
            tvCategory.setTextColor(ContextCompat.getColor(
                    itemView.getContext(),
                    item.getCategory() == PropertyCategory.HOUSE_FOR_RENT
                            ? R.color.blue_primary
                            : R.color.green_primary
            ));
            String timePart = (item.getLinkedRecord() != null && !item.getLinkedRecord().getTime().isEmpty())
                    ? " · " + item.getLinkedRecord().getTime()
                    : "";
            String dimsPart = (item.getImageWidth() > 0 && item.getImageHeight() > 0)
                    ? "  ·  " + item.getImageWidth() + "×" + item.getImageHeight() + "px"
                    : "";
            tvMeta.setText("Date: " + item.getRecordDate() + timePart + dimsPart + "  ·  TXT: " + item.getSourceTxtFile());

            String key = item.getFileUri().toString();
            ivThumb.setTag(key);

            Bitmap cached = cache.get(key);
            if (cached != null && !cached.isRecycled()) {
                ivThumb.setImageBitmap(cached);
            } else {
                ivThumb.setImageDrawable(null);
                final Context appContext = itemView.getContext().getApplicationContext();
                executor.execute(() -> {
                    Bitmap decoded = decodeThumbnail(appContext, item.getFileUri());
                    if (decoded != null) {
                        cache.put(key, decoded);
                        mainHandler.post(() -> {
                            if (key.equals(ivThumb.getTag())) {
                                ivThumb.setImageBitmap(decoded);
                            }
                        });
                    }
                });
            }

            ivThumb.setOnClickListener(v -> listener.onViewPoster(item));
            btnView.setOnClickListener(v -> listener.onViewPoster(item));
            btnShare.setOnClickListener(v -> listener.onSharePoster(item));
            btnRegenerate.setOnClickListener(v -> listener.onRegeneratePoster(item));
            btnDelete.setOnClickListener(v -> listener.onDeletePoster(item));
        }

        private static Bitmap decodeThumbnail(@NonNull Context context, @NonNull Uri uri) {
            try {
                BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
                boundsOpts.inJustDecodeBounds = true;
                try (InputStream boundsIn = context.getContentResolver().openInputStream(uri)) {
                    if (boundsIn == null) {
                        return null;
                    }
                    BitmapFactory.decodeStream(boundsIn, null, boundsOpts);
                }

                int sampleSize = 1;
                if (boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0) {
                    int targetWidth = 480;
                    int targetMaxHeight = 1400;
                    while ((boundsOpts.outWidth / (sampleSize * 2)) >= targetWidth
                            || (boundsOpts.outHeight / sampleSize) > targetMaxHeight) {
                        sampleSize *= 2;
                    }
                } else {
                    sampleSize = 4;
                }

                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        return null;
                    }
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = sampleSize;
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    return BitmapFactory.decodeStream(in, null, opts);
                }
            } catch (Exception e) {
                return null;
            }
        }
    }
}
