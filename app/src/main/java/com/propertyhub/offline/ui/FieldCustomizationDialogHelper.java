package com.propertyhub.offline.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.propertyhub.offline.R;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyFieldCatalog;
import com.propertyhub.offline.model.PropertyFieldConfig;
import com.propertyhub.offline.model.PropertyFieldInputType;
import com.propertyhub.offline.storage.SettingsManager;

import java.util.List;
import java.util.Map;

/**
 * Reusable dialog controller allowing users to:
 * 1) Edit, clear, or reset the default value of any built-in or custom input field per category.
 * 2) Dynamically add or delete custom input fields per category with mandatory Required/Optional choice.
 */
public final class FieldCustomizationDialogHelper {

    private FieldCustomizationDialogHelper() {
    }

    public static void show(
            @NonNull Context context,
            @NonNull SettingsManager settingsManager,
            @NonNull PropertyCategory initialCategory,
            @Nullable Runnable onFieldsChanged
    ) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_manage_fields, null, false);
        MaterialButton btnClose = view.findViewById(R.id.btnManageFieldsClose);
        MaterialButton btnCatHouse = view.findViewById(R.id.btnCatHouse);
        MaterialButton btnCatLand = view.findViewById(R.id.btnCatLand);
        MaterialButton btnAddCustomField = view.findViewById(R.id.btnAddCustomField);
        LinearLayout container = view.findViewById(R.id.manageFieldsContainer);

        final PropertyCategory[] currentCategory = {initialCategory};

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        Runnable[] renderList = new Runnable[1];
        renderList[0] = () -> {
            PropertyCategory cat = currentCategory[0];
            boolean isHouse = cat == PropertyCategory.HOUSE_FOR_RENT;
            styleCategoryButtons(context, btnCatHouse, btnCatLand, isHouse);

            int accentColor = ContextCompat.getColor(
                    context,
                    isHouse ? R.color.blue_primary : R.color.green_primary
            );
            btnAddCustomField.setBackgroundTintList(ColorStateList.valueOf(accentColor));

            container.removeAllViews();
            List<PropertyFieldConfig> fields = settingsManager.getEffectiveFields(cat);
            Map<String, String> overrides = settingsManager.getFieldDefaultOverrides(cat);
            float density = context.getResources().getDisplayMetrics().density;

            for (PropertyFieldConfig cfg : fields) {
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                int pad = Math.round(12 * density);
                card.setPadding(pad, pad, pad, pad);

                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(resolveAttrColor(context, R.attr.phSubtleSurface, 0xFFEFF4FA));
                cardBg.setCornerRadius(12f * density);
                cardBg.setStroke(Math.round(1f * density),
                        resolveAttrColor(context, R.attr.phCardStroke, 0xFFD8E2F2));
                card.setBackground(cardBg);

                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                cardLp.bottomMargin = Math.round(10 * density);
                card.setLayoutParams(cardLp);

                // Top header line: Field label + badges
                LinearLayout titleRow = new LinearLayout(context);
                titleRow.setOrientation(LinearLayout.HORIZONTAL);
                titleRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvLabel = new TextView(context);
                tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                tvLabel.setText(cfg.getLabel()
                        + (cfg.isRequired() ? " (Required)" : " (Optional)"));
                tvLabel.setTextColor(resolveAttrColor(context, R.attr.phTextPrimary, 0xFF0B1F44));
                tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                titleRow.addView(tvLabel);

                TextView tvType = new TextView(context);
                tvType.setText((cfg.isCustom() ? "Custom · " : "") + formatInputType(cfg.getInputType()));
                tvType.setTextColor(resolveAttrColor(context, R.attr.phTextSecondary, 0xFF64748B));
                tvType.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                titleRow.addView(tvType);

                card.addView(titleRow);

                // Default value input
                EditText etDefault = new EditText(context);
                LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                etLp.topMargin = Math.round(8 * density);
                etDefault.setLayoutParams(etLp);
                etDefault.setBackgroundResource(isHouse
                        ? R.drawable.bg_input_field_blue
                        : R.drawable.bg_input_field_green);
                etDefault.setHint("Default value (leave blank for empty)");
                etDefault.setText(cfg.getDefaultValue());
                etDefault.setPadding(
                        Math.round(10 * density),
                        Math.round(8 * density),
                        Math.round(10 * density),
                        Math.round(8 * density)
                );
                etDefault.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                etDefault.setTextColor(resolveAttrColor(context, R.attr.phTextPrimary, 0xFF0B1F44));
                etDefault.setHintTextColor(resolveAttrColor(context, R.attr.phTextSecondary, 0xFF64748B));
                if (cfg.getInputType() == PropertyFieldInputType.MULTILINE) {
                    etDefault.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    etDefault.setSingleLine(false);
                    etDefault.setMaxLines(3);
                } else if (cfg.getInputType() == PropertyFieldInputType.PHONE) {
                    etDefault.setInputType(InputType.TYPE_CLASS_PHONE);
                    etDefault.setSingleLine(true);
                } else if (cfg.getInputType() == PropertyFieldInputType.NUMBER) {
                    etDefault.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    etDefault.setSingleLine(true);
                } else {
                    etDefault.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    etDefault.setSingleLine(true);
                }
                card.addView(etDefault);

                // Action buttons row
                LinearLayout actionsRow = new LinearLayout(context);
                LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                actLp.topMargin = Math.round(6 * density);
                actionsRow.setLayoutParams(actLp);
                actionsRow.setOrientation(LinearLayout.HORIZONTAL);
                actionsRow.setGravity(Gravity.END);

                MaterialButton btnSaveDefault = new MaterialButton(
                        context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btnSaveDefault.setText("Save Default");
                btnSaveDefault.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                btnSaveDefault.setAllCaps(false);
                btnSaveDefault.setTextColor(accentColor);
                btnSaveDefault.setStrokeColor(ColorStateList.valueOf(accentColor));
                btnSaveDefault.setInsetTop(0);
                btnSaveDefault.setInsetBottom(0);
                btnSaveDefault.setOnClickListener(v -> {
                    String val = etDefault.getText() == null ? "" : etDefault.getText().toString();
                    settingsManager.setFieldDefaultOverride(cat, cfg.getFieldId(), val);
                    if (onFieldsChanged != null) {
                        onFieldsChanged.run();
                    }
                    Toast.makeText(context, "Default saved for " + cfg.getLabel(), Toast.LENGTH_SHORT).show();
                    renderList[0].run();
                });
                actionsRow.addView(btnSaveDefault);

                MaterialButton btnClear = new MaterialButton(
                        context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btnClear.setStrokeWidth(0);
                btnClear.setText("Clear");
                btnClear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                btnClear.setAllCaps(false);
                btnClear.setTextColor(resolveAttrColor(context, R.attr.phTextSecondary, 0xFF64748B));
                btnClear.setInsetTop(0);
                btnClear.setInsetBottom(0);
                btnClear.setOnClickListener(v -> {
                    etDefault.setText("");
                    settingsManager.setFieldDefaultOverride(cat, cfg.getFieldId(), "");
                    if (onFieldsChanged != null) {
                        onFieldsChanged.run();
                    }
                    Toast.makeText(context, "Default cleared for " + cfg.getLabel(), Toast.LENGTH_SHORT).show();
                    renderList[0].run();
                });
                actionsRow.addView(btnClear);

                if (!cfg.isCustom() && overrides.containsKey(cfg.getFieldId())) {
                    MaterialButton btnReset = new MaterialButton(
                            context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                    btnReset.setStrokeWidth(0);
                    btnReset.setText("Reset");
                    btnReset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                    btnReset.setAllCaps(false);
                    btnReset.setTextColor(resolveAttrColor(context, R.attr.phTextSecondary, 0xFF64748B));
                    btnReset.setInsetTop(0);
                    btnReset.setInsetBottom(0);
                    btnReset.setOnClickListener(v -> {
                        settingsManager.setFieldDefaultOverride(cat, cfg.getFieldId(), null);
                        if (onFieldsChanged != null) {
                            onFieldsChanged.run();
                        }
                        renderList[0].run();
                    });
                    actionsRow.addView(btnReset);
                }

                if (cfg.isCustom()) {
                    MaterialButton btnDelete = new MaterialButton(
                            context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                    btnDelete.setStrokeWidth(0);
                    btnDelete.setText("Delete");
                    btnDelete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                    btnDelete.setAllCaps(false);
                    btnDelete.setTextColor(ContextCompat.getColor(context, R.color.danger_red));
                    btnDelete.setInsetTop(0);
                    btnDelete.setInsetBottom(0);
                    btnDelete.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                            .setTitle("Remove Custom Field?")
                            .setMessage("Remove \"" + cfg.getLabel()
                                    + "\" from new forms? Existing saved TXT records and posters remain untouched.")
                            .setPositiveButton("Delete", (d, w) -> {
                                settingsManager.removeCustomField(cat, cfg.getFieldId());
                                if (onFieldsChanged != null) {
                                    onFieldsChanged.run();
                                }
                                renderList[0].run();
                            })
                            .setNegativeButton("Cancel", null)
                            .show());
                    actionsRow.addView(btnDelete);
                }

                card.addView(actionsRow);
                container.addView(card);
            }
        };

        btnCatHouse.setOnClickListener(v -> {
            currentCategory[0] = PropertyCategory.HOUSE_FOR_RENT;
            renderList[0].run();
        });
        btnCatLand.setOnClickListener(v -> {
            currentCategory[0] = PropertyCategory.LAND_FOR_SALE;
            renderList[0].run();
        });
        btnAddCustomField.setOnClickListener(v -> showAddCustomFieldDialog(
                context,
                settingsManager,
                currentCategory[0],
                () -> {
                    renderList[0].run();
                    if (onFieldsChanged != null) {
                        onFieldsChanged.run();
                    }
                }
        ));
        btnClose.setOnClickListener(v -> dialog.dismiss());

        renderList[0].run();
        dialog.show();
    }

    private static void showAddCustomFieldDialog(
            @NonNull Context context,
            @NonNull SettingsManager settingsManager,
            @NonNull PropertyCategory category,
            @NonNull Runnable onAdded
    ) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_custom_field, null, false);
        TextView tvCategoryInfo = view.findViewById(R.id.tvAddFieldCategoryInfo);
        EditText etLabel = view.findViewById(R.id.etCustomFieldLabel);
        EditText etDefault = view.findViewById(R.id.etCustomFieldDefault);
        RadioGroup rgType = view.findViewById(R.id.rgCustomFieldType);
        RadioGroup rgRequired = view.findViewById(R.id.rgCustomFieldRequired);
        TextView tvRequiredError = view.findViewById(R.id.tvRequiredChoiceError);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelAddField);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveAddField);

        boolean isHouse = category == PropertyCategory.HOUSE_FOR_RENT;
        tvCategoryInfo.setText("Category: " + (isHouse ? "House for Rent" : "Land for Sale"));
        int accent = ContextCompat.getColor(context, isHouse ? R.color.blue_primary : R.color.green_primary);
        btnSave.setBackgroundTintList(ColorStateList.valueOf(accent));

        // Explicitly ensure NEITHER Required nor Optional is pre-selected by default
        rgRequired.clearCheck();
        rgRequired.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != View.NO_ID) {
                tvRequiredError.setVisibility(View.GONE);
            }
        });

        AlertDialog addDialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> addDialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String label = etLabel.getText() == null ? "" : etLabel.getText().toString().trim();
            if (label.isEmpty()) {
                etLabel.setError("Field name is required");
                etLabel.requestFocus();
                return;
            }
            int checkedReqId = rgRequired.getCheckedRadioButtonId();
            if (checkedReqId != R.id.rbFieldRequired && checkedReqId != R.id.rbFieldOptional) {
                tvRequiredError.setVisibility(View.VISIBLE);
                rgRequired.requestFocus();
                return;
            }
            boolean isRequired = checkedReqId == R.id.rbFieldRequired;
            String defaultVal = etDefault.getText() == null ? "" : etDefault.getText().toString();

            PropertyFieldInputType inputType = PropertyFieldInputType.SINGLE_LINE;
            int checkedTypeId = rgType.getCheckedRadioButtonId();
            if (checkedTypeId == R.id.rbTypeMultiline) {
                inputType = PropertyFieldInputType.MULTILINE;
            } else if (checkedTypeId == R.id.rbTypeNumber) {
                inputType = PropertyFieldInputType.NUMBER;
            } else if (checkedTypeId == R.id.rbTypePhone) {
                inputType = PropertyFieldInputType.PHONE;
            }

            PropertyFieldConfig added = settingsManager.addCustomField(
                    category,
                    label,
                    defaultVal,
                    inputType,
                    isRequired
            );
            addDialog.dismiss();
            Toast.makeText(context, "Added field: " + added.getLabel(), Toast.LENGTH_SHORT).show();
            onAdded.run();
        });

        addDialog.show();
    }

    private static void styleCategoryButtons(
            @NonNull Context context,
            @NonNull MaterialButton btnHouse,
            @NonNull MaterialButton btnLand,
            boolean isHouseSelected
    ) {
        int blue = ContextCompat.getColor(context, R.color.blue_primary);
        int green = ContextCompat.getColor(context, R.color.green_primary);
        int neutralText = resolveAttrColor(context, R.attr.phTextSecondary, 0xFF64748B);
        int neutralBg = resolveAttrColor(context, R.attr.phSubtleSurface, 0xFFEFF4FA);

        if (isHouseSelected) {
            btnHouse.setBackgroundTintList(ColorStateList.valueOf(blue));
            btnHouse.setTextColor(Color.WHITE);
            btnLand.setBackgroundTintList(ColorStateList.valueOf(neutralBg));
            btnLand.setTextColor(neutralText);
        } else {
            btnHouse.setBackgroundTintList(ColorStateList.valueOf(neutralBg));
            btnHouse.setTextColor(neutralText);
            btnLand.setBackgroundTintList(ColorStateList.valueOf(green));
            btnLand.setTextColor(Color.WHITE);
        }
    }

    @NonNull
    private static String formatInputType(@NonNull PropertyFieldInputType type) {
        if (type == PropertyFieldInputType.MULTILINE) {
            return "Multiline";
        }
        if (type == PropertyFieldInputType.NUMBER) {
            return "Number";
        }
        if (type == PropertyFieldInputType.PHONE) {
            return "Phone";
        }
        return "Text";
    }

    private static int resolveAttrColor(@NonNull Context context, @AttrRes int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                return ContextCompat.getColor(context, tv.resourceId);
            }
            return tv.data;
        }
        return fallback;
    }
}
