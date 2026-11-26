package com.propertyhub.offline.poster;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.storage.StorageManager;

import java.util.concurrent.ExecutorService;

public class AutoPosterManager {

    public interface Callback {
        void onCompleted(@NonNull AutoPosterResult result);
    }

    public static final class AutoPosterResult {
        private final boolean success;
        private final boolean textOverflowWarning;
        @Nullable
        private final Uri posterUri;
        @Nullable
        private final String errorMessage;

        private AutoPosterResult(
                boolean success,
                boolean textOverflowWarning,
                @Nullable Uri posterUri,
                @Nullable String errorMessage
        ) {
            this.success = success;
            this.textOverflowWarning = textOverflowWarning;
            this.posterUri = posterUri;
            this.errorMessage = errorMessage;
        }

        public static AutoPosterResult success(@NonNull Uri posterUri, boolean textOverflowWarning) {
            return new AutoPosterResult(true, textOverflowWarning, posterUri, null);
        }

        public static AutoPosterResult failure(@NonNull String errorMessage) {
            return new AutoPosterResult(false, false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean hasTextOverflowWarning() {
            return textOverflowWarning;
        }

        @Nullable
        public Uri getPosterUri() {
            return posterUri;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private final StorageManager storageManager;
    private final PosterRenderer posterRenderer;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AutoPosterManager(
            @NonNull StorageManager storageManager,
            @NonNull PosterRenderer posterRenderer,
            @NonNull ExecutorService executor
    ) {
        this.storageManager = storageManager;
        this.posterRenderer = posterRenderer;
        this.executor = executor;
    }

    public void generatePosterAsync(
            @NonNull PropertyRecord record,
            boolean overwriteIfExists,
            @NonNull Callback callback
    ) {
        executor.execute(() -> {
            AutoPosterResult result = generatePosterSync(record, overwriteIfExists);
            mainHandler.post(() -> callback.onCompleted(result));
        });
    }

    @NonNull
    public AutoPosterResult generatePosterSync(
            @NonNull PropertyRecord record,
            boolean overwriteIfExists
    ) {
        try {
            if (!overwriteIfExists
                    && storageManager.posterExists(record.getCategory(), record.getRecordId())) {
                return AutoPosterResult.failure("Poster already exists for " + record.getRecordId() + ".");
            }

            PosterRenderResult renderResult = posterRenderer.renderPoster(record);
            if (!renderResult.isSuccess() || renderResult.getJpegBytes() == null) {
                return AutoPosterResult.failure(
                        renderResult.getErrorMessage() != null
                                ? renderResult.getErrorMessage()
                                : "Poster rendering failed."
                );
            }

            Uri savedUri = storageManager.savePosterJpeg(
                    record.getCategory(),
                    record.getRecordId(),
                    renderResult.getJpegBytes(),
                    overwriteIfExists
            );

            if (savedUri == null) {
                return AutoPosterResult.failure("Could not save generated poster image.");
            }

            record.setPosterGenerated(true);
            record.setPosterUri(savedUri);
            return AutoPosterResult.success(savedUri, renderResult.hasTextOverflowWarning());
        } catch (Exception e) {
            String msg = e.getMessage();
            return AutoPosterResult.failure(msg != null ? msg : "Poster generation failed.");
        }
    }
}
