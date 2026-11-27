package com.propertyhub.offline.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Central configuration registry for category input fields.
 * Future fields can be added to HOUSE_FIELDS or LAND_FIELDS without modifying
 * form rendering, persistence, rich-text formatting, or dynamic poster layout code.
 */
public final class PropertyFieldCatalog {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_MOBILE = "mobile";

    public static final String HOUSE_DEFAULT_TITLE = "ফ্ল্যাট / বাসা ভাড়া দেওয়া হবে";
    public static final String LAND_DEFAULT_TITLE = "জমি বিক্রি করা হবে";

    private static final List<PropertyFieldConfig> HOUSE_FIELDS = sortByOrder(Arrays.asList(
            new PropertyFieldConfig(
                    FIELD_TITLE,
                    PropertyCategory.HOUSE_FOR_RENT,
                    "টাইটেল",
                    HOUSE_DEFAULT_TITLE,
                    "পোস্টারের শিরোনাম লিখুন",
                    PropertyFieldInputType.SINGLE_LINE,
                    true,
                    false,
                    1,
                    false
            ),
            new PropertyFieldConfig(
                    "description",
                    PropertyCategory.HOUSE_FOR_RENT,
                    "বর্ণনা",
                    "",
                    "রুম সংখ্যা, সুবিধা, ভাড়া ইত্যাদি লিখুন",
                    PropertyFieldInputType.MULTILINE,
                    false,
                    true,
                    2,
                    true
            ),
            new PropertyFieldConfig(
                    "address",
                    PropertyCategory.HOUSE_FOR_RENT,
                    "ঠিকানা",
                    "",
                    "বাসা / ফ্ল্যাটের সম্পূর্ণ ঠিকানা লিখুন",
                    PropertyFieldInputType.MULTILINE,
                    false,
                    true,
                    3,
                    true
            ),
            new PropertyFieldConfig(
                    FIELD_MOBILE,
                    PropertyCategory.HOUSE_FOR_RENT,
                    "মোবাইল",
                    "",
                    "01XXXXXXXXX (১১ ডিজিট)",
                    PropertyFieldInputType.PHONE,
                    true,
                    false,
                    4,
                    false
            )
    ));

    private static final List<PropertyFieldConfig> LAND_FIELDS = sortByOrder(Arrays.asList(
            new PropertyFieldConfig(
                    FIELD_TITLE,
                    PropertyCategory.LAND_FOR_SALE,
                    "টাইটেল",
                    LAND_DEFAULT_TITLE,
                    "পোস্টারের শিরোনাম লিখুন",
                    PropertyFieldInputType.SINGLE_LINE,
                    true,
                    false,
                    1,
                    false
            ),
            new PropertyFieldConfig(
                    "land_address",
                    PropertyCategory.LAND_FOR_SALE,
                    "জমির ঠিকানা",
                    "",
                    "জমির অবস্থান ও ঠিকানা লিখুন",
                    PropertyFieldInputType.MULTILINE,
                    true,
                    true,
                    2,
                    true
            ),
            new PropertyFieldConfig(
                    "mouza",
                    PropertyCategory.LAND_FOR_SALE,
                    "মৌজা",
                    "",
                    "মৌজার নাম লিখুন",
                    PropertyFieldInputType.SINGLE_LINE,
                    false,
                    true,
                    3,
                    true
            ),
            new PropertyFieldConfig(
                    "land_area",
                    PropertyCategory.LAND_FOR_SALE,
                    "জমির পরিমাণ",
                    "",
                    "যেমন: ৩ শতক, ৫ শতক, ২ কাঠা",
                    PropertyFieldInputType.SINGLE_LINE,
                    false,
                    true,
                    4,
                    true
            ),
            new PropertyFieldConfig(
                    "land_price",
                    PropertyCategory.LAND_FOR_SALE,
                    "জমির মূল্য",
                    "",
                    "মূল্য লিখুন (যেমন: ১৫ লক্ষ টাকা)",
                    PropertyFieldInputType.SINGLE_LINE,
                    false,
                    true,
                    5,
                    true
            ),
            new PropertyFieldConfig(
                    "dag_number",
                    PropertyCategory.LAND_FOR_SALE,
                    "দাগ নম্বর",
                    "",
                    "দাগ নম্বর লিখুন",
                    PropertyFieldInputType.SINGLE_LINE,
                    false,
                    true,
                    6,
                    true
            ),
            new PropertyFieldConfig(
                    "khatian_number",
                    PropertyCategory.LAND_FOR_SALE,
                    "খতিয়ান নম্বর",
                    "",
                    "খতিয়ান নম্বর লিখুন",
                    PropertyFieldInputType.SINGLE_LINE,
                    false,
                    true,
                    7,
                    true
            ),
            new PropertyFieldConfig(
                    "additional_info",
                    PropertyCategory.LAND_FOR_SALE,
                    "অতিরিক্ত তথ্য",
                    "",
                    "রাস্তা, জমির ধরন বা অন্যান্য তথ্য লিখুন",
                    PropertyFieldInputType.MULTILINE,
                    false,
                    true,
                    8,
                    true
            ),
            new PropertyFieldConfig(
                    FIELD_MOBILE,
                    PropertyCategory.LAND_FOR_SALE,
                    "যোগাযোগ নম্বর",
                    "",
                    "01XXXXXXXXX (১১ ডিজিট)",
                    PropertyFieldInputType.PHONE,
                    true,
                    false,
                    9,
                    false
            )
    ));

    private PropertyFieldCatalog() {
    }

    @NonNull
    public static List<PropertyFieldConfig> getFieldsForCategory(@NonNull PropertyCategory category) {
        return category == PropertyCategory.HOUSE_FOR_RENT ? HOUSE_FIELDS : LAND_FIELDS;
    }

    @Nullable
    public static PropertyFieldConfig findField(
            @NonNull PropertyCategory category,
            @NonNull String fieldId
    ) {
        for (PropertyFieldConfig cfg : getFieldsForCategory(category)) {
            if (cfg.getFieldId().equals(fieldId)) {
                return cfg;
            }
        }
        return null;
    }

    @NonNull
    public static String getDefaultTitle(@NonNull PropertyCategory category) {
        return category == PropertyCategory.HOUSE_FOR_RENT
                ? HOUSE_DEFAULT_TITLE
                : LAND_DEFAULT_TITLE;
    }

    /**
     * Validates 11-digit Bangladeshi mobile numbers (01[3-9]XXXXXXXX),
     * supporting optional +88 / 88 country prefix, spaces/dashes, and Bengali digits
     * while preserving the user's exact entered text separately.
     */
    public static boolean isValidBangladeshiMobile(@Nullable String rawInput) {
        if (rawInput == null) {
            return false;
        }
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        StringBuilder digitsOnly = new StringBuilder();
        boolean sawPlus = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digitsOnly.append(ch);
            } else if (ch >= '\u09E6' && ch <= '\u09EF') {
                digitsOnly.append((char) ('0' + (ch - '\u09E6')));
            } else if (ch == '+' && !sawPlus && digitsOnly.length() == 0) {
                sawPlus = true;
            } else if (ch != '-' && ch != ' ' && ch != '(' && ch != ')') {
                return false;
            }
        }
        String normalized = digitsOnly.toString();
        if (normalized.startsWith("880") && normalized.length() == 13) {
            normalized = normalized.substring(2);
        }
        return normalized.matches("^01[3-9]\\d{8}$");
    }

    @NonNull
    private static List<PropertyFieldConfig> sortByOrder(@NonNull List<PropertyFieldConfig> list) {
        List<PropertyFieldConfig> copy = new ArrayList<>(list);
        Collections.sort(copy, Comparator.comparingInt(PropertyFieldConfig::getDisplayOrder));
        return Collections.unmodifiableList(copy);
    }
}
