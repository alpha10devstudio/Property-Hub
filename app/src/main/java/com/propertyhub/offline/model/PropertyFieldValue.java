package com.propertyhub.offline.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.poster.DetailsParagraphParser.StyledSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the value and rich inline formatting runs (Bold / Italic) for a single property field.
 * Includes a dependency-free JSON codec so host JVM unit tests and Android runtime behave identically.
 */
public final class PropertyFieldValue {

    private final String fieldId;
    private final String label;
    private final String plainText;
    private final List<StyledSegment> segments;

    public PropertyFieldValue(
            @NonNull String fieldId,
            @NonNull String label,
            @NonNull String plainText,
            @Nullable List<StyledSegment> segments
    ) {
        this.fieldId = fieldId;
        this.label = label;
        this.plainText = plainText;
        if (segments == null || segments.isEmpty()) {
            this.segments = plainText.isEmpty()
                    ? Collections.emptyList()
                    : Collections.singletonList(new StyledSegment(plainText, false, false));
        } else {
            this.segments = Collections.unmodifiableList(mergeSegments(segments));
        }
    }

    public PropertyFieldValue(
            @NonNull String fieldId,
            @NonNull String label,
            @NonNull String plainText
    ) {
        this(fieldId, label, plainText, null);
    }

    @NonNull
    public String getFieldId() {
        return fieldId;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    @NonNull
    public String getPlainText() {
        return plainText;
    }

    @NonNull
    public List<StyledSegment> getSegments() {
        return segments;
    }

    public boolean isEmpty() {
        return plainText.trim().isEmpty();
    }

    @NonNull
    public static List<StyledSegment> mergeSegments(@NonNull List<StyledSegment> input) {
        List<StyledSegment> merged = new ArrayList<>();
        for (StyledSegment seg : input) {
            if (seg == null || seg.getText().isEmpty()) {
                continue;
            }
            if (!merged.isEmpty()) {
                StyledSegment prev = merged.get(merged.size() - 1);
                if (prev.isBold() == seg.isBold() && prev.isItalic() == seg.isItalic()) {
                    merged.set(merged.size() - 1,
                            new StyledSegment(prev.getText() + seg.getText(), prev.isBold(), prev.isItalic()));
                    continue;
                }
            }
            merged.add(seg);
        }
        return merged;
    }

    @NonNull
    public static String toJsonArrayString(@NonNull List<PropertyFieldValue> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            PropertyFieldValue fv = values.get(i);
            sb.append('{');
            sb.append("\"id\":");
            appendJsonString(sb, fv.fieldId);
            sb.append(",\"label\":");
            appendJsonString(sb, fv.label);
            sb.append(",\"text\":");
            appendJsonString(sb, fv.plainText);
            sb.append(",\"runs\":[");
            List<StyledSegment> segs = fv.segments;
            for (int j = 0; j < segs.size(); j++) {
                if (j > 0) {
                    sb.append(',');
                }
                StyledSegment seg = segs.get(j);
                sb.append("{\"t\":");
                appendJsonString(sb, seg.getText());
                sb.append(",\"b\":").append(seg.isBold() ? "true" : "false");
                sb.append(",\"i\":").append(seg.isItalic() ? "true" : "false");
                sb.append('}');
            }
            sb.append("]}");
        }
        sb.append(']');
        return sb.toString();
    }

    @NonNull
    public static List<PropertyFieldValue> fromJsonArrayString(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonParser parser = new JsonParser(json.trim());
            Object root = parser.parseValue();
            if (!(root instanceof List<?>)) {
                return Collections.emptyList();
            }
            List<?> rawItems = (List<?>) root;
            List<PropertyFieldValue> out = new ArrayList<>();
            for (Object itemObj : rawItems) {
                if (!(itemObj instanceof List<?>)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<KeyVal> obj = (List<KeyVal>) itemObj;
                String id = getString(obj, "id");
                String label = getString(obj, "label");
                String text = getString(obj, "text");
                Object runsObj = getRaw(obj, "runs");
                List<StyledSegment> segments = new ArrayList<>();
                if (runsObj instanceof List<?>) {
                    for (Object rObj : (List<?>) runsObj) {
                        if (!(rObj instanceof List<?>)) {
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        List<KeyVal> runMap = (List<KeyVal>) rObj;
                        String t = getString(runMap, "t");
                        boolean b = getBoolean(runMap, "b");
                        boolean it = getBoolean(runMap, "i");
                        if (!t.isEmpty()) {
                            segments.add(new StyledSegment(t, b, it));
                        }
                    }
                }
                if (!id.isEmpty()) {
                    if (segments.isEmpty() && !text.isEmpty()) {
                        segments.add(new StyledSegment(text, false, false));
                    }
                    out.add(new PropertyFieldValue(id, label, text, segments));
                }
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
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
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                        break;
                }
            }
        }
        sb.append('"');
    }

    private static final class KeyVal {
        final String key;
        final Object value;

        KeyVal(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    @Nullable
    private static Object getRaw(@NonNull List<KeyVal> list, @NonNull String key) {
        for (KeyVal kv : list) {
            if (key.equals(kv.key)) {
                return kv.value;
            }
        }
        return null;
    }

    @NonNull
    private static String getString(@NonNull List<KeyVal> list, @NonNull String key) {
        Object v = getRaw(list, key);
        return v instanceof String ? (String) v : "";
    }

    private static boolean getBoolean(@NonNull List<KeyVal> list, @NonNull String key) {
        Object v = getRaw(list, key);
        return v instanceof Boolean && (Boolean) v;
    }

    private static final class JsonParser {
        private final String src;
        private int pos = 0;

        JsonParser(@NonNull String src) {
            this.src = src;
        }

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
            pos++; // '['
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

        private List<KeyVal> parseObject() {
            pos++; // '{'
            List<KeyVal> kvs = new ArrayList<>();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return kvs;
            }
            while (pos < src.length()) {
                skipWs();
                String key = parseString();
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ':') {
                    pos++;
                }
                Object val = parseValue();
                kvs.add(new KeyVal(key, val));
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
            return kvs;
        }

        private String parseString() {
            pos++; // '"'
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
