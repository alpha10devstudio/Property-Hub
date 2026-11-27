package com.propertyhub.offline.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.propertyhub.offline.R;
import com.propertyhub.offline.export.ZipExportManager;
import com.propertyhub.offline.model.PosterFileItem;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.model.ZipFileItem;
import com.propertyhub.offline.poster.BulkPosterManager;
import com.propertyhub.offline.poster.TxtImportAnalyzer;
import com.propertyhub.offline.storage.StorageManager;
import com.propertyhub.offline.util.DateTimeProvider;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeneratedFilesFragment extends Fragment {

    private static final int TAB_RECORDS = 0;
    private static final int TAB_BULK = 1;
    private static final int TAB_POSTERS = 2;
    private static final int TAB_ZIP = 3;

    private static final int DATE_MODE_ALL = 0;
    private static final int DATE_MODE_SINGLE = 1;
    private static final int DATE_MODE_RANGE = 2;

    private static final String STATE_SELECTED_TAB = "state_files_tab";

    private MainHostCallbacks host;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DateTimeProvider dateTimeProvider = new DateTimeProvider();

    private ImageButton btnFilesBackArrow;
    private TabLayout filesTabLayout;
    private View panelRecords;
    private View panelBulkGenerator;
    private View panelPosters;
    private View panelZipExports;

    // Panel 1: Records
    private Spinner spRecordsCategory;
    private Spinner spRecordsDateMode;
    private LinearLayout layoutRecordsDateInputs;
    private Button btnRecordsStartDate;
    private Button btnRecordsEndDate;
    private TextView tvRecordsSummary;
    private TextView tvRecordsWarnings;
    private TextView tvRecordsEmpty;
    private RecordsAdapter recordsAdapter;

    private String recordsStartDate;
    private String recordsEndDate;

    // Panel 2: Bulk Generator & TXT Import
    private Button btnImportTxtFiles;
    private View cardImportSummary;
    private TextView tvImportSummaryTitle;
    private TextView tvImportSummaryBody;
    private TextView tvImportConflictWarning;
    private Button btnReviewConflicts;
    private Button btnGenerateMissingImported;

    private Spinner spBulkCategory;
    private Spinner spBulkDateMode;
    private LinearLayout layoutBulkDateInputs;
    private Button btnBulkStartDate;
    private Button btnBulkEndDate;
    private Button btnStartBulk;
    private Button btnCancelBulk;
    private Button btnRetryFailedBulk;
    private Button btnViewGeneratedPosters;
    private Button btnImportMoreTxt;
    private Button btnExportPostersZipFromBulk;
    private TextView tvBulkPercent;
    private LinearProgressIndicator progressBulk;
    private TextView tvBulkStats;
    private TextView tvBulkWarnings;

    private String bulkStartDate;
    private String bulkEndDate;
    private List<PropertyRecord> lastFailedRecords = Collections.emptyList();

    @Nullable
    private List<Uri> lastImportedUris;
    @Nullable
    private BulkPosterManager.ImportPreparationSummary currentImportSummary;
    private final Map<String, PropertyRecord> resolvedConflictChoices = new LinkedHashMap<>();

    // Panel 3: Posters Viewer
    private Spinner spPostersCategory;
    private TextView tvPostersCount;
    private TextView tvPostersEmpty;
    private PostersAdapter postersAdapter;

    // Panel 4: ZIP Exports
    private Spinner spZipCategory;
    private Spinner spZipDateMode;
    private LinearLayout layoutZipDateInputs;
    private Button btnZipStartDate;
    private Button btnZipEndDate;
    private CheckBox cbIncludeTxtInZip;
    private Button btnCreateZip;
    private View cardLatestZipResult;
    private TextView tvLatestZipTitle;
    private TextView tvLatestZipSummary;
    private Button btnOpenLatestZip;
    private Button btnShareLatestZip;
    private TextView tvZipListEmpty;
    private ZipFilesAdapter zipFilesAdapter;

    private String zipStartDate;
    private String zipEndDate;
    @Nullable
    private Uri latestZipUri;
    @Nullable
    private String latestZipName;

    private boolean isInitialSpinnerSetup = true;

    private final ActivityResultLauncher<Intent> txtImportLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            List<Uri> uris = extractSelectedUris(result.getData());
                            if (!uris.isEmpty()) {
                                analyzeImportedTxtUris(uris);
                            }
                        }
                    }
            );

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
        return inflater.inflate(R.layout.fragment_generated_files, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String today = dateTimeProvider.formatLocalDate(dateTimeProvider.now());
        recordsStartDate = today;
        recordsEndDate = today;
        bulkStartDate = today;
        bulkEndDate = today;
        zipStartDate = today;
        zipEndDate = today;

        bindViews(view);
        setupSpinners();
        setupRecyclerViews();
        wireListeners();

        int initialTab = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_SELECTED_TAB, TAB_RECORDS)
                : TAB_RECORDS;
        TabLayout.Tab tab = filesTabLayout.getTabAt(initialTab);
        if (tab != null) {
            tab.select();
        }
        showActivePanel(initialTab);

        isInitialSpinnerSetup = false;
        refreshAllData();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (filesTabLayout != null) {
            outState.putInt(STATE_SELECTED_TAB, filesTabLayout.getSelectedTabPosition());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (postersAdapter != null) {
            postersAdapter.shutdown();
        }
        ioExecutor.shutdown();
    }

    public void refreshAllData() {
        if (!isAdded() || host == null) {
            return;
        }
        loadRecordsAsync();
        loadPostersAsync();
        loadZipExportsAsync();
        if (lastImportedUris != null && !lastImportedUris.isEmpty() && !host.getBulkPosterManager().isRunning()) {
            analyzeImportedTxtUris(lastImportedUris);
        }
    }

    private void bindViews(@NonNull View view) {
        btnFilesBackArrow = view.findViewById(R.id.btnFilesBackArrow);
        filesTabLayout = view.findViewById(R.id.filesTabLayout);
        panelRecords = view.findViewById(R.id.panelRecords);
        panelBulkGenerator = view.findViewById(R.id.panelBulkGenerator);
        panelPosters = view.findViewById(R.id.panelPosters);
        panelZipExports = view.findViewById(R.id.panelZipExports);

        // Panel 1
        spRecordsCategory = view.findViewById(R.id.spRecordsCategory);
        spRecordsDateMode = view.findViewById(R.id.spRecordsDateMode);
        layoutRecordsDateInputs = view.findViewById(R.id.layoutRecordsDateInputs);
        btnRecordsStartDate = view.findViewById(R.id.btnRecordsStartDate);
        btnRecordsEndDate = view.findViewById(R.id.btnRecordsEndDate);
        tvRecordsSummary = view.findViewById(R.id.tvRecordsSummary);
        tvRecordsWarnings = view.findViewById(R.id.tvRecordsWarnings);
        tvRecordsEmpty = view.findViewById(R.id.tvRecordsEmpty);

        // Panel 2
        btnImportTxtFiles = view.findViewById(R.id.btnImportTxtFiles);
        cardImportSummary = view.findViewById(R.id.cardImportSummary);
        tvImportSummaryTitle = view.findViewById(R.id.tvImportSummaryTitle);
        tvImportSummaryBody = view.findViewById(R.id.tvImportSummaryBody);
        tvImportConflictWarning = view.findViewById(R.id.tvImportConflictWarning);
        btnReviewConflicts = view.findViewById(R.id.btnReviewConflicts);
        btnGenerateMissingImported = view.findViewById(R.id.btnGenerateMissingImported);

        spBulkCategory = view.findViewById(R.id.spBulkCategory);
        spBulkDateMode = view.findViewById(R.id.spBulkDateMode);
        layoutBulkDateInputs = view.findViewById(R.id.layoutBulkDateInputs);
        btnBulkStartDate = view.findViewById(R.id.btnBulkStartDate);
        btnBulkEndDate = view.findViewById(R.id.btnBulkEndDate);
        btnStartBulk = view.findViewById(R.id.btnStartBulk);
        btnCancelBulk = view.findViewById(R.id.btnCancelBulk);
        btnRetryFailedBulk = view.findViewById(R.id.btnRetryFailedBulk);
        btnViewGeneratedPosters = view.findViewById(R.id.btnViewGeneratedPosters);
        btnImportMoreTxt = view.findViewById(R.id.btnImportMoreTxt);
        btnExportPostersZipFromBulk = view.findViewById(R.id.btnExportPostersZipFromBulk);
        tvBulkPercent = view.findViewById(R.id.tvBulkPercent);
        progressBulk = view.findViewById(R.id.progressBulk);
        tvBulkStats = view.findViewById(R.id.tvBulkStats);
        tvBulkWarnings = view.findViewById(R.id.tvBulkWarnings);

        // Panel 3
        spPostersCategory = view.findViewById(R.id.spPostersCategory);
        tvPostersCount = view.findViewById(R.id.tvPostersCount);
        tvPostersEmpty = view.findViewById(R.id.tvPostersEmpty);

        // Panel 4
        spZipCategory = view.findViewById(R.id.spZipCategory);
        spZipDateMode = view.findViewById(R.id.spZipDateMode);
        layoutZipDateInputs = view.findViewById(R.id.layoutZipDateInputs);
        btnZipStartDate = view.findViewById(R.id.btnZipStartDate);
        btnZipEndDate = view.findViewById(R.id.btnZipEndDate);
        cbIncludeTxtInZip = view.findViewById(R.id.cbIncludeTxtInZip);
        btnCreateZip = view.findViewById(R.id.btnCreateZip);
        cardLatestZipResult = view.findViewById(R.id.cardLatestZipResult);
        tvLatestZipTitle = view.findViewById(R.id.tvLatestZipTitle);
        tvLatestZipSummary = view.findViewById(R.id.tvLatestZipSummary);
        btnOpenLatestZip = view.findViewById(R.id.btnOpenLatestZip);
        btnShareLatestZip = view.findViewById(R.id.btnShareLatestZip);
        tvZipListEmpty = view.findViewById(R.id.tvZipListEmpty);
    }

    private void setupSpinners() {
        String[] categoryOptions = new String[]{"All Categories", "House for Rent", "Land for Sale"};
        String[] dateModeOptions = new String[]{"All Dates", "Single Date", "Date Range"};

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryOptions);
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, dateModeOptions);

        spRecordsCategory.setAdapter(catAdapter);
        spBulkCategory.setAdapter(catAdapter);
        spPostersCategory.setAdapter(catAdapter);
        spZipCategory.setAdapter(catAdapter);

        spRecordsDateMode.setAdapter(dateAdapter);
        spBulkDateMode.setAdapter(dateAdapter);
        spZipDateMode.setAdapter(dateAdapter);

        updateDateButtonsText(btnRecordsStartDate, btnRecordsEndDate, recordsStartDate, recordsEndDate);
        updateDateButtonsText(btnBulkStartDate, btnBulkEndDate, bulkStartDate, bulkEndDate);
        updateDateButtonsText(btnZipStartDate, btnZipEndDate, zipStartDate, zipEndDate);
    }

    private void setupRecyclerViews() {
        RecyclerView rvRecords = requireView().findViewById(R.id.rvRecords);
        rvRecords.setLayoutManager(new LinearLayoutManager(requireContext()));
        recordsAdapter = new RecordsAdapter(this::showRecordDetailsDialog);
        rvRecords.setAdapter(recordsAdapter);

        RecyclerView rvPosters = requireView().findViewById(R.id.rvPosters);
        rvPosters.setLayoutManager(new LinearLayoutManager(requireContext()));
        postersAdapter = new PostersAdapter(new PostersAdapter.PosterActionListener() {
            @Override
            public void onViewPoster(@NonNull PosterFileItem item) {
                showFullPosterPreview(item.getRecordId(),
                        item.getCategory().getDisplayName() + " · " + item.getRecordDate(),
                        item.getFileUri());
            }

            @Override
            public void onSharePoster(@NonNull PosterFileItem item) {
                shareFileUri(item.getFileUri(), "image/jpeg", "Share Property Poster");
            }

            @Override
            public void onRegeneratePoster(@NonNull PosterFileItem item) {
                confirmAndRegeneratePoster(item);
            }

            @Override
            public void onDeletePoster(@NonNull PosterFileItem item) {
                confirmAndDeletePoster(item);
            }
        });
        rvPosters.setAdapter(postersAdapter);

        RecyclerView rvZipFiles = requireView().findViewById(R.id.rvZipFiles);
        rvZipFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        zipFilesAdapter = new ZipFilesAdapter(new ZipFilesAdapter.ZipActionListener() {
            @Override
            public void onOpenZip(@NonNull ZipFileItem item) {
                openZipUri(item.getFileUri());
            }

            @Override
            public void onShareZip(@NonNull ZipFileItem item) {
                shareFileUri(item.getFileUri(), "application/zip", "Share Property ZIP Archive");
            }
        });
        rvZipFiles.setAdapter(zipFilesAdapter);
    }

    private void wireListeners() {
        btnFilesBackArrow.setOnClickListener(v -> {
            if (host != null) {
                host.openHome();
            }
        });

        filesTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showActivePanel(tab.getPosition());
                if (tab.getPosition() == TAB_RECORDS) {
                    loadRecordsAsync();
                } else if (tab.getPosition() == TAB_POSTERS) {
                    loadPostersAsync();
                } else if (tab.getPosition() == TAB_ZIP) {
                    loadZipExportsAsync();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        AdapterView.OnItemSelectedListener recordsFilterListener = new SimpleItemSelectedListener(() -> {
            if (!isInitialSpinnerSetup) {
                updateDateModeUi(
                        spRecordsDateMode.getSelectedItemPosition(),
                        layoutRecordsDateInputs,
                        btnRecordsStartDate,
                        btnRecordsEndDate
                );
                loadRecordsAsync();
            }
        });
        spRecordsCategory.setOnItemSelectedListener(recordsFilterListener);
        spRecordsDateMode.setOnItemSelectedListener(recordsFilterListener);

        btnRecordsStartDate.setOnClickListener(v -> showDatePicker(recordsStartDate, picked -> {
            recordsStartDate = picked;
            if (recordsEndDate.compareTo(recordsStartDate) < 0) {
                recordsEndDate = picked;
            }
            updateDateButtonsText(btnRecordsStartDate, btnRecordsEndDate, recordsStartDate, recordsEndDate);
            loadRecordsAsync();
        }));

        btnRecordsEndDate.setOnClickListener(v -> showDatePicker(recordsEndDate, picked -> {
            recordsEndDate = picked;
            if (recordsEndDate.compareTo(recordsStartDate) < 0) {
                recordsStartDate = picked;
            }
            updateDateButtonsText(btnRecordsStartDate, btnRecordsEndDate, recordsStartDate, recordsEndDate);
            loadRecordsAsync();
        }));

        // TXT Import & Smart Bulk Generator listeners
        btnImportTxtFiles.setOnClickListener(v -> launchMultiTxtImportPicker());
        btnImportMoreTxt.setOnClickListener(v -> launchMultiTxtImportPicker());
        btnReviewConflicts.setOnClickListener(v -> startConflictReviewFlow(0));
        btnGenerateMissingImported.setOnClickListener(v -> startImportedBulkGeneration());

        spBulkDateMode.setOnItemSelectedListener(new SimpleItemSelectedListener(() ->
                updateDateModeUi(
                        spBulkDateMode.getSelectedItemPosition(),
                        layoutBulkDateInputs,
                        btnBulkStartDate,
                        btnBulkEndDate
                )));

        btnBulkStartDate.setOnClickListener(v -> showDatePicker(bulkStartDate, picked -> {
            bulkStartDate = picked;
            if (bulkEndDate.compareTo(bulkStartDate) < 0) {
                bulkEndDate = picked;
            }
            updateDateButtonsText(btnBulkStartDate, btnBulkEndDate, bulkStartDate, bulkEndDate);
        }));

        btnBulkEndDate.setOnClickListener(v -> showDatePicker(bulkEndDate, picked -> {
            bulkEndDate = picked;
            if (bulkEndDate.compareTo(bulkStartDate) < 0) {
                bulkStartDate = picked;
            }
            updateDateButtonsText(btnBulkStartDate, btnBulkEndDate, bulkStartDate, bulkEndDate);
        }));

        btnStartBulk.setOnClickListener(v -> startBulkGeneration());
        btnCancelBulk.setOnClickListener(v -> {
            if (host != null) {
                host.getBulkPosterManager().requestCancel();
            }
        });
        btnRetryFailedBulk.setOnClickListener(v -> retryFailedBulkGeneration());
        btnViewGeneratedPosters.setOnClickListener(v -> {
            TabLayout.Tab postersTab = filesTabLayout.getTabAt(TAB_POSTERS);
            if (postersTab != null) {
                postersTab.select();
            }
        });
        btnExportPostersZipFromBulk.setOnClickListener(v -> {
            TabLayout.Tab zipTab = filesTabLayout.getTabAt(TAB_ZIP);
            if (zipTab != null) {
                zipTab.select();
            }
        });

        // Posters category filter
        spPostersCategory.setOnItemSelectedListener(new SimpleItemSelectedListener(() -> {
            if (!isInitialSpinnerSetup) {
                loadPostersAsync();
            }
        }));

        // ZIP panel date selectors & creation
        spZipDateMode.setOnItemSelectedListener(new SimpleItemSelectedListener(() ->
                updateDateModeUi(
                        spZipDateMode.getSelectedItemPosition(),
                        layoutZipDateInputs,
                        btnZipStartDate,
                        btnZipEndDate
                )));

        btnZipStartDate.setOnClickListener(v -> showDatePicker(zipStartDate, picked -> {
            zipStartDate = picked;
            if (zipEndDate.compareTo(zipStartDate) < 0) {
                zipEndDate = picked;
            }
            updateDateButtonsText(btnZipStartDate, btnZipEndDate, zipStartDate, zipEndDate);
        }));

        btnZipEndDate.setOnClickListener(v -> showDatePicker(zipEndDate, picked -> {
            zipEndDate = picked;
            if (zipEndDate.compareTo(zipStartDate) < 0) {
                zipStartDate = picked;
            }
            updateDateButtonsText(btnZipStartDate, btnZipEndDate, zipStartDate, zipEndDate);
        }));

        btnCreateZip.setOnClickListener(v -> createZipExport());
        btnOpenLatestZip.setOnClickListener(v -> {
            if (latestZipUri != null) {
                openZipUri(latestZipUri);
            }
        });
        btnShareLatestZip.setOnClickListener(v -> {
            if (latestZipUri != null) {
                shareFileUri(latestZipUri, "application/zip",
                        latestZipName != null ? "Share " + latestZipName : "Share ZIP Archive");
            }
        });
    }

    private void showActivePanel(int tabIndex) {
        panelRecords.setVisibility(tabIndex == TAB_RECORDS ? View.VISIBLE : View.GONE);
        panelBulkGenerator.setVisibility(tabIndex == TAB_BULK ? View.VISIBLE : View.GONE);
        panelPosters.setVisibility(tabIndex == TAB_POSTERS ? View.VISIBLE : View.GONE);
        panelZipExports.setVisibility(tabIndex == TAB_ZIP ? View.VISIBLE : View.GONE);
    }

    private void launchMultiTxtImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            txtImportLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(
                    requireContext(),
                    "No file picker found to open TXT documents.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @NonNull
    private List<Uri> extractSelectedUris(@NonNull Intent data) {
        List<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        return uris;
    }

    private void analyzeImportedTxtUris(@NonNull List<Uri> uris) {
        if (host == null || !isAdded()) {
            return;
        }
        lastImportedUris = new ArrayList<>(uris);
        resolvedConflictChoices.clear();

        cardImportSummary.setVisibility(View.VISIBLE);
        tvImportSummaryTitle.setText("Analyzing " + uris.size() + " TXT file(s)…");
        tvImportSummaryBody.setText("Verifying RECORD_IDs and checking existing JPG posters…");
        tvImportConflictWarning.setVisibility(View.GONE);
        btnReviewConflicts.setVisibility(View.GONE);
        btnGenerateMissingImported.setEnabled(false);

        host.getBulkPosterManager().analyzeImportedTxtUrisAsync(uris, summary -> {
            if (!isAdded()) {
                return;
            }
            currentImportSummary = summary;
            renderImportSummaryCard();
        });
    }

    private void renderImportSummaryCard() {
        if (currentImportSummary == null || !isAdded()) {
            return;
        }
        BulkPosterManager.ImportPreparationSummary summary = currentImportSummary;
        int resolvedCount = resolvedConflictChoices.size();
        int pendingCount = summary.getPendingCount() + resolvedCount;

        tvImportSummaryTitle.setText(String.format(
                Locale.US,
                "TXT Import Ready (%d file%s)",
                summary.totalFiles,
                summary.totalFiles == 1 ? "" : "s"
        ));

        String body = String.format(
                Locale.US,
                "Total Records: %d\n"
                        + "Already Generated: %d\n"
                        + "Pending Generation: %d\n"
                        + "Invalid Records: %d\n"
                        + "Duplicate RECORD_IDs Skipped: %d",
                summary.totalRecordsScanned,
                summary.alreadyGeneratedCount,
                pendingCount,
                summary.invalidRecordsCount,
                summary.duplicateRecordsSkippedCount
        );
        tvImportSummaryBody.setText(body);

        if (!summary.conflicts.isEmpty()) {
            tvImportConflictWarning.setVisibility(View.VISIBLE);
            tvImportConflictWarning.setText(String.format(
                    Locale.US,
                    "Warning: %d RECORD_ID conflict(s) detected with differing content (%d resolved). Original files will not be overwritten.",
                    summary.conflicts.size(),
                    resolvedCount
            ));
            btnReviewConflicts.setVisibility(View.VISIBLE);
        } else {
            tvImportConflictWarning.setVisibility(View.GONE);
            btnReviewConflicts.setVisibility(View.GONE);
        }

        btnGenerateMissingImported.setEnabled(pendingCount > 0 && !host.getBulkPosterManager().isRunning());
        btnGenerateMissingImported.setText(pendingCount > 0
                ? String.format(Locale.US, "Generate Missing Posters (%d)", pendingCount)
                : getString(R.string.generate_missing_posters));
    }

    private void startConflictReviewFlow(int index) {
        if (currentImportSummary == null || index >= currentImportSummary.conflicts.size()) {
            renderImportSummaryCard();
            return;
        }
        TxtImportAnalyzer.RecordConflict conflict = currentImportSummary.conflicts.get(index);
        PropertyRecord first = conflict.getFirstRecord();
        PropertyRecord second = conflict.getConflictingRecord();

        String message = "RECORD_ID: " + first.getRecordId()
                + "\nIssue: " + conflict.getReason()
                + "\n\n--- Version A (" + first.getSourceFileName() + ") ---\n"
                + "Category: " + first.getCategory().getDisplayName() + "\n"
                + "Mobile: " + first.getMobile() + "\n"
                + first.getDetails()
                + "\n\n--- Version B (" + second.getSourceFileName() + ") ---\n"
                + "Category: " + second.getCategory().getDisplayName() + "\n"
                + "Mobile: " + second.getMobile() + "\n"
                + second.getDetails();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(String.format(
                        Locale.US,
                        "Review Conflict (%d/%d)",
                        index + 1,
                        currentImportSummary.conflicts.size()
                ))
                .setMessage(message)
                .setPositiveButton("Use Version A", (d, w) -> {
                    resolvedConflictChoices.put(conflict.getNormalizedKey(), first);
                    startConflictReviewFlow(index + 1);
                })
                .setNeutralButton("Use Version B", (d, w) -> {
                    resolvedConflictChoices.put(conflict.getNormalizedKey(), second);
                    startConflictReviewFlow(index + 1);
                })
                .setNegativeButton("Skip This ID", (d, w) -> {
                    resolvedConflictChoices.remove(conflict.getNormalizedKey());
                    startConflictReviewFlow(index + 1);
                })
                .show();
    }

    private void startImportedBulkGeneration() {
        if (host == null || currentImportSummary == null) {
            return;
        }
        if (!host.getStorageManager().hasValidStorageAccess()) {
            host.requestDirectoryPicker(null);
            return;
        }
        if (host.getBulkPosterManager().isRunning()) {
            return;
        }

        List<PropertyRecord> resolvedList = new ArrayList<>(resolvedConflictChoices.values());
        btnStartBulk.setEnabled(false);
        btnGenerateMissingImported.setEnabled(false);
        btnCancelBulk.setEnabled(true);
        btnRetryFailedBulk.setEnabled(false);
        tvBulkWarnings.setVisibility(View.GONE);

        host.getBulkPosterManager().startImportedBulkGeneration(
                currentImportSummary,
                resolvedList,
                this::onBulkProgressUpdate
        );
    }

    private void loadRecordsAsync() {
        if (host == null || !isAdded()) {
            return;
        }
        PropertyCategory catFilter = selectedCategory(spRecordsCategory);
        String[] range = resolveDateRange(
                spRecordsDateMode.getSelectedItemPosition(), recordsStartDate, recordsEndDate);

        ioExecutor.execute(() -> {
            StorageManager.RecordsQueryResult res = host.getStorageManager()
                    .loadRecords(catFilter, range[0], range[1]);

            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                List<PropertyRecord> list = res.getRecords();
                recordsAdapter.submitList(list);

                if (!res.isStorageAccessible()) {
                    tvRecordsSummary.setText("Storage folder not selected.");
                    tvRecordsEmpty.setVisibility(View.VISIBLE);
                    tvRecordsEmpty.setText("Select a Property Hub folder in Settings to inspect saved TXT records.");
                } else {
                    int generated = 0;
                    for (PropertyRecord r : list) {
                        if (r.isPosterGenerated()) {
                            generated++;
                        }
                    }
                    tvRecordsSummary.setText(String.format(
                            Locale.US,
                            "%d record(s) · %d poster(s) ready · %d pending",
                            list.size(),
                            generated,
                            Math.max(0, list.size() - generated)
                    ));
                    tvRecordsEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    tvRecordsEmpty.setText("No saved property records found for this filter.");
                }

                List<String> warnings = res.getWarnings();
                if (!warnings.isEmpty()) {
                    tvRecordsWarnings.setVisibility(View.VISIBLE);
                    tvRecordsWarnings.setText("File notice (" + warnings.size() + "): "
                            + TextUtils.join(" | ", warnings.subList(0, Math.min(2, warnings.size()))));
                } else {
                    tvRecordsWarnings.setVisibility(View.GONE);
                }
            });
        });
    }

    private void loadPostersAsync() {
        if (host == null || !isAdded()) {
            return;
        }
        PropertyCategory catFilter = selectedCategory(spPostersCategory);
        ioExecutor.execute(() -> {
            List<PosterFileItem> items = host.getStorageManager().listPosterFiles(catFilter);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                postersAdapter.submitList(items);
                tvPostersCount.setText(String.format(Locale.US, "%d poster(s)", items.size()));
                tvPostersEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void loadZipExportsAsync() {
        if (host == null || !isAdded()) {
            return;
        }
        ioExecutor.execute(() -> {
            List<ZipFileItem> items = host.getStorageManager().listZipExports();
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                zipFilesAdapter.submitList(items);
                tvZipListEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showRecordDetailsDialog(@NonNull PropertyRecord record) {
        String message = "Record ID: " + record.getRecordId()
                + "\nDate: " + record.getDate()
                + "\nTime: " + (record.getTime().isEmpty() ? "-" : record.getTime())
                + "\nCategory: " + record.getCategory().getDisplayName()
                + "\nMobile: " + record.getMobile()
                + "\nSource TXT: " + record.getSourceFileName()
                + "\n\nDetails:\n" + record.getDetails();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(record.getRecordId())
                .setMessage(message)
                .setNeutralButton("Delete", (d, w) -> confirmAndDeleteRecord(record))
                .setNegativeButton("Close", null);

        if (record.isPosterGenerated() && record.getPosterUri() != null) {
            builder.setPositiveButton("View Poster", (d, w) -> showFullPosterPreview(
                    record.getRecordId(),
                    record.getCategory().getDisplayName() + " · " + record.getDate(),
                    record.getPosterUri()
            ));
        } else {
            builder.setPositiveButton("Generate Poster", (d, w) -> generateSinglePosterFromRecord(record));
        }
        builder.show();
    }

    private void generateSinglePosterFromRecord(@NonNull PropertyRecord record) {
        if (host == null) {
            return;
        }
        host.getAutoPosterManager().generatePosterAsync(record, false, result -> {
            if (!isAdded()) {
                return;
            }
            if (result.isSuccess()) {
                host.showTopToast(
                        "Poster Generated",
                        record.getPosterFileName(),
                        record.getCategory() == PropertyCategory.HOUSE_FOR_RENT
                );
                refreshAllData();
                if (result.hasTextOverflowWarning() && result.getPosterUri() != null) {
                    showFullPosterPreview(
                            "Max Height Reached (1440×2560px) · " + record.getRecordId(),
                            "Poster text exceeded 2560px max height even after spacing and font optimization. Review preview or shorten text slightly.",
                            result.getPosterUri()
                    );
                }
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Poster Generation Failed")
                        .setMessage(result.getErrorMessage() != null
                                ? result.getErrorMessage()
                                : "Could not generate poster.")
                        .setPositiveButton("Retry", (d, w) -> generateSinglePosterFromRecord(record))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void confirmAndRegeneratePoster(@NonNull PosterFileItem item) {
        if (host == null) {
            return;
        }
        if (item.getLinkedRecord() == null) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Source Record Not Found")
                    .setMessage("Cannot regenerate " + item.getFileName()
                            + " because its matching TXT record was not found in Property Hub/"
                            + item.getCategory().getFolderName() + ".")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Regenerate Poster?")
                .setMessage("This will overwrite " + item.getFileName()
                        + " using the latest saved TXT data for " + item.getRecordId() + ".")
                .setPositiveButton("Regenerate", (d, w) -> {
                    PropertyRecord linked = item.getLinkedRecord();
                    host.getAutoPosterManager().generatePosterAsync(linked, true, res -> {
                        if (!isAdded()) {
                            return;
                        }
                        if (res.isSuccess()) {
                            postersAdapter.evictCacheForUri(item.getFileUri());
                            if (res.getPosterUri() != null) {
                                postersAdapter.evictCacheForUri(res.getPosterUri());
                            }
                            host.showTopToast(
                                    "Poster Regenerated",
                                    item.getFileName(),
                                    item.getCategory() == PropertyCategory.HOUSE_FOR_RENT
                            );
                            loadPostersAsync();
                            loadRecordsAsync();
                            if (res.hasTextOverflowWarning() && res.getPosterUri() != null) {
                                showFullPosterPreview(
                                        "Max Height Reached (1440×2560px) · " + item.getRecordId(),
                                        "Poster text exceeded 2560px max height even after spacing and font optimization. Review preview or shorten text slightly.",
                                        res.getPosterUri()
                                );
                            }
                        } else {
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Regeneration Failed")
                                    .setMessage(res.getErrorMessage())
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmAndDeletePoster(@NonNull PosterFileItem item) {
        if (host == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Poster & Record?")
                .setMessage("This will permanently delete " + item.getFileName()
                        + " and its linked property record (" + item.getRecordId() + ") from "
                        + item.getSourceTxtFile() + ".")
                .setPositiveButton("Delete", (d, w) -> performDelete(
                        item.getCategory(), item.getRecordId(), item.getFileUri()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmAndDeleteRecord(@NonNull PropertyRecord record) {
        if (host == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Property Record?")
                .setMessage("This will permanently delete " + record.getRecordId()
                        + " from " + record.getSourceFileName()
                        + (record.isPosterGenerated()
                        ? " along with its generated poster (" + record.getPosterFileName() + ")."
                        : "."))
                .setPositiveButton("Delete", (d, w) -> performDelete(
                        record.getCategory(), record.getRecordId(), record.getPosterUri()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performDelete(
            @NonNull PropertyCategory category,
            @NonNull String recordId,
            @Nullable Uri posterUri
    ) {
        if (host == null) {
            return;
        }
        ioExecutor.execute(() -> {
            StorageManager.DeleteResult deleteResult =
                    host.getStorageManager().deleteRecordAndPoster(category, recordId);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                if (deleteResult.isSuccess()) {
                    if (posterUri != null && postersAdapter != null) {
                        postersAdapter.evictCacheForUri(posterUri);
                    }
                    String summary = (deleteResult.isDeletedPoster() ? "Poster & " : "")
                            + (deleteResult.isDeletedRecord() ? "TXT record deleted" : "Removed");
                    host.showTopToast(
                            "Deleted " + recordId,
                            summary,
                            category == PropertyCategory.HOUSE_FOR_RENT
                    );
                    refreshAllData();
                } else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete Failed")
                            .setMessage(deleteResult.getErrorMessage() != null
                                    ? deleteResult.getErrorMessage()
                                    : "Could not delete the selected poster and record.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    private void showFullPosterPreview(
            @NonNull String title,
            @NonNull String subtitle,
            @NonNull Uri posterUri
    ) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_poster_preview, null, false);
        TextView tvTitle = view.findViewById(R.id.tvPreviewTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvPreviewSubtitle);
        ImageView ivFull = view.findViewById(R.id.ivPreviewPosterFull);
        Button btnClose = view.findViewById(R.id.btnPreviewClose);
        Button btnShare = view.findViewById(R.id.btnPreviewShare);

        tvTitle.setText(title);
        tvSubtitle.setText(subtitle);

        final Bitmap[] decodedRef = new Bitmap[1];
        ioExecutor.execute(() -> {
            Bitmap bmp = decodeFullPosterSafely(requireContext().getApplicationContext(), posterUri);
            mainHandler.post(() -> {
                if (isAdded() && bmp != null) {
                    decodedRef[0] = bmp;
                    ivFull.setImageBitmap(bmp);
                }
            });
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();

        dialog.setOnDismissListener(d -> {
            ivFull.setImageDrawable(null);
            if (decodedRef[0] != null && !decodedRef[0].isRecycled()) {
                decodedRef[0].recycle();
                decodedRef[0] = null;
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnShare.setOnClickListener(v -> shareFileUri(posterUri, "image/jpeg", "Share Property Poster"));
        dialog.show();
    }

    @Nullable
    private Bitmap decodeFullPosterSafely(@NonNull Context context, @NonNull Uri uri) {
        try {
            BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
            boundsOpts.inJustDecodeBounds = true;
            try (InputStream boundsIn = context.getContentResolver().openInputStream(uri)) {
                if (boundsIn == null) {
                    return null;
                }
                BitmapFactory.decodeStream(boundsIn, null, boundsOpts);
            }

            int sampleSize = 1;
            if (boundsOpts.outHeight > 3600 || boundsOpts.outWidth > 2048) {
                while ((boundsOpts.outHeight / sampleSize) > 3600 || (boundsOpts.outWidth / sampleSize) > 2048) {
                    sampleSize *= 2;
                }
            }

            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return null;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sampleSize;
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (OutOfMemoryError oom) {
            try (InputStream retry = context.getContentResolver().openInputStream(uri)) {
                if (retry == null) {
                    return null;
                }
                BitmapFactory.Options fallback = new BitmapFactory.Options();
                fallback.inSampleSize = 2;
                return BitmapFactory.decodeStream(retry, null, fallback);
            } catch (Exception ignored) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void startBulkGeneration() {
        if (host == null) {
            return;
        }
        if (!host.getStorageManager().hasValidStorageAccess()) {
            host.requestDirectoryPicker(null);
            return;
        }
        if (host.getBulkPosterManager().isRunning()) {
            return;
        }

        PropertyCategory catFilter = selectedCategory(spBulkCategory);
        String[] range = resolveDateRange(
                spBulkDateMode.getSelectedItemPosition(), bulkStartDate, bulkEndDate);

        btnStartBulk.setEnabled(false);
        btnGenerateMissingImported.setEnabled(false);
        btnCancelBulk.setEnabled(true);
        btnRetryFailedBulk.setEnabled(false);
        tvBulkWarnings.setVisibility(View.GONE);

        host.getBulkPosterManager().startBulkGeneration(
                catFilter,
                range[0],
                range[1],
                this::onBulkProgressUpdate
        );
    }

    private void retryFailedBulkGeneration() {
        if (host == null || lastFailedRecords.isEmpty() || host.getBulkPosterManager().isRunning()) {
            return;
        }
        btnStartBulk.setEnabled(false);
        btnGenerateMissingImported.setEnabled(false);
        btnCancelBulk.setEnabled(true);
        btnRetryFailedBulk.setEnabled(false);
        host.getBulkPosterManager().retryFailedRecords(lastFailedRecords, this::onBulkProgressUpdate);
    }

    private void onBulkProgressUpdate(@NonNull BulkPosterManager.BulkProgress progress) {
        if (!isAdded()) {
            return;
        }
        int pct = progress.getProgressPercent();
        tvBulkPercent.setText(pct + "%");
        progressBulk.setProgressCompat(pct, true);

        String statusHeader = progress.running
                ? ("Generating: " + progress.currentRecordId)
                : (progress.cancelled ? "Cancelled by user" : "Batch Complete");

        String stats = statusHeader
                + "\nTotal records scanned: " + progress.totalRecords
                + "\nExisting posters skipped: " + progress.alreadyExistsCount
                + "\nWaiting in queue: " + progress.waitingCount
                + "\nNew posters generated: " + progress.succeededCount
                + "\nFailed generations: " + progress.failedCount
                + "\nInvalid records: " + progress.invalidRecordsCount
                + "\nDuplicate records skipped: " + progress.duplicateRecordsSkippedCount;
        tvBulkStats.setText(stats);

        if (!progress.warnings.isEmpty()) {
            tvBulkWarnings.setVisibility(View.VISIBLE);
            int limit = Math.min(4, progress.warnings.size());
            tvBulkWarnings.setText("Notices (" + progress.warnings.size() + "): "
                    + TextUtils.join(" | ", progress.warnings.subList(0, limit)));
        } else {
            tvBulkWarnings.setVisibility(View.GONE);
        }

        if (!progress.running) {
            btnStartBulk.setEnabled(true);
            btnCancelBulk.setEnabled(false);
            lastFailedRecords = progress.failedRecords;
            btnRetryFailedBulk.setEnabled(!lastFailedRecords.isEmpty());
            refreshAllData();

            if (!progress.cancelled) {
                host.showTopToast(
                        "Bulk Generation Finished",
                        progress.succeededCount + " generated, " + progress.alreadyExistsCount + " skipped",
                        true
                );
            }
        }
    }

    private void createZipExport() {
        if (host == null) {
            return;
        }
        if (!host.getStorageManager().hasValidStorageAccess()) {
            host.requestDirectoryPicker(null);
            return;
        }

        PropertyCategory catFilter = selectedCategory(spZipCategory);
        String[] range = resolveDateRange(
                spZipDateMode.getSelectedItemPosition(), zipStartDate, zipEndDate);
        boolean includeTxt = cbIncludeTxtInZip.isChecked();

        btnCreateZip.setEnabled(false);
        btnCreateZip.setText("Creating ZIP Archive…");

        host.getZipExportManager().exportZipAsync(
                catFilter,
                range[0],
                range[1],
                includeTxt,
                result -> {
                    if (!isAdded()) {
                        return;
                    }
                    btnCreateZip.setEnabled(true);
                    btnCreateZip.setText(R.string.create_zip_archive);
                    handleZipResult(result);
                }
        );
    }

    private void handleZipResult(@NonNull ZipExportManager.ZipExportResult result) {
        if (result.isSuccess() && result.getZipUri() != null && result.getFileName() != null) {
            latestZipUri = result.getZipUri();
            latestZipName = result.getFileName();
            cardLatestZipResult.setVisibility(View.VISIBLE);
            tvLatestZipTitle.setText("Archive Created: " + result.getFileName());
            String summary = result.getImageCount() + " JPG poster(s)"
                    + (result.getTxtCount() > 0 ? " + " + result.getTxtCount() + " TXT file(s)" : "")
                    + " saved & verified in Property Hub/ZIP Exports.";
            tvLatestZipSummary.setText(summary);

            host.showTopToast(
                    "ZIP Archive Ready",
                    result.getFileName(),
                    true
            );
            loadZipExportsAsync();
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("ZIP Export Notice")
                    .setMessage(result.getErrorMessage() != null
                            ? result.getErrorMessage()
                            : "Could not create ZIP archive.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void shareFileUri(@NonNull Uri uri, @NonNull String mimeType, @NonNull String chooserTitle) {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(mimeType);
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri("", uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, chooserTitle));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No compatible share app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openZipUri(@NonNull Uri uri) {
        try {
            Intent open = new Intent(Intent.ACTION_VIEW);
            open.setDataAndType(uri, "application/zip");
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(open, "Open ZIP Archive"));
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "No ZIP viewer installed. Use Share or your File Manager.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Nullable
    private PropertyCategory selectedCategory(@NonNull Spinner spinner) {
        int pos = spinner.getSelectedItemPosition();
        if (pos == 1) {
            return PropertyCategory.HOUSE_FOR_RENT;
        }
        if (pos == 2) {
            return PropertyCategory.LAND_FOR_SALE;
        }
        return null;
    }

    @NonNull
    private String[] resolveDateRange(int mode, @NonNull String startDate, @NonNull String endDate) {
        if (mode == DATE_MODE_SINGLE) {
            return new String[]{startDate, startDate};
        }
        if (mode == DATE_MODE_RANGE) {
            return new String[]{startDate, endDate};
        }
        return new String[]{null, null};
    }

    private void updateDateModeUi(
            int mode,
            @NonNull View container,
            @NonNull Button startBtn,
            @NonNull Button endBtn
    ) {
        if (mode == DATE_MODE_ALL) {
            container.setVisibility(View.GONE);
        } else if (mode == DATE_MODE_SINGLE) {
            container.setVisibility(View.VISIBLE);
            startBtn.setVisibility(View.VISIBLE);
            endBtn.setVisibility(View.GONE);
        } else {
            container.setVisibility(View.VISIBLE);
            startBtn.setVisibility(View.VISIBLE);
            endBtn.setVisibility(View.VISIBLE);
        }
    }

    private void updateDateButtonsText(
            @NonNull Button startBtn,
            @NonNull Button endBtn,
            @NonNull String startDate,
            @NonNull String endDate
    ) {
        startBtn.setText("Date: " + startDate);
        endBtn.setText("To: " + endDate);
    }

    private interface OnDatePicked {
        void onPicked(@NonNull String formattedDate);
    }

    private void showDatePicker(@NonNull String currentYmd, @NonNull OnDatePicked callback) {
        Calendar cal = Calendar.getInstance();
        try {
            String[] parts = currentYmd.split("-");
            if (parts.length == 3) {
                cal.set(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]) - 1,
                        Integer.parseInt(parts[2])
                );
            }
        } catch (Exception ignored) {
        }

        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    String formatted = String.format(
                            Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    callback.onPicked(formatted);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private static final class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        private final Runnable onSelected;

        SimpleItemSelectedListener(@NonNull Runnable onSelected) {
            this.onSelected = onSelected;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            onSelected.run();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}
