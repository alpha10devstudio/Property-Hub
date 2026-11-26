package com.propertyhub.offline.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.poster.DetailsParagraphParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PropertyRecord {

    private final String recordId;
    private final String date;
    private final String time;
    private final PropertyCategory category;
    private final String title;
    private final String mobile;
    private final String details;
    private final List<PropertyFieldValue> fieldValues;
    private final String sourceFileName;
    private boolean posterGenerated;
    @Nullable
    private Uri posterUri;

    public PropertyRecord(
            @NonNull String recordId,
            @NonNull String date,
            @NonNull String time,
            @NonNull PropertyCategory category,
            @NonNull String mobile,
            @NonNull String details,
            @NonNull String sourceFileName
    ) {
        this(
                recordId,
                date,
                time,
                category,
                PropertyFieldCatalog.getDefaultTitle(category),
                mobile,
                details,
                Collections.emptyList(),
                sourceFileName
        );
    }

    public PropertyRecord(
            @NonNull String recordId,
            @NonNull String date,
            @NonNull String time,
            @NonNull PropertyCategory category,
            @Nullable String title,
            @NonNull String mobile,
            @NonNull String details,
            @Nullable List<PropertyFieldValue> fieldValues,
            @NonNull String sourceFileName
    ) {
        this.recordId = recordId;
        this.date = date;
        this.time = time;
        this.category = category;
        String resolvedTitle = title != null && !title.trim().isEmpty()
                ? title.trim()
                : resolveTitleFromFields(category, fieldValues);
        this.title = resolvedTitle;
        this.mobile = mobile;
        this.fieldValues = fieldValues == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(fieldValues));
        this.details = !details.trim().isEmpty()
                ? details
                : buildHumanReadableSummary(category, this.fieldValues);
        this.sourceFileName = sourceFileName;
    }

    @NonNull
    public String getRecordId() {
        return recordId;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    @NonNull
    public String getTime() {
        return time;
    }

    @NonNull
    public PropertyCategory getCategory() {
        return category;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getMobile() {
        return mobile;
    }

    @NonNull
    public String getDetails() {
        return details;
    }

    @NonNull
    public List<PropertyFieldValue> getFieldValues() {
        return fieldValues;
    }

    public boolean hasStructuredFields() {
        return !fieldValues.isEmpty();
    }

    @Nullable
    public PropertyFieldValue getFieldValue(@NonNull String fieldId) {
        for (PropertyFieldValue fv : fieldValues) {
            if (fv.getFieldId().equals(fieldId)) {
                return fv;
            }
        }
        return null;
    }

    /**
     * Returns non-empty poster-visible body fields ordered by PropertyFieldCatalog.
     * Falls back cleanly to a single details block for legacy plain-text TXT records.
     */
    @NonNull
    public List<PropertyFieldValue> getPosterVisibleFieldValues() {
        if (!fieldValues.isEmpty()) {
            List<PropertyFieldValue> ordered = new ArrayList<>();
            List<PropertyFieldConfig> catalog = PropertyFieldCatalog.getFieldsForCategory(category);
            for (PropertyFieldConfig cfg : catalog) {
                if (!cfg.isPosterVisible()) {
                    continue;
                }
                PropertyFieldValue fv = getFieldValue(cfg.getFieldId());
                if (fv != null && !fv.isEmpty()) {
                    ordered.add(fv);
                }
            }
            // Also include any custom/future fields saved in record that aren't title/mobile
            for (PropertyFieldValue fv : fieldValues) {
                if (PropertyFieldCatalog.FIELD_TITLE.equals(fv.getFieldId())
                        || PropertyFieldCatalog.FIELD_MOBILE.equals(fv.getFieldId())
                        || fv.isEmpty()) {
                    continue;
                }
                if (PropertyFieldCatalog.findField(category, fv.getFieldId()) == null) {
                    ordered.add(fv);
                }
            }
            if (!ordered.isEmpty()) {
                return ordered;
            }
        }

        // Fallback for legacy TXT records without FIELDS_JSON
        List<DetailsParagraphParser.StyledSegment> flat =
                DetailsParagraphParser.parseInlineFormatting(details.trim());
        String fallbackLabel = category == PropertyCategory.HOUSE_FOR_RENT ? "বর্ণনা" : "জমির তথ্য";
        return Collections.singletonList(new PropertyFieldValue("details", fallbackLabel, details, flat));
    }

    @NonNull
    public String getSourceFileName() {
        return sourceFileName;
    }

    public boolean isPosterGenerated() {
        return posterGenerated;
    }

    public void setPosterGenerated(boolean posterGenerated) {
        this.posterGenerated = posterGenerated;
    }

    @Nullable
    public Uri getPosterUri() {
        return posterUri;
    }

    public void setPosterUri(@Nullable Uri posterUri) {
        this.posterUri = posterUri;
    }

    @NonNull
    public String getPosterFileName() {
        return buildPosterFileName(category, recordId);
    }

    @NonNull
    public static String buildPosterFileName(@NonNull PropertyCategory category, @NonNull String recordId) {
        String upper = recordId.toUpperCase(Locale.US);
        String newPrefix = category.getIdPrefix();
        if (upper.startsWith(newPrefix)) {
            String safeFull = recordId.replaceAll("[^A-Za-z0-9_-]", "_");
            return safeFull + ".jpg";
        }
        String legacyPrefix = category.getLegacyIdPrefix();
        String suffix;
        if (upper.startsWith(legacyPrefix)) {
            suffix = recordId.substring(legacyPrefix.length());
        } else {
            suffix = recordId;
        }
        String safeSuffix = suffix.replaceAll("[^A-Za-z0-9_-]", "_");
        return category.getLegacyPosterPrefix() + safeSuffix + ".jpg";
    }

    @NonNull
    private static String resolveTitleFromFields(
            @NonNull PropertyCategory category,
            @Nullable List<PropertyFieldValue> fieldValues
    ) {
        if (fieldValues != null) {
            for (PropertyFieldValue fv : fieldValues) {
                if (PropertyFieldCatalog.FIELD_TITLE.equals(fv.getFieldId())
                        && !fv.getPlainText().trim().isEmpty()) {
                    return fv.getPlainText().trim();
                }
            }
        }
        return PropertyFieldCatalog.getDefaultTitle(category);
    }

    @NonNull
    public static String buildHumanReadableSummary(
            @NonNull PropertyCategory category,
            @Nullable List<PropertyFieldValue> fieldValues
    ) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PropertyFieldValue fv : fieldValues) {
            if (PropertyFieldCatalog.FIELD_TITLE.equals(fv.getFieldId())
                    || PropertyFieldCatalog.FIELD_MOBILE.equals(fv.getFieldId())
                    || fv.isEmpty()) {
                continue;
            }
            PropertyFieldConfig cfg = PropertyFieldCatalog.findField(category, fv.getFieldId());
            String label = cfg != null ? cfg.getLabel() : fv.getLabel();
            if (sb.length() > 0) {
                sb.append('\n');
            }
            if (!label.trim().isEmpty()) {
                sb.append(label.trim()).append(": ");
            }
            sb.append(fv.getPlainText().trim());
        }
        return sb.toString();
    }
}
