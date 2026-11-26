package com.propertyhub.offline.poster;

import androidx.annotation.Nullable;

public final class PosterRenderResult {

    private final boolean success;
    @Nullable
    private final byte[] jpegBytes;
    private final boolean textOverflowWarning;
    @Nullable
    private final String errorMessage;

    private PosterRenderResult(
            boolean success,
            @Nullable byte[] jpegBytes,
            boolean textOverflowWarning,
            @Nullable String errorMessage
    ) {
        this.success = success;
        this.jpegBytes = jpegBytes;
        this.textOverflowWarning = textOverflowWarning;
        this.errorMessage = errorMessage;
    }

    public static PosterRenderResult success(byte[] jpegBytes, boolean textOverflowWarning) {
        return new PosterRenderResult(true, jpegBytes, textOverflowWarning, null);
    }

    public static PosterRenderResult error(String errorMessage) {
        return new PosterRenderResult(false, null, false, errorMessage);
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

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }
}
