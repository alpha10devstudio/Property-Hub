package com.propertyhub.offline.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    @NonNull
    public static List<PropertyFieldConfig> resolveEffectiveFields(
            @NonNull PropertyCategory category,
            @Nullable List<PropertyFieldConfig> customFields,
            @Nullable Map<String, String> defaultOverrides
    ) {
        List<PropertyFieldConfig> base = getFieldsForCategory(category);
        List<PropertyFieldConfig> bodyAndTitle = new ArrayList<>();
        PropertyFieldConfig mobileField = null;

        for (PropertyFieldConfig cfg : base) {
            String override = defaultOverrides != null ? defaultOverrides.get(cfg.getFieldId()) : null;
            PropertyFieldConfig resolved = override != null ? cfg.withDefaultValue(override) : cfg;
            if (resolved.isMobileField()) {
                mobileField = resolved;
            } else {
                bodyAndTitle.add(resolved);
            }
        }

        if (customFields != null) {
            List<PropertyFieldConfig> sortedCustom = new ArrayList<>();
            for (PropertyFieldConfig cf : customFields) {
                if (cf != null && cf.getCategory() == category) {
                    String override = defaultOverrides != null ? defaultOverrides.get(cf.getFieldId()) : null;
                    sortedCustom.add(override != null ? cf.withDefaultValue(override) : cf);
                }
            }
            Collections.sort(sortedCustom, Comparator.comparingInt(PropertyFieldConfig::getDisplayOrder));
            bodyAndTitle.addAll(sortedCustom);
        }

        if (mobileField != null) {
            bodyAndTitle.add(mobileField);
        }

        return Collections.unmodifiableList(bodyAndTitle);
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

    @Nullable
    public static PropertyFieldConfig findField(
            @NonNull List<PropertyFieldConfig> effectiveFields,
            @NonNull String fieldId
    ) {
        for (PropertyFieldConfig cfg : effectiveFields) {
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

    @NonNull
    public static String getEffectiveDefaultTitle(
            @NonNull PropertyCategory category,
            @Nullable Map<String, String> defaultOverrides
    ) {
        if (defaultOverrides != null && defaultOverrides.containsKey(FIELD_TITLE)) {
            String custom = defaultOverrides.get(FIELD_TITLE);
            return custom != null ? custom : "";
        }
        return getDefaultTitle(category);
    }

    @NonNull
    public static String generateCustomFieldId(
            @NonNull PropertyCategory category,
            @NonNull String label,
            @NonNull List<PropertyFieldConfig> existingFields
    ) {
        String ascii = label.trim().toLowerCase(java.util.Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (ascii.isEmpty()) {
            ascii = "field";
        } else if (ascii.length() > 20) {
            ascii = ascii.substring(0, 20);
        }
        String prefix = category == PropertyCategory.HOUSE_FOR_RENT ? "custom_h_" : "custom_l_";
        String candidate = prefix + ascii;
        int index = 2;
        while (containsFieldId(existingFields, candidate)) {
            candidate = prefix + ascii + "_" + index;
            index++;
        }
        return candidate;
    }

    private static boolean containsFieldId(
            @NonNull List<PropertyFieldConfig> fields,
            @NonNull String fieldId
    ) {
        for (PropertyFieldConfig cfg : fields) {
            if (fieldId.equals(cfg.getFieldId())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static String serializeCustomFields(@NonNull List<PropertyFieldConfig> fields) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            PropertyFieldConfig cfg = fields.get(i);
            sb.append('{');
            sb.append("\"id\":");
            appendJsonString(sb, cfg.getFieldId());
            sb.append(",\"cat\":");
            appendJsonString(sb, cfg.getCategory().name());
            sb.append(",\"label\":");
            appendJsonString(sb, cfg.getLabel());
            sb.append(",\"def\":");
            appendJsonString(sb, cfg.getDefaultValue());
            sb.append(",\"ph\":");
            appendJsonString(sb, cfg.getPlaceholder());
            sb.append(",\"type\":");
            appendJsonString(sb, cfg.getInputType().name());
            sb.append(",\"req\":").append(cfg.isRequired() ? "true" : "false");
            sb.append(",\"fmt\":").append(cfg.supportsFormatting() ? "true" : "false");
            sb.append(",\"ord\":").append(cfg.getDisplayOrder());
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    @NonNull
    public static List<PropertyFieldConfig> parseCustomFields(
            @Nullable String json,
            @NonNull PropertyCategory fallbackCategory
    ) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Object parsed = new SimpleJsonParser(json.trim()).parseValue();
            if (!(parsed instanceof List<?>)) {
                return Collections.emptyList();
            }
            List<PropertyFieldConfig> out = new ArrayList<>();
            int nextOrder = 100;
            for (Object item : (List<?>) parsed) {
                if (!(item instanceof Map<?, ?>)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> obj = (Map<String, Object>) item;
                String id = strVal(obj.get("id"));
                String label = strVal(obj.get("label")).trim();
                if (id.isEmpty() || label.isEmpty()) {
                    continue;
                }
                PropertyCategory cat = fallbackCategory;
                String rawCat = strVal(obj.get("cat"));
                if (!rawCat.isEmpty()) {
                    try {
                        cat = PropertyCategory.valueOf(rawCat);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                String def = strVal(obj.get("def"));
                String ph = strVal(obj.get("ph"));
                if (ph.isEmpty()) {
                    ph = label + " লিখুন";
                }
                PropertyFieldInputType type = PropertyFieldInputType.SINGLE_LINE;
                String rawType = strVal(obj.get("type"));
                if (!rawType.isEmpty()) {
                    try {
                        type = PropertyFieldInputType.valueOf(rawType);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                boolean req = boolVal(obj.get("req"));
                boolean fmt = obj.containsKey("fmt")
                        ? boolVal(obj.get("fmt"))
                        : (type == PropertyFieldInputType.SINGLE_LINE || type == PropertyFieldInputType.MULTILINE);
                int ord = obj.containsKey("ord") ? intVal(obj.get("ord"), nextOrder) : nextOrder;
                nextOrder = Math.max(nextOrder + 1, ord + 1);

                out.add(new PropertyFieldConfig(
                        id,
                        cat,
                        label,
                        def,
                        ph,
                        type,
                        req,
                        fmt,
                        ord,
                        true,
                        true
                ));
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    @NonNull
    public static String serializeDefaultOverrides(@NonNull Map<String, String> overrides) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendJsonString(sb, entry.getKey());
            sb.append(':');
            appendJsonString(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    @NonNull
    public static Map<String, String> parseDefaultOverrides(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return new java.util.LinkedHashMap<>();
        }
        try {
            Object parsed = new SimpleJsonParser(json.trim()).parseValue();
            if (!(parsed instanceof Map<?, ?>)) {
                return new java.util.LinkedHashMap<>();
            }
            Map<String, String> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) parsed).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    out.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
            return out;
        } catch (Exception ignored) {
            return new java.util.LinkedHashMap<>();
        }
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

    private static void appendJsonString(@NonNull StringBuilder sb, @Nullable String value) {
        sb.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\f':
                        sb.append("\\f");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format(java.util.Locale.US, "\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                        break;
                }
            }
        }
        sb.append('"');
    }

    @NonNull
    private static String strVal(@Nullable Object o) {
        return o instanceof String ? (String) o : "";
    }

    private static boolean boolVal(@Nullable Object o) {
        return o instanceof Boolean && (Boolean) o;
    }

    private static int intVal(@Nullable Object o, int fallback) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        return fallback;
    }

    private static final class SimpleJsonParser {
        private final String src;
        private int pos = 0;

        SimpleJsonParser(@NonNull String src) {
            this.src = src;
        }

        @Nullable
        Object parseValue() {
            skipWs();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end");
            }
            char c = src.charAt(pos);
            if (c == '[') {
                return parseArray();
            }
            if (c == '{') {
                return parseObject();
            }
            if (c == '"') {
                return parseString();
            }
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return parseNumber();
        }

        private List<Object> parseArray() {
            pos++;
            List<Object> list = new ArrayList<>();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < src.length()) {
                list.add(parseValue());
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
            }
            return list;
        }

        private Map<String, Object> parseObject() {
            pos++;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < src.length()) {
                skipWs();
                String key = parseString();
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ':') {
                    pos++;
                }
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWs();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
            }
            return map;
        }

        private String parseString() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 <= src.length()) {
                                String hex = src.substring(pos, pos + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos += 4;
                            }
                            break;
                        default:
                            sb.append(esc);
                            break;
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Double parseNumber() {
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            return Double.parseDouble(src.substring(start, pos));
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
