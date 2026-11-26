package com.propertyhub.offline.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.propertyhub.offline.BuildConfig;
import com.propertyhub.offline.R;
import com.propertyhub.offline.model.AppThemeMode;
import com.propertyhub.offline.storage.SettingsManager;
import com.propertyhub.offline.storage.StorageManager;

public class SettingsFragment extends Fragment {

    private MainHostCallbacks host;

    private TextView tvStorageFolderName;
    private TextView tvStorageFolderPath;
    private MaterialSwitch switchAutoJpg;
    private TextView tvCurrentThemeLabel;
    private TextView tvCurrentLanguage;
    private TextView tvAboutVersion;

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
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBackArrow = view.findViewById(R.id.btnSettingsBackArrow);
        View cardStorage = view.findViewById(R.id.cardStorageLocation);
        View cardTheme = view.findViewById(R.id.cardAppTheme);
        View cardLanguage = view.findViewById(R.id.cardLanguage);
        Button btnBackHome = view.findViewById(R.id.btnBackToHome);

        tvStorageFolderName = view.findViewById(R.id.tvStorageFolderName);
        tvStorageFolderPath = view.findViewById(R.id.tvStorageFolderPath);
        switchAutoJpg = view.findViewById(R.id.switchAutoJpg);
        tvCurrentThemeLabel = view.findViewById(R.id.tvCurrentThemeLabel);
        tvCurrentLanguage = view.findViewById(R.id.tvCurrentLanguage);
        tvAboutVersion = view.findViewById(R.id.tvAboutVersion);

        tvAboutVersion.setText("Version " + BuildConfig.VERSION_NAME);

        refreshUi();

        btnBackArrow.setOnClickListener(v -> {
            if (host != null) {
                host.openHome();
            }
        });

        btnBackHome.setOnClickListener(v -> {
            if (host != null) {
                host.openHome();
            }
        });

        cardStorage.setOnClickListener(v -> onChangeStorageClicked());

        switchAutoJpg.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (host != null && buttonView.isPressed()) {
                host.getSettingsManager().setAutoJpgEnabled(isChecked);
            }
        });

        cardTheme.setOnClickListener(v -> showThemeSelectionDialog());
        cardLanguage.setOnClickListener(v -> showLanguageDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
    }

    public void refreshUi() {
        if (!isAdded() || host == null || tvStorageFolderPath == null) {
            return;
        }
        StorageManager storage = host.getStorageManager();
        SettingsManager settings = host.getSettingsManager();

        if (storage.hasValidStorageAccess()) {
            tvStorageFolderName.setText(storage.getSelectedFolderShortName());
            String path = storage.getSelectedFolderDisplayPath();
            tvStorageFolderPath.setText(path != null ? path : "/Property Hub");
        } else {
            tvStorageFolderName.setText("Property Hub");
            tvStorageFolderPath.setText(getString(R.string.settings_storage_not_set));
        }

        switchAutoJpg.setChecked(settings.isAutoJpgEnabled());
        tvCurrentThemeLabel.setText(settings.getThemeMode().getLabel());
        tvCurrentLanguage.setText(settings.getLanguageDisplayName());
    }

    private void onChangeStorageClicked() {
        if (host == null) {
            return;
        }
        if (host.getStorageManager().hasValidStorageAccess()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Change Storage Location?")
                    .setMessage("Previously saved files in your current folder will NOT be deleted or moved. Future records, posters, and ZIP exports will be saved to the newly selected directory.")
                    .setPositiveButton("Choose New Folder",
                            (d, w) -> host.requestDirectoryPicker(null))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            host.requestDirectoryPicker(null);
        }
    }

    private void showThemeSelectionDialog() {
        if (host == null) {
            return;
        }
        AppThemeMode[] modes = AppThemeMode.values();
        String[] labels = new String[modes.length];
        AppThemeMode current = host.getSettingsManager().getThemeMode();
        int checkedIndex = 0;

        for (int i = 0; i < modes.length; i++) {
            labels[i] = modes[i].getLabel();
            if (modes[i] == current) {
                checkedIndex = i;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("App Theme")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    AppThemeMode selected = modes[which];
                    dialog.dismiss();
                    if (selected != current) {
                        host.applyThemeMode(selected);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLanguageDialog() {
        String[] languages = new String[]{"English"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Language")
                .setSingleChoiceItems(languages, 0, (dialog, which) -> {
                    if (host != null) {
                        host.getSettingsManager().setLanguageCode("en");
                        refreshUi();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Close", null)
                .show();
    }
}
