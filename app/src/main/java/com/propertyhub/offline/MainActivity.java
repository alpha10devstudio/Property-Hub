package com.propertyhub.offline;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.propertyhub.offline.export.ZipExportManager;
import com.propertyhub.offline.model.AppThemeMode;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.poster.AutoPosterManager;
import com.propertyhub.offline.poster.BulkPosterManager;
import com.propertyhub.offline.poster.PosterRenderer;
import com.propertyhub.offline.storage.SettingsManager;
import com.propertyhub.offline.storage.StorageManager;
import com.propertyhub.offline.ui.CollectDataFragment;
import com.propertyhub.offline.ui.GeneratedFilesFragment;
import com.propertyhub.offline.ui.MainHostCallbacks;
import com.propertyhub.offline.ui.SettingsFragment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements MainHostCallbacks {

    private static final long TOP_TOAST_DURATION_MS = 2600L;
    private static final String STATE_ACTIVE_SCREEN = "state_active_screen";
    private static final int SCREEN_COLLECT = 0;
    private static final int SCREEN_SETTINGS = 1;
    private static final int SCREEN_FILES = 2;

    private static final String TAG_COLLECT = "tab_collect";
    private static final String TAG_SETTINGS = "tab_settings";
    private static final String TAG_FILES = "tab_files";

    private SettingsManager settingsManager;
    private StorageManager storageManager;
    private PosterRenderer posterRenderer;
    private AutoPosterManager autoPosterManager;
    private BulkPosterManager bulkPosterManager;
    private ZipExportManager zipExportManager;

    private final ExecutorService hostIoExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ImageButton btnHeaderFiles;
    private ImageButton btnHeaderSettings;
    private View storageWarningBanner;
    private TextView tvStorageWarningText;
    private Button btnBannerSelectFolder;

    private View topToastCard;
    private ImageView ivTopToastIcon;
    private TextView tvTopToastTitle;
    private TextView tvTopToastMessage;
    private final Runnable hideTopToastRunnable = this::hideTopToastAnimated;

    private AlertDialog storageSetupDialog;
    @Nullable
    private PropertyCategory pendingSubmitCategory;
    private int activeScreen = SCREEN_COLLECT;

    private final ActivityResultLauncher<Intent> directoryPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri treeUri = result.getData().getData();
                            if (treeUri != null) {
                                handleSelectedTreeUri(treeUri);
                                return;
                            }
                        }
                        pendingSubmitCategory = null;
                        refreshStorageBanner();
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settingsManager = new SettingsManager(this);
        applySelectedThemeResource(settingsManager.getThemeMode());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storageManager = new StorageManager(this, settingsManager, new com.propertyhub.offline.util.DateTimeProvider());
        posterRenderer = new PosterRenderer(this);
        autoPosterManager = new AutoPosterManager(storageManager, posterRenderer, hostIoExecutor);
        bulkPosterManager = new BulkPosterManager(storageManager, posterRenderer);
        zipExportManager = new ZipExportManager(this, storageManager);

        if (savedInstanceState != null) {
            activeScreen = savedInstanceState.getInt(STATE_ACTIVE_SCREEN, SCREEN_COLLECT);
        }

        bindHeaderAndNavViews();
        initFragments(savedInstanceState);
        setupBackNavigation();

        if (!storageManager.hasValidStorageAccess()) {
            refreshStorageBanner();
            showStorageSetupDialog(true);
        } else {
            refreshStorageBanner();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_ACTIVE_SCREEN, activeScreen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStorageBanner();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(hideTopToastRunnable);
        if (storageSetupDialog != null && storageSetupDialog.isShowing()) {
            storageSetupDialog.dismiss();
        }
        bulkPosterManager.shutdown();
        zipExportManager.shutdown();
        hostIoExecutor.shutdown();
    }

    private void applySelectedThemeResource(@NonNull AppThemeMode mode) {
        if (mode == AppThemeMode.DARK) {
            setTheme(R.style.Theme_PropertyHub_Dark);
        } else if (mode == AppThemeMode.LIGHT) {
            setTheme(R.style.Theme_PropertyHub_Light);
        } else {
            setTheme(R.style.Theme_PropertyHub_Blue);
        }
    }

    private void bindHeaderAndNavViews() {
        View brandLogo = findViewById(R.id.ivBrandLogo);
        View headerTitleGroup = findViewById(R.id.headerTitleGroup);
        brandLogo.setOnClickListener(v -> openHome());
        headerTitleGroup.setOnClickListener(v -> openHome());

        btnHeaderFiles = findViewById(R.id.btnHeaderFiles);
        btnHeaderFiles.setOnClickListener(v -> {
            if (activeScreen == SCREEN_FILES) {
                openHome();
            } else {
                openGeneratedFiles();
            }
        });

        btnHeaderSettings = findViewById(R.id.btnHeaderSettings);
        btnHeaderSettings.setOnClickListener(v -> {
            if (activeScreen == SCREEN_SETTINGS) {
                openHome();
            } else {
                openSettings();
            }
        });

        storageWarningBanner = findViewById(R.id.storageWarningBanner);
        tvStorageWarningText = findViewById(R.id.tvStorageWarningText);
        btnBannerSelectFolder = findViewById(R.id.btnBannerSelectFolder);
        btnBannerSelectFolder.setOnClickListener(v -> requestDirectoryPicker(null));

        topToastCard = findViewById(R.id.topToastCard);
        ivTopToastIcon = findViewById(R.id.ivTopToastIcon);
        tvTopToastTitle = findViewById(R.id.tvTopToastTitle);
        tvTopToastMessage = findViewById(R.id.tvTopToastMessage);
        topToastCard.setOnClickListener(v -> {
            mainHandler.removeCallbacks(hideTopToastRunnable);
            hideTopToastAnimated();
        });
    }

    private void initFragments(@Nullable Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            CollectDataFragment collectFragment = new CollectDataFragment();
            SettingsFragment settingsFragment = new SettingsFragment();
            GeneratedFilesFragment filesFragment = new GeneratedFilesFragment();

            fm.beginTransaction()
                    .add(R.id.fragmentContainer, collectFragment, TAG_COLLECT)
                    .add(R.id.fragmentContainer, settingsFragment, TAG_SETTINGS)
                    .hide(settingsFragment)
                    .add(R.id.fragmentContainer, filesFragment, TAG_FILES)
                    .hide(filesFragment)
                    .commit();
            activeScreen = SCREEN_COLLECT;
            updateHeaderIconStates();
        } else {
            switchToScreen(activeScreen);
        }
    }

    private void switchToScreen(int screen) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment collect = fm.findFragmentByTag(TAG_COLLECT);
        Fragment settings = fm.findFragmentByTag(TAG_SETTINGS);
        Fragment files = fm.findFragmentByTag(TAG_FILES);

        FragmentTransaction tx = fm.beginTransaction();
        if (collect != null) {
            if (screen == SCREEN_COLLECT) {
                tx.show(collect);
            } else {
                tx.hide(collect);
            }
        }
        if (settings != null) {
            if (screen == SCREEN_SETTINGS) {
                tx.show(settings);
            } else {
                tx.hide(settings);
            }
        }
        if (files != null) {
            if (screen == SCREEN_FILES) {
                tx.show(files);
            } else {
                tx.hide(files);
            }
        }
        tx.commitAllowingStateLoss();
        activeScreen = screen;
        updateHeaderIconStates();

        if (screen == SCREEN_SETTINGS && settings instanceof SettingsFragment) {
            ((SettingsFragment) settings).refreshUi();
        } else if (screen == SCREEN_FILES && files instanceof GeneratedFilesFragment) {
            ((GeneratedFilesFragment) files).refreshAllData();
        }
    }

    private void updateHeaderIconStates() {
        if (btnHeaderFiles != null) {
            btnHeaderFiles.setAlpha(activeScreen == SCREEN_FILES ? 1.0f : 0.85f);
        }
        if (btnHeaderSettings != null) {
            btnHeaderSettings.setAlpha(activeScreen == SCREEN_SETTINGS ? 1.0f : 0.85f);
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (activeScreen != SCREEN_COLLECT) {
                    openHome();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @NonNull
    @Override
    public StorageManager getStorageManager() {
        return storageManager;
    }

    @NonNull
    @Override
    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    @NonNull
    @Override
    public PosterRenderer getPosterRenderer() {
        return posterRenderer;
    }

    @NonNull
    @Override
    public AutoPosterManager getAutoPosterManager() {
        return autoPosterManager;
    }

    @NonNull
    @Override
    public BulkPosterManager getBulkPosterManager() {
        return bulkPosterManager;
    }

    @NonNull
    @Override
    public ZipExportManager getZipExportManager() {
        return zipExportManager;
    }

    @Override
    public void requestDirectoryPicker(@Nullable PropertyCategory pendingCategory) {
        this.pendingSubmitCategory = pendingCategory;
        if (pendingCategory != null) {
            showStorageSetupDialog(true);
        } else {
            launchDirectoryPicker();
        }
    }

    @Override
    public void showTopToast(
            @NonNull String title,
            @NonNull String subtitle,
            boolean isHouseTheme
    ) {
        mainHandler.removeCallbacks(hideTopToastRunnable);

        tvTopToastTitle.setText(title);
        tvTopToastMessage.setText(subtitle);

        int tintColor = isHouseTheme ? 0xFF60A5FA : 0xFF22C55E;
        ivTopToastIcon.setColorFilter(tintColor);

        topToastCard.animate().cancel();
        if (topToastCard.getVisibility() != View.VISIBLE) {
            topToastCard.setVisibility(View.VISIBLE);
            topToastCard.setAlpha(0f);
            topToastCard.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                float startY = -(topToastCard.getHeight() + 48f);
                topToastCard.setTranslationY(startY);
                topToastCard.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(240L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            });
        } else {
            topToastCard.setTranslationY(0f);
            topToastCard.setAlpha(1f);
        }

        mainHandler.postDelayed(hideTopToastRunnable, TOP_TOAST_DURATION_MS);
    }

    private void hideTopToastAnimated() {
        if (isFinishing() || isDestroyed() || topToastCard.getVisibility() != View.VISIBLE) {
            return;
        }
        float endY = -(topToastCard.getHeight() + 48f);
        topToastCard.animate()
                .translationY(endY)
                .alpha(0f)
                .setDuration(200L)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> topToastCard.setVisibility(View.GONE))
                .start();
    }

    @Override
    public void openHome() {
        switchToScreen(SCREEN_COLLECT);
    }

    @Override
    public void openSettings() {
        switchToScreen(SCREEN_SETTINGS);
    }

    @Override
    public void openGeneratedFiles() {
        switchToScreen(SCREEN_FILES);
    }

    @Override
    public void applyThemeMode(@NonNull AppThemeMode themeMode) {
        settingsManager.setThemeMode(themeMode);
        recreate();
    }

    @Override
    public void refreshStorageBanner() {
        if (storageWarningBanner == null) {
            return;
        }
        boolean hasAccess = storageManager.hasValidStorageAccess();
        if (hasAccess) {
            storageWarningBanner.setVisibility(View.GONE);
        } else {
            storageWarningBanner.setVisibility(View.VISIBLE);
            tvStorageWarningText.setText(getString(R.string.storage_banner_missing));
        }

        Fragment settings = getSupportFragmentManager().findFragmentByTag(TAG_SETTINGS);
        if (settings instanceof SettingsFragment) {
            ((SettingsFragment) settings).refreshUi();
        }
    }

    @Override
    public void notifyFilesUpdated() {
        Fragment files = getSupportFragmentManager().findFragmentByTag(TAG_FILES);
        if (files instanceof GeneratedFilesFragment) {
            ((GeneratedFilesFragment) files).refreshAllData();
        }
    }

    private void showStorageSetupDialog(boolean requiredPrompt) {
        if (storageSetupDialog != null && storageSetupDialog.isShowing()) {
            storageSetupDialog.dismiss();
        }

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_storage_setup, null, false);

        TextView tvStatus = dialogView.findViewById(R.id.tvDialogFolderStatus);
        Button btnSelectFolder = dialogView.findViewById(R.id.btnDialogSelectFolder);
        Button btnClose = dialogView.findViewById(R.id.btnDialogClose);

        boolean valid = storageManager.hasValidStorageAccess();
        if (valid) {
            String path = storageManager.getSelectedFolderDisplayPath();
            tvStatus.setText("Active folder: " + (path != null ? path : "/Property Hub")
                    + "\nSubfolders: House for Rent, Land for Sale, Generated Posters & ZIP Exports");
            btnSelectFolder.setText(R.string.change_storage_folder);
            btnClose.setText(R.string.done);
        } else {
            tvStatus.setText(R.string.dialog_storage_instructions);
            btnSelectFolder.setText(R.string.select_storage_folder);
            btnClose.setText(requiredPrompt ? R.string.later : R.string.close);
        }

        storageSetupDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnSelectFolder.setOnClickListener(v -> {
            storageSetupDialog.dismiss();
            launchDirectoryPicker();
        });

        btnClose.setOnClickListener(v -> {
            pendingSubmitCategory = null;
            storageSetupDialog.dismiss();
        });

        storageSetupDialog.show();
    }

    private void launchDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri initialUri = storageManager.buildSuggestedDocumentsInitialUri();
            if (initialUri != null) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
            }
        }

        try {
            directoryPickerLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "No folder picker found on this device.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void handleSelectedTreeUri(@NonNull Uri treeUri) {
        hostIoExecutor.execute(() -> {
            boolean ok = storageManager.initializeDirectoryFromTreeUri(treeUri);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                refreshStorageBanner();
                notifyFilesUpdated();

                if (ok) {
                    String displayFolder = storageManager.getSelectedFolderDisplayPath();
                    showTopToast(
                            "Storage Folder Ready",
                            displayFolder != null ? displayFolder : "Property Hub",
                            true
                    );

                    PropertyCategory pending = pendingSubmitCategory;
                    pendingSubmitCategory = null;
                    if (pending != null) {
                        Fragment collect = getSupportFragmentManager().findFragmentByTag(TAG_COLLECT);
                        if (collect instanceof CollectDataFragment) {
                            ((CollectDataFragment) collect).retryPendingSubmission(pending);
                        }
                    }
                } else {
                    pendingSubmitCategory = null;
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Folder Setup Failed")
                            .setMessage("Could not create the Property Hub folders in the selected directory. Please choose a writable folder such as Documents.")
                            .setPositiveButton("Try Again", (d, w) -> launchDirectoryPicker())
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        });
    }
}
