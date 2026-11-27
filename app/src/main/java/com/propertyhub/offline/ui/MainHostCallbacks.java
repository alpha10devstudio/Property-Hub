package com.propertyhub.offline.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.export.ZipExportManager;
import com.propertyhub.offline.model.AppThemeMode;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.poster.AutoPosterManager;
import com.propertyhub.offline.poster.BulkPosterManager;
import com.propertyhub.offline.poster.PosterRenderer;
import com.propertyhub.offline.storage.SettingsManager;
import com.propertyhub.offline.storage.StorageManager;

public interface MainHostCallbacks {
    @NonNull
    StorageManager getStorageManager();

    @NonNull
    SettingsManager getSettingsManager();

    @NonNull
    PosterRenderer getPosterRenderer();

    @NonNull
    AutoPosterManager getAutoPosterManager();

    @NonNull
    BulkPosterManager getBulkPosterManager();

    @NonNull
    ZipExportManager getZipExportManager();

    void requestDirectoryPicker(@Nullable PropertyCategory pendingCategory);

    void showTopToast(
            @NonNull String title,
            @NonNull String subtitle,
            boolean isHouseTheme
    );

    void openHome();

    void openSettings();

    void openGeneratedFiles();

    void applyThemeMode(@NonNull AppThemeMode themeMode);

    void refreshStorageBanner();

    void notifyFilesUpdated();

    void notifyFieldsUpdated();
}
