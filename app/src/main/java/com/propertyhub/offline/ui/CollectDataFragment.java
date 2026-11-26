package com.propertyhub.offline.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.propertyhub.offline.R;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.poster.AutoPosterManager;
import com.propertyhub.offline.storage.StorageResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CollectDataFragment extends Fragment {

    private static final long SUBMIT_DEBOUNCE_MS = 700L;
    private static final String STATE_HOUSE_MOBILE = "state_house_mobile";
    private static final String STATE_HOUSE_DETAILS = "state_house_details";
    private static final String STATE_LAND_MOBILE = "state_land_mobile";
    private static final String STATE_LAND_DETAILS = "state_land_details";

    private MainHostCallbacks host;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AtomicBoolean isSubmittingHouse = new AtomicBoolean(false);
    private final AtomicBoolean isSubmittingLand = new AtomicBoolean(false);
    private long lastHouseSubmitElapsedMs = 0L;
    private long lastLandSubmitElapsedMs = 0L;

    private EditText etHouseMobile;
    private EditText etHouseDetails;
    private Button btnSubmitHouse;

    private EditText etLandMobile;
    private EditText etLandDetails;
    private Button btnSubmitLand;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainHostCallbacks) {
            host = (MainHostCallbacks) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_collect_data, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etHouseMobile = view.findViewById(R.id.etHouseMobile);
        etHouseDetails = view.findViewById(R.id.etHouseDetails);
        btnSubmitHouse = view.findViewById(R.id.btnSubmitHouse);

        etLandMobile = view.findViewById(R.id.etLandMobile);
        etLandDetails = view.findViewById(R.id.etLandDetails);
        btnSubmitLand = view.findViewById(R.id.btnSubmitLand);

        if (savedInstanceState != null) {
            etHouseMobile.setText(savedInstanceState.getString(STATE_HOUSE_MOBILE, ""));
            etHouseDetails.setText(savedInstanceState.getString(STATE_HOUSE_DETAILS, ""));
            etLandMobile.setText(savedInstanceState.getString(STATE_LAND_MOBILE, ""));
            etLandDetails.setText(savedInstanceState.getString(STATE_LAND_DETAILS, ""));
        }

        btnSubmitHouse.setOnClickListener(v -> submitCategory(PropertyCategory.HOUSE_FOR_RENT));
        btnSubmitLand.setOnClickListener(v -> submitCategory(PropertyCategory.LAND_FOR_SALE));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (etHouseMobile != null) {
            outState.putString(STATE_HOUSE_MOBILE, textOf(etHouseMobile));
            outState.putString(STATE_HOUSE_DETAILS, textOf(etHouseDetails));
            outState.putString(STATE_LAND_MOBILE, textOf(etLandMobile));
            outState.putString(STATE_LAND_DETAILS, textOf(etLandDetails));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }

    public void retryPendingSubmission(@NonNull PropertyCategory category) {
        if (!isAdded()) {
            return;
        }
        submitCategory(category);
    }

    private void submitCategory(@NonNull PropertyCategory category) {
        if (host == null) {
            return;
        }

        long nowElapsed = SystemClock.elapsedRealtime();
        boolean isHouse = category == PropertyCategory.HOUSE_FOR_RENT;
        EditText mobileField = isHouse ? etHouseMobile : etLandMobile;
        EditText detailsField = isHouse ? etHouseDetails : etLandDetails;

        if (isHouse) {
            if (nowElapsed - lastHouseSubmitElapsedMs < SUBMIT_DEBOUNCE_MS) {
                return;
            }
        } else {
            if (nowElapsed - lastLandSubmitElapsedMs < SUBMIT_DEBOUNCE_MS) {
                return;
            }
        }

        if (!validateInputs(mobileField, detailsField)) {
            return;
        }

        if (!host.getStorageManager().hasValidStorageAccess()) {
            host.refreshStorageBanner();
            host.requestDirectoryPicker(category);
            return;
        }

        AtomicBoolean lock = isHouse ? isSubmittingHouse : isSubmittingLand;
        if (!lock.compareAndSet(false, true)) {
            return;
        }

        if (isHouse) {
            lastHouseSubmitElapsedMs = nowElapsed;
            setHouseSubmitting(true);
        } else {
            lastLandSubmitElapsedMs = nowElapsed;
            setLandSubmitting(true);
        }

        final String mobile = textOf(mobileField).trim();
        // Preserve exact original user details including line breaks and punctuation
        final String details = textOf(detailsField);

        ioExecutor.execute(() -> {
            StorageResult result = host.getStorageManager()
                    .saveNewPropertyRecord(category, mobile, details);

            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                if (isHouse) {
                    isSubmittingHouse.set(false);
                    setHouseSubmitting(false);
                } else {
                    isSubmittingLand.set(false);
                    setLandSubmitting(false);
                }
                handleTxtSaveResult(result);
            });
        });
    }

    private void handleTxtSaveResult(@NonNull StorageResult result) {
        if (!result.isSuccess() || result.getSavedRecord() == null) {
            host.refreshStorageBanner();
            if (result.isPermissionMissing()) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Folder Access Required")
                        .setMessage(result.getErrorMessage())
                        .setPositiveButton("Select Folder",
                                (d, w) -> host.requestDirectoryPicker(result.getCategory()))
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Could Not Save Record")
                        .setMessage(result.getErrorMessage())
                        .setPositiveButton("OK", null)
                        .show();
            }
            return;
        }

        PropertyRecord savedRecord = result.getSavedRecord();

        // Clear ONLY the submitted form after TXT record is safely stored
        if (savedRecord.getCategory() == PropertyCategory.HOUSE_FOR_RENT) {
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

        host.refreshStorageBanner();
        host.notifyFilesUpdated();

        boolean autoJpgOn = host.getSettingsManager().isAutoJpgEnabled();
        if (!autoJpgOn) {
            host.showTopToast(
                    savedRecord.getRecordId() + " Saved to TXT",
                    result.getDisplayPath() != null ? result.getDisplayPath() : "",
                    savedRecord.getCategory() == PropertyCategory.HOUSE_FOR_RENT
            );
            return;
        }

        // Generate matching 4:3 JPG poster in background
        generatePosterWithRetrySupport(savedRecord, result.getDisplayPath());
    }

    private void generatePosterWithRetrySupport(
            @NonNull PropertyRecord savedRecord,
            @Nullable String txtDisplayPath
    ) {
        host.getAutoPosterManager().generatePosterAsync(savedRecord, false, posterResult -> {
            if (!isAdded()) {
                return;
            }
            host.notifyFilesUpdated();

            if (posterResult.isSuccess()) {
                if (posterResult.hasTextOverflowWarning() && posterResult.getPosterUri() != null) {
                    showPosterOverflowReviewDialog(savedRecord, posterResult.getPosterUri());
                } else {
                    host.showTopToast(
                            "Property saved and poster generated successfully!",
                            savedRecord.getRecordId() + " · " + savedRecord.getPosterFileName(),
                            savedRecord.getCategory() == PropertyCategory.HOUSE_FOR_RENT
                    );
                }
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Poster Generation Failed")
                        .setMessage("Property data saved successfully, but poster generation failed. You can retry.\n\nRecord: "
                                + savedRecord.getRecordId()
                                + (txtDisplayPath != null ? "\nTXT: " + txtDisplayPath : "")
                                + (posterResult.getErrorMessage() != null
                                ? "\nReason: " + posterResult.getErrorMessage() : ""))
                        .setPositiveButton("Retry Poster",
                                (d, w) -> generatePosterWithRetrySupport(savedRecord, txtDisplayPath))
                        .setNegativeButton("Later", null)
                        .show();
            }
        });
    }

    private void showPosterOverflowReviewDialog(
            @NonNull PropertyRecord record,
            @NonNull Uri posterUri
    ) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_poster_preview, null, false);
        android.widget.TextView title = view.findViewById(R.id.tvPreviewTitle);
        android.widget.TextView sub = view.findViewById(R.id.tvPreviewSubtitle);
        ImageView iv = view.findViewById(R.id.ivPreviewPosterFull);
        Button btnClose = view.findViewById(R.id.btnPreviewClose);
        Button btnShare = view.findViewById(R.id.btnPreviewShare);

        title.setText("Long Description Warning · " + record.getRecordId());
        sub.setText("TXT saved cleanly, but the description was very long for 1440×1080. Please review the poster.");
        btnShare.setVisibility(View.GONE);
        iv.setImageURI(posterUri);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private boolean validateInputs(@NonNull EditText mobileField, @NonNull EditText detailsField) {
        String mobile = textOf(mobileField).trim();
        String details = textOf(detailsField);

        boolean valid = true;

        if (TextUtils.isEmpty(mobile)) {
            mobileField.setError("Please enter a mobile number");
            mobileField.requestFocus();
            valid = false;
        } else if (!isValidGeneralPhone(mobile)) {
            mobileField.setError("Enter a valid phone number");
            mobileField.requestFocus();
            valid = false;
        } else {
            mobileField.setError(null);
        }

        if (TextUtils.isEmpty(details.trim())) {
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

    private boolean isValidGeneralPhone(@NonNull String mobile) {
        int digits = 0;
        for (int i = 0; i < mobile.length(); i++) {
            char c = mobile.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
            } else if (c != '+' && c != '-' && c != ' ' && c != '(' && c != ')') {
                return false;
            }
        }
        return digits >= 5;
    }

    private void setHouseSubmitting(boolean submitting) {
        btnSubmitHouse.setEnabled(!submitting);
        btnSubmitHouse.setText(submitting
                ? getString(R.string.saving_record)
                : getString(R.string.submit_house_for_rent));
    }

    private void setLandSubmitting(boolean submitting) {
        btnSubmitLand.setEnabled(!submitting);
        btnSubmitLand.setText(submitting
                ? getString(R.string.saving_record)
                : getString(R.string.submit_land_for_sale));
    }

    @NonNull
    private String textOf(@NonNull EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }
}
