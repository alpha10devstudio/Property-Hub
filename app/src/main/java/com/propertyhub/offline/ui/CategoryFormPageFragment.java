package com.propertyhub.offline.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.propertyhub.offline.R;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyFieldCatalog;
import com.propertyhub.offline.model.PropertyFieldConfig;
import com.propertyhub.offline.model.PropertyFieldInputType;
import com.propertyhub.offline.model.PropertyFieldValue;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.poster.DetailsParagraphParser.StyledSegment;
import com.propertyhub.offline.storage.StorageResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Config-driven property collection page for a single category (House for Rent or Land for Sale).
 * Renders all inputs from {@link PropertyFieldCatalog}, preserves rich B/I spans in memory and TXT,
 * validates required fields and 11-digit BD mobile numbers, and saves TXT before optional JPG generation.
 */
public class CategoryFormPageFragment extends Fragment {

    private static final String ARG_CATEGORY = "arg_category";
    private static final String STATE_FIELDS_JSON = "state_fields_json";
    private static final String STATE_USER_EDITED_IDS = "state_user_edited_ids";
    private static final long SUBMIT_DEBOUNCE_MS = 700L;

    private PropertyCategory category = PropertyCategory.HOUSE_FOR_RENT;
    private MainHostCallbacks host;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isSubmitting = new AtomicBoolean(false);
    private long lastSubmitElapsedMs = 0L;

    private final List<FieldBinding> fieldBindings = new ArrayList<>();
    private final Set<String> activeFieldIds = new HashSet<>();
    private final Set<String> userEditedFieldIds = new HashSet<>();
    private final Map<String, boolean[]> pendingStyleByField = new HashMap<>();
    private LinearLayout fieldsContainer;
    private boolean isHouseTheme = true;
    private MaterialButton btnCategorySubmit;
    private ProgressBar progressSubmit;

    private static final class FieldBinding {
        final PropertyFieldConfig config;
        final EditText editText;
        @Nullable
        final RichTextSpannableCodec.Controller richController;

        FieldBinding(
                @NonNull PropertyFieldConfig config,
                @NonNull EditText editText,
                @Nullable RichTextSpannableCodec.Controller richController
        ) {
            this.config = config;
            this.editText = editText;
            this.richController = richController;
        }
    }

    @NonNull
    public static CategoryFormPageFragment newInstance(@NonNull PropertyCategory category) {
        CategoryFormPageFragment fragment = new CategoryFormPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainHostCallbacks) {
            host = (MainHostCallbacks) context;
        } else if (getParentFragment() instanceof MainHostCallbacks) {
            host = (MainHostCallbacks) getParentFragment();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            String rawCat = getArguments().getString(ARG_CATEGORY);
            if (rawCat != null) {
                try {
                    category = PropertyCategory.valueOf(rawCat);
                } catch (IllegalArgumentException ignored) {
                    category = PropertyCategory.HOUSE_FOR_RENT;
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_category_form_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean isHouse = category == PropertyCategory.HOUSE_FOR_RENT;
        isHouseTheme = isHouse;
        FrameLayout bannerFrame = view.findViewById(R.id.categoryBannerFrame);
        ImageView ivBannerArt = view.findViewById(R.id.ivCategoryBannerArt);
        ImageView ivBannerIcon = view.findViewById(R.id.ivCategoryBannerIcon);
        TextView tvBannerTitle = view.findViewById(R.id.tvCategoryBannerTitle);
        TextView tvBannerSubtitle = view.findViewById(R.id.tvCategoryBannerSubtitle);
        MaterialButton btnCustomizeFields = view.findViewById(R.id.btnCustomizeFields);
        fieldsContainer = view.findViewById(R.id.fieldsContainer);
        btnCategorySubmit = view.findViewById(R.id.btnCategorySubmit);
        progressSubmit = view.findViewById(R.id.progressSubmit);

        if (isHouse) {
            bannerFrame.setBackgroundResource(R.drawable.bg_house_banner);
            ivBannerArt.setImageResource(R.drawable.ic_house_hero_art);
            ivBannerIcon.setImageResource(R.drawable.ic_house_outline);
            tvBannerTitle.setText(R.string.house_for_rent_title);
            tvBannerSubtitle.setText(R.string.house_for_rent_subtitle);
            tvBannerSubtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.header_subtitle));
            btnCategorySubmit.setText(R.string.submit_house_for_rent);
            btnCategorySubmit.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.blue_primary)));
        } else {
            bannerFrame.setBackgroundResource(R.drawable.bg_land_banner);
            ivBannerArt.setImageResource(R.drawable.ic_land_hero_art);
            ivBannerIcon.setImageResource(R.drawable.ic_location_pin);
            tvBannerTitle.setText(R.string.land_for_sale_title);
            tvBannerSubtitle.setText(R.string.land_for_sale_subtitle);
            tvBannerSubtitle.setTextColor(0xFFE3FFE9);
            btnCategorySubmit.setText(R.string.submit_land_for_sale);
            btnCategorySubmit.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.green_primary)));
        }

        Map<String, PropertyFieldValue> restoredMap = new HashMap<>();
        Set<String> restoredIds = new HashSet<>();
        userEditedFieldIds.clear();
        if (savedInstanceState != null) {
            String json = savedInstanceState.getString(STATE_FIELDS_JSON);
            for (PropertyFieldValue fv : PropertyFieldValue.fromJsonArrayString(json)) {
                restoredMap.put(fv.getFieldId(), fv);
                restoredIds.add(fv.getFieldId());
            }
            ArrayList<String> edited = savedInstanceState.getStringArrayList(STATE_USER_EDITED_IDS);
            if (edited != null) {
                userEditedFieldIds.addAll(edited);
            }
        }

        buildDynamicFields(fieldsContainer, isHouse, restoredMap, restoredIds);

        btnCustomizeFields.setOnClickListener(v -> {
            if (host == null) {
                return;
            }
            FieldCustomizationDialogHelper.show(
                    requireContext(),
                    host.getSettingsManager(),
                    category,
                    () -> {
                        if (host != null) {
                            host.notifyFieldsUpdated();
                        } else {
                            refreshFieldsPreservingDrafts();
                        }
                    }
            );
        });

        btnCategorySubmit.setOnClickListener(v -> triggerSubmit());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFieldsPreservingDrafts();
    }

    public void refreshFieldsPreservingDrafts() {
        if (!isAdded() || fieldsContainer == null || host == null) {
            return;
        }
        Map<String, PropertyFieldValue> draftSnapshot = new HashMap<>();
        Set<String> previousIds = new HashSet<>(activeFieldIds);
        for (FieldBinding binding : fieldBindings) {
            if (binding.richController != null) {
                RichTextSpannableCodec.StyleState st = binding.richController.getPendingState();
                pendingStyleByField.put(binding.config.getFieldId(), new boolean[]{st.bold, st.italic});
            }
        }
        for (PropertyFieldValue fv : collectFieldValues()) {
            draftSnapshot.put(fv.getFieldId(), fv);
            previousIds.add(fv.getFieldId());
        }
        buildDynamicFields(fieldsContainer, isHouseTheme, draftSnapshot, previousIds);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!fieldBindings.isEmpty()) {
            outState.putString(STATE_FIELDS_JSON, PropertyFieldValue.toJsonArrayString(collectFieldValues()));
        }
        outState.putStringArrayList(STATE_USER_EDITED_IDS, new ArrayList<>(userEditedFieldIds));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }

    public void triggerSubmit() {
        if (!isAdded() || host == null) {
            return;
        }
        long nowElapsed = SystemClock.elapsedRealtime();
        if (nowElapsed - lastSubmitElapsedMs < SUBMIT_DEBOUNCE_MS) {
            return;
        }
        if (!validateFields()) {
            return;
        }
        if (!host.getStorageManager().hasValidStorageAccess()) {
            host.refreshStorageBanner();
            host.requestDirectoryPicker(category);
            return;
        }
        if (!isSubmitting.compareAndSet(false, true)) {
            return;
        }
        lastSubmitElapsedMs = nowElapsed;

        final List<PropertyFieldValue> collected = collectFieldValues();
        String titleValue = "";
        String mobileValue = "";
        for (PropertyFieldValue fv : collected) {
            if (PropertyFieldCatalog.FIELD_TITLE.equals(fv.getFieldId())) {
                titleValue = fv.getPlainText().trim();
            } else if (PropertyFieldCatalog.FIELD_MOBILE.equals(fv.getFieldId())) {
                mobileValue = fv.getPlainText().trim();
            }
        }
        if (titleValue.isEmpty()) {
            titleValue = host != null
                    ? host.getSettingsManager().getEffectiveDefaultTitle(category)
                    : PropertyFieldCatalog.getDefaultTitle(category);
        }

        final String finalTitle = titleValue;
        final String finalMobile = mobileValue;
        final boolean autoJpgOn = host.getSettingsManager().isAutoJpgEnabled();

        setSubmittingState(true, autoJpgOn
                ? getString(R.string.saving_and_generating)
                : getString(R.string.saving_record));

        ioExecutor.execute(() -> {
            StorageResult result = host.getStorageManager()
                    .saveNewPropertyRecord(category, finalTitle, finalMobile, collected);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                handleTxtSaveResult(result);
            });
        });
    }

    private void buildDynamicFields(
            @NonNull LinearLayout container,
            boolean isHouse,
            @NonNull Map<String, PropertyFieldValue> restoredMap,
            @NonNull Set<String> previouslyRenderedIds
    ) {
        container.removeAllViews();
        fieldBindings.clear();
        activeFieldIds.clear();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<PropertyFieldConfig> configs = host != null
                ? host.getSettingsManager().getEffectiveFields(category)
                : PropertyFieldCatalog.getFieldsForCategory(category);
        int circleBgRes = isHouse ? R.drawable.bg_circle_blue : R.drawable.bg_circle_green;
        int inputBgRes = isHouse ? R.drawable.bg_input_field_blue : R.drawable.bg_input_field_green;
        int iconTint = ContextCompat.getColor(requireContext(), isHouse ? R.color.blue_icon : R.color.green_icon);
        int activeAccent = ContextCompat.getColor(requireContext(), isHouse ? R.color.blue_primary : R.color.green_primary);

        for (int idx = 0; idx < configs.size(); idx++) {
            PropertyFieldConfig cfg = configs.get(idx);
            activeFieldIds.add(cfg.getFieldId());
            View row = inflater.inflate(R.layout.item_dynamic_field, container, false);

            FrameLayout iconBadge = row.findViewById(R.id.fieldIconBadge);
            ImageView ivIcon = row.findViewById(R.id.ivFieldIcon);
            TextView tvLabel = row.findViewById(R.id.tvFieldLabel);
            LinearLayout formatControls = row.findViewById(R.id.layoutFormatControls);
            TextView btnBold = row.findViewById(R.id.btnFormatBold);
            TextView btnItalic = row.findViewById(R.id.btnFormatItalic);
            EditText editText = row.findViewById(R.id.etFieldInput);

            iconBadge.setBackgroundResource(circleBgRes);
            ivIcon.setImageResource(resolveIconRes(cfg));
            ivIcon.setColorFilter(iconTint);

            tvLabel.setText(cfg.isRequired() ? (cfg.getLabel() + " *") : cfg.getLabel());
            editText.setBackgroundResource(inputBgRes);
            editText.setHint(cfg.getPlaceholder());

            configureEditTextInput(editText, cfg, idx == configs.size() - 1);

            boolean isUserEdited = userEditedFieldIds.contains(cfg.getFieldId());
            PropertyFieldValue restored = restoredMap.get(cfg.getFieldId());
            if (isUserEdited && restored != null) {
                if (cfg.supportsFormatting()) {
                    editText.setText(RichTextSpannableCodec.toSpannable(restored.getSegments()));
                } else {
                    editText.setText(restored.getPlainText());
                }
            } else {
                editText.setText(cfg.getDefaultValue());
            }

            final String currentFieldId = cfg.getFieldId();
            RichTextSpannableCodec.Controller controller = null;
            if (cfg.supportsFormatting()) {
                formatControls.setVisibility(View.VISIBLE);
                applyFormatToggleVisual(btnBold, false, activeAccent);
                applyFormatToggleVisual(btnItalic, false, activeAccent);

                controller = RichTextSpannableCodec.attach(editText, state -> {
                    applyFormatToggleVisual(btnBold, state.bold, activeAccent);
                    applyFormatToggleVisual(btnItalic, state.italic, activeAccent);
                });
                controller.setOnUserContentOrStyleEdited(() -> userEditedFieldIds.add(currentFieldId));
                controller.bindFormatButtons(btnBold, btnItalic);

                boolean[] savedPending = pendingStyleByField.get(currentFieldId);
                if (savedPending != null && savedPending.length == 2) {
                    controller.setPendingState(savedPending[0], savedPending[1]);
                }
            } else {
                formatControls.setVisibility(View.GONE);
                editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        userEditedFieldIds.add(currentFieldId);
                    }
                });
            }

            if (idx == configs.size() - 1) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
                lp.bottomMargin = 6;
                row.setLayoutParams(lp);
            }

            container.addView(row);
            fieldBindings.add(new FieldBinding(cfg, editText, controller));
        }
    }

    private void configureEditTextInput(
            @NonNull EditText editText,
            @NonNull PropertyFieldConfig cfg,
            boolean isLast
    ) {
        PropertyFieldInputType type = cfg.getInputType();
        if (type == PropertyFieldInputType.MULTILINE) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            editText.setSingleLine(false);
            editText.setMinLines(3);
            editText.setGravity(Gravity.TOP | Gravity.START);
        } else if (type == PropertyFieldInputType.PHONE) {
            editText.setInputType(InputType.TYPE_CLASS_PHONE);
            editText.setSingleLine(true);
            editText.setMaxLines(1);
            editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            editText.setImeOptions(isLast ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
        } else if (type == PropertyFieldInputType.NUMBER) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            editText.setSingleLine(true);
            editText.setMaxLines(1);
            editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            editText.setImeOptions(isLast ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            editText.setSingleLine(true);
            editText.setMaxLines(1);
            editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            editText.setImeOptions(isLast ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
        }
    }

    private int resolveIconRes(@NonNull PropertyFieldConfig cfg) {
        String id = cfg.getFieldId();
        if (PropertyFieldCatalog.FIELD_MOBILE.equals(id)) {
            return R.drawable.ic_phone;
        }
        if (PropertyFieldCatalog.FIELD_TITLE.equals(id) || "land_price".equals(id)) {
            return R.drawable.ic_tag;
        }
        if ("address".equals(id) || "land_address".equals(id)) {
            return R.drawable.ic_location_pin;
        }
        if ("mouza".equals(id) || "land_area".equals(id)
                || "dag_number".equals(id) || "khatian_number".equals(id)) {
            return R.drawable.ic_layers_plot;
        }
        return R.drawable.ic_document;
    }

    private void applyFormatToggleVisual(@NonNull TextView button, boolean active, int accentColor) {
        GradientDrawable bg = new GradientDrawable();
        float density = getResources().getDisplayMetrics().density;
        bg.setCornerRadius(7f * density);
        if (active) {
            bg.setColor(accentColor);
            bg.setStroke(Math.round(1f * density), accentColor);
            button.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(Math.round(1f * density), 0xFF94A3B8);
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        }
        button.setBackground(bg);
    }

    private boolean validateFields() {
        boolean allValid = true;
        EditText firstInvalid = null;

        for (FieldBinding binding : fieldBindings) {
            String raw = textOf(binding.editText);
            String trimmed = raw.trim();
            PropertyFieldConfig cfg = binding.config;

            if (cfg.isRequired() && trimmed.isEmpty()) {
                binding.editText.setError(cfg.getLabel() + " অবশ্যই লিখুন");
                if (firstInvalid == null) {
                    firstInvalid = binding.editText;
                }
                allValid = false;
            } else if (PropertyFieldCatalog.FIELD_MOBILE.equals(cfg.getFieldId())
                    && !PropertyFieldCatalog.isValidBangladeshiMobile(trimmed)) {
                binding.editText.setError("সঠিক ১১ ডিজিটের মোবাইল নম্বর লিখুন (01XXXXXXXXX)");
                if (firstInvalid == null) {
                    firstInvalid = binding.editText;
                }
                allValid = false;
            } else {
                binding.editText.setError(null);
            }
        }

        if (firstInvalid != null) {
            firstInvalid.requestFocus();
        }
        return allValid;
    }

    @NonNull
    private List<PropertyFieldValue> collectFieldValues() {
        List<PropertyFieldValue> out = new ArrayList<>();
        for (FieldBinding binding : fieldBindings) {
            PropertyFieldConfig cfg = binding.config;
            String rawText = textOf(binding.editText);
            if (cfg.supportsFormatting()) {
                List<StyledSegment> segs = RichTextSpannableCodec.fromSpanned(binding.editText.getText());
                out.add(new PropertyFieldValue(cfg.getFieldId(), cfg.getLabel(), rawText, segs));
            } else {
                out.add(new PropertyFieldValue(cfg.getFieldId(), cfg.getLabel(), rawText));
            }
        }
        return out;
    }

    private void resetFormToDefaults() {
        userEditedFieldIds.clear();
        pendingStyleByField.clear();
        if (isAdded() && fieldsContainer != null && host != null) {
            buildDynamicFields(fieldsContainer, isHouseTheme, Collections.emptyMap(), Collections.emptySet());
            return;
        }
        for (FieldBinding binding : fieldBindings) {
            binding.editText.setError(null);
            binding.editText.setText(binding.config.getDefaultValue());
            if (binding.richController != null) {
                binding.richController.resetPending();
            }
        }
    }

    private void handleTxtSaveResult(@NonNull StorageResult result) {
        if (!result.isSuccess() || result.getSavedRecord() == null) {
            unlockSubmissionState();
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
        resetFormToDefaults();
        host.refreshStorageBanner();
        host.notifyFilesUpdated();

        boolean autoJpgOn = host.getSettingsManager().isAutoJpgEnabled();
        if (!autoJpgOn) {
            unlockSubmissionState();
            host.showTopToast(
                    savedRecord.getRecordId() + " Saved to TXT",
                    result.getDisplayPath() != null ? result.getDisplayPath() : "",
                    savedRecord.getCategory() == PropertyCategory.HOUSE_FOR_RENT
            );
            return;
        }

        generatePosterWithRetrySupport(savedRecord, result.getDisplayPath());
    }

    private void generatePosterWithRetrySupport(
            @NonNull PropertyRecord savedRecord,
            @Nullable String txtDisplayPath
    ) {
        setSubmittingState(true, getString(R.string.saving_and_generating));
        host.getAutoPosterManager().generatePosterAsync(savedRecord, false, posterResult -> {
            if (!isAdded()) {
                return;
            }
            unlockSubmissionState();
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
                        .setMessage("Property data saved successfully to TXT, but poster generation failed. You can retry.\n\nRecord: "
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
        TextView title = view.findViewById(R.id.tvPreviewTitle);
        TextView sub = view.findViewById(R.id.tvPreviewSubtitle);
        ImageView iv = view.findViewById(R.id.ivPreviewPosterFull);
        Button btnClose = view.findViewById(R.id.btnPreviewClose);
        Button btnShare = view.findViewById(R.id.btnPreviewShare);

        title.setText("Max Height Reached (1440×2560px) · " + record.getRecordId());
        sub.setText("TXT saved cleanly. Poster text exceeded the 2560px maximum height even after spacing and font optimization—please review the preview or shorten lengthy content slightly.");
        btnShare.setVisibility(View.GONE);
        iv.setImageURI(posterUri);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setSubmittingState(boolean submitting, @NonNull String labelText) {
        if (btnCategorySubmit == null) {
            return;
        }
        btnCategorySubmit.setEnabled(!submitting);
        btnCategorySubmit.setText(labelText);
        if (progressSubmit != null) {
            progressSubmit.setVisibility(submitting ? View.VISIBLE : View.GONE);
        }
    }

    private void unlockSubmissionState() {
        isSubmitting.set(false);
        setSubmittingState(false, getString(
                category == PropertyCategory.HOUSE_FOR_RENT
                        ? R.string.submit_house_for_rent
                        : R.string.submit_land_for_sale));
    }

    @NonNull
    private String textOf(@NonNull EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }
}
