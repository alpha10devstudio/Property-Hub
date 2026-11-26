package com.propertyhub.offline;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final long SUBMIT_DEBOUNCE_MS = 700L;
    private static final long TOP_TOAST_DURATION_MS = 2400L;

    private SafStorageManager storageManager;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AtomicBoolean isSubmittingHouse = new AtomicBoolean(false);
    private final AtomicBoolean isSubmittingLand = new AtomicBoolean(false);
    private long lastHouseSubmitElapsedMs = 0L;
    private long lastLandSubmitElapsedMs = 0L;

    // House for Rent views
    private EditText etHouseMobile;
    private EditText etHouseDetails;
    private Button btnSubmitHouse;

    // Land for Sale views
    private EditText etLandMobile;
    private EditText etLandDetails;
    private Button btnSubmitLand;

    // Storage prompt / status banner
    private View storageWarningBanner;
    private TextView tvStorageWarningText;
    private Button btnBannerSelectFolder;

    // Top-sliding auto-dismissing toast notification
    private View topToastCard;
    private ImageView ivTopToastIcon;
    private TextView tvTopToastTitle;
    private TextView tvTopToastMessage;
    private final Runnable hideTopToastRunnable = this::hideTopToastAnimated;

    private AlertDialog storageSetupDialog;
    private PropertyCategory pendingSubmitCategory;

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
                        refreshStorageBannerState();
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storageManager = new SafStorageManager(this);

        bindViews();
        wireInteractions();

        if (!storageManager.hasValidStorageAccess()) {
            refreshStorageBannerState();
            showStorageSetupDialog(true);
        } else {
            refreshStorageBannerState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStorageBannerState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(hideTopToastRunnable);
        if (storageSetupDialog != null && storageSetupDialog.isShowing()) {
            storageSetupDialog.dismiss();
        }
        ioExecutor.shutdown();
    }

    private void bindViews() {
        ImageButton btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> showStorageSetupDialog(false));

        storageWarningBanner = findViewById(R.id.storageWarningBanner);
        tvStorageWarningText = findViewById(R.id.tvStorageWarningText);
        btnBannerSelectFolder = findViewById(R.id.btnBannerSelectFolder);

        topToastCard = findViewById(R.id.topToastCard);
        ivTopToastIcon = findViewById(R.id.ivTopToastIcon);
        tvTopToastTitle = findViewById(R.id.tvTopToastTitle);
        tvTopToastMessage = findViewById(R.id.tvTopToastMessage);
        topToastCard.setOnClickListener(v -> {
            mainHandler.removeCallbacks(hideTopToastRunnable);
            hideTopToastAnimated();
        });

        etHouseMobile = findViewById(R.id.etHouseMobile);
        etHouseDetails = findViewById(R.id.etHouseDetails);
        btnSubmitHouse = findViewById(R.id.btnSubmitHouse);

        etLandMobile = findViewById(R.id.etLandMobile);
        etLandDetails = findViewById(R.id.etLandDetails);
        btnSubmitLand = findViewById(R.id.btnSubmitLand);
    }

    private void wireInteractions() {
        btnBannerSelectFolder.setOnClickListener(v -> launchDirectoryPicker());

        btnSubmitHouse.setOnClickListener(v -> onHouseSubmitClicked());
        btnSubmitLand.setOnClickListener(v -> onLandSubmitClicked());
    }

    private void onHouseSubmitClicked() {
        long nowElapsed = SystemClock.elapsedRealtime();
        if (nowElapsed - lastHouseSubmitElapsedMs < SUBMIT_DEBOUNCE_MS) {
            return;
        }
        if (!validateInputs(etHouseMobile, etHouseDetails)) {
            return;
        }

        if (!storageManager.hasValidStorageAccess()) {
            pendingSubmitCategory = PropertyCategory.HOUSE_FOR_RENT;
            refreshStorageBannerState();
            showStorageSetupDialog(true);
            return;
        }

        if (!isSubmittingHouse.compareAndSet(false, true)) {
            return;
        }
        lastHouseSubmitElapsedMs = nowElapsed;

        final String mobile = etHouseMobile.getText().toString().trim();
        final String details = etHouseDetails.getText().toString().trim();
        setHouseSubmittingUi(true);

        submitRecordAsync(PropertyCategory.HOUSE_FOR_RENT, mobile, details);
    }

    private void onLandSubmitClicked() {
        long nowElapsed = SystemClock.elapsedRealtime();
        if (nowElapsed - lastLandSubmitElapsedMs < SUBMIT_DEBOUNCE_MS) {
            return;
        }
        if (!validateInputs(etLandMobile, etLandDetails)) {
            return;
        }

        if (!storageManager.hasValidStorageAccess()) {
            pendingSubmitCategory = PropertyCategory.LAND_FOR_SALE;
            refreshStorageBannerState();
            showStorageSetupDialog(true);
            return;
        }

        if (!isSubmittingLand.compareAndSet(false, true)) {
            return;
        }
        lastLandSubmitElapsedMs = nowElapsed;

        final String mobile = etLandMobile.getText().toString().trim();
        final String details = etLandDetails.getText().toString().trim();
        setLandSubmittingUi(true);

        submitRecordAsync(PropertyCategory.LAND_FOR_SALE, mobile, details);
    }

    private void submitRecordAsync(
            @NonNull PropertyCategory category,
            @NonNull String mobile,
            @NonNull String details
    ) {
        ioExecutor.execute(() -> {
            StorageResult result = storageManager.savePropertyRecord(category, mobile, details);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (category == PropertyCategory.HOUSE_FOR_RENT) {
                    isSubmittingHouse.set(false);
                    setHouseSubmittingUi(false);
                } else {
                    isSubmittingLand.set(false);
                    setLandSubmittingUi(false);
                }
                handleStorageResult(result);
            });
        });
    }

    private void handleStorageResult(@NonNull StorageResult result) {
        if (result.isSuccess()) {
            if (result.getCategory() == PropertyCategory.HOUSE_FOR_RENT) {
                etHouseMobile.setText("");
                etHouseDetails.setText("");
                etHouseMobile.setError(null);
                etHouseDetails.setError(null);
            } else {
                etLandMobile.setText("");
                etLandDetails.setText("");
                etLandMobile.setError(null);
                etLandDetails.setError(null);
            }

            refreshStorageBannerState();

            showTopSuccessToast(
                    result.getCategory(),
                    result.getRecordNumber(),
                    result.getDisplayPath()
            );
        } else {
            refreshStorageBannerState();
            if (result.isPermissionMissing()) {
                pendingSubmitCategory = result.getCategory();
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Folder Access Required")
                        .setMessage(result.getErrorMessage())
                        .setPositiveButton("Select Folder", (d, w) -> launchDirectoryPicker())
                        .setNegativeButton("Cancel", (d, w) -> pendingSubmitCategory = null)
                        .show();
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Could Not Save Record")
                        .setMessage(result.getErrorMessage())
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    private void showTopSuccessToast(
            @NonNull PropertyCategory category,
            int recordNumber,
            @NonNull String displayPath
    ) {
        mainHandler.removeCallbacks(hideTopToastRunnable);

        String recordLabel = String.format(Locale.US, "#%03d", recordNumber);
        tvTopToastTitle.setText(category.getDisplayName() + " " + recordLabel + " Saved");
        tvTopToastMessage.setText(displayPath);

        int tintColor = category == PropertyCategory.HOUSE_FOR_RENT
                ? 0xFF60A5FA
                : 0xFF22C55E;
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

    private boolean validateInputs(@NonNull EditText mobileField, @NonNull EditText detailsField) {
        String mobile = mobileField.getText() == null ? "" : mobileField.getText().toString().trim();
        String details = detailsField.getText() == null ? "" : detailsField.getText().toString().trim();

        boolean valid = true;

        if (TextUtils.isEmpty(mobile)) {
            mobileField.setError("Please enter a mobile number");
            mobileField.requestFocus();
            valid = false;
        } else if (!isValidMobileNumber(mobile)) {
            mobileField.setError("Enter a valid mobile number (at least 6 digits)");
            mobileField.requestFocus();
            valid = false;
        } else {
            mobileField.setError(null);
        }

        if (TextUtils.isEmpty(details)) {
            detailsField.setError("Please enter property details");
            if (valid) {
                detailsField.requestFocus();
            }
            valid = false;
        } else {
            detailsField.setError(null);
        }

        return valid;
    }

    private boolean isValidMobileNumber(@NonNull String mobile) {
        int digitCount = 0;
        for (int i = 0; i < mobile.length(); i++) {
            char c = mobile.charAt(i);
            if (Character.isDigit(c)) {
                digitCount++;
            } else if (c != '+' && c != '-' && c != ' ' && c != '(' && c != ')') {
                return false;
            }
        }
        return digitCount >= 6;
    }

    private void setHouseSubmittingUi(boolean submitting) {
        btnSubmitHouse.setEnabled(!submitting);
        btnSubmitHouse.setText(submitting
                ? getString(R.string.saving_record)
                : getString(R.string.submit_house_for_rent));
    }

    private void setLandSubmittingUi(boolean submitting) {
        btnSubmitLand.setEnabled(!submitting);
        btnSubmitLand.setText(submitting
                ? getString(R.string.saving_record)
                : getString(R.string.submit_land_for_sale));
    }

    private void refreshStorageBannerState() {
        boolean hasAccess = storageManager.hasValidStorageAccess();
        if (hasAccess) {
            storageWarningBanner.setVisibility(View.GONE);
        } else {
            storageWarningBanner.setVisibility(View.VISIBLE);
            tvStorageWarningText.setText(getString(R.string.storage_banner_missing));
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
            String path = storageManager.getSelectedFolderDisplayName();
            tvStatus.setText("Active folder: " + path
                    + "\nSubfolders: House for Rent & Land for Sale");
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
        ioExecutor.execute(() -> {
            boolean ok = storageManager.initializeDirectoryFromTreeUri(treeUri);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                refreshStorageBannerState();
                if (ok) {
                    String displayFolder = storageManager.getSelectedFolderDisplayName();
                    Toast.makeText(
                            this,
                            "Storage ready: " + displayFolder,
                            Toast.LENGTH_SHORT
                    ).show();

                    if (pendingSubmitCategory == PropertyCategory.HOUSE_FOR_RENT) {
                        pendingSubmitCategory = null;
                        onHouseSubmitClicked();
                    } else if (pendingSubmitCategory == PropertyCategory.LAND_FOR_SALE) {
                        pendingSubmitCategory = null;
                        onLandSubmitClicked();
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
