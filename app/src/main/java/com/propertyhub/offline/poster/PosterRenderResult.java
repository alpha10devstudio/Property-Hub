package com.propertyhub.offline.poster;

import androidx.annotation.Nullable;

public final class PosterRenderResult {

    private final boolean success;
    @Nullable
    private final byte[] jpegBytes;
    private final boolean textOverflowWarning;
    private final int width;
    private final int height;
    @Nullable
    private final String errorMessage;

    private PosterRenderResult(
            boolean success,
            @Nullable byte[] jpegBytes,
            boolean textOverflowWarning,
            int width,
            int height,
            @Nullable String errorMessage
    ) {
        this.success = success;
        this.jpegBytes = jpegBytes;
        this.textOverflowWarning = textOverflowWarning;
        this.width = width;
        this.height = height;
        this.errorMessage = errorMessage;
    }

    public static PosterRenderResult success(byte[] jpegBytes, boolean textOverflowWarning) {
        return success(jpegBytes, textOverflowWarning, PosterRenderer.POSTER_WIDTH, 0);
    }

    public static PosterRenderResult success(
            byte[] jpegBytes,
            boolean textOverflowWarning,
            int width,
            int height
    ) {
        return new PosterRenderResult(true, jpegBytes, textOverflowWarning, width, height, null);
    }

    public static PosterRenderResult error(String errorMessage) {
        return new PosterRenderResult(false, null, false, 0, 0, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    @Nullable
    public byte[] getJpegBytes() {
        return jpegBytes;
    }

    public boolean hasTextOverflowWarning() {
        return textOverflowWarning;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }
}
