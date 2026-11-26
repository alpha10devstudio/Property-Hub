package com.propertyhub.offline.poster;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses dynamic property Details input into clean paragraphs and inline Markdown segments:
 * - Segments enclosed in quotes ("..." or “...”) become individual paragraphs.
 * - Commas and whitespace outside quotes act only as paragraph separators and are stripped.
 * - Commas inside quotes are preserved verbatim.
 * - Inline Markdown formatting (**bold**, __bold__, *italic*, _italic_, ***bold italic***)
 *   is parsed inside every paragraph for styled poster rendering without mutating stored TXT data.
 */
public final class DetailsParagraphParser {

    public static final class StyledSegment {
        private final String text;
        private final boolean bold;
        private final boolean italic;

        public StyledSegment(@NonNull String text, boolean bold, boolean italic) {
            this.text = text;
            this.bold = bold;
            this.italic = italic;
        }

        @NonNull
        public String getText() {
            return text;
        }

        public boolean isBold() {
            return bold;
        }

        public boolean isItalic() {
            return italic;
        }
    }

    private DetailsParagraphParser() {
    }

    @NonNull
    public static List<String> parse(@NonNull String rawDetails) {
        if (rawDetails.trim().isEmpty()) {
            return Collections.singletonList("-");
        }

        String normalized = rawDetails.replace("\r\n", "\n").replace('\r', '\n');
        if (!containsAnyQuote(normalized)) {
            return parsePlainLines(normalized);
        }

        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char expectedClosingQuote = '"';

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);

            if (!inQuotes) {
                if (isOpeningQuote(ch)) {
                    flushUnquotedBuffer(current, paragraphs);
                    inQuotes = true;
                    expectedClosingQuote = ch == '\u201C' ? '\u201D' : '"';
                } else if (ch == ',' || Character.isWhitespace(ch)) {
                    if (ch == ',' || ch == '\n') {
                        flushUnquotedBuffer(current, paragraphs);
                    } else if (current.length() > 0) {
                        current.append(ch);
                    }
                } else {
                    current.append(ch);
                }
            } else {
                if (ch == '\\' && i + 1 < normalized.length() && isAnyQuote(normalized.charAt(i + 1))) {
                    current.append(normalized.charAt(i + 1));
                    i++;
                } else if (isMatchingClosingQuote(ch, expectedClosingQuote)) {
                    String trimmed = trimOuterWhitespace(current.toString());
                    if (!trimmed.isEmpty()) {
                        paragraphs.add(trimmed);
                    }
                    current.setLength(0);
                    inQuotes = false;
                } else {
                    current.append(ch);
                }
            }
        }

        // Preserve any trailing unclosed quote or unquoted text without data loss
        String remainder = trimOuterWhitespace(current.toString());
        if (!remainder.isEmpty()) {
            paragraphs.add(remainder);
        }

        if (paragraphs.isEmpty()) {
            return Collections.singletonList("-");
        }
        return paragraphs;
    }

    @NonNull
    public static List<List<StyledSegment>> parseStyledParagraphs(@NonNull String rawDetails) {
        List<String> rawParagraphs = parse(rawDetails);
        List<List<StyledSegment>> out = new ArrayList<>(rawParagraphs.size());
        for (String paragraph : rawParagraphs) {
            List<StyledSegment> segments = parseInlineFormatting(paragraph);
            if (!segments.isEmpty()) {
                out.add(segments);
            } else {
                out.add(Collections.singletonList(new StyledSegment(paragraph, false, false)));
            }
        }
        if (out.isEmpty()) {
            out.add(Collections.singletonList(new StyledSegment("-", false, false)));
        }
        return out;
    }

    @NonNull
    public static List<StyledSegment> parseInlineFormatting(@NonNull String paragraph) {
        if (paragraph.isEmpty()) {
            return Collections.singletonList(new StyledSegment("", false, false));
        }
        List<StyledSegment> raw = new ArrayList<>();
        parseInlineRecursive(paragraph, 0, paragraph.length(), false, false, raw);
        return mergeAdjacentSegments(raw);
    }

    private static void parseInlineRecursive(
            @NonNull String src,
            int start,
            int end,
            boolean bold,
            boolean italic,
            @NonNull List<StyledSegment> out
    ) {
        StringBuilder plain = new StringBuilder();
        int i = start;

        while (i < end) {
            char ch = src.charAt(i);

            // Preserve backslash escapes for markdown markers safely
            if (ch == '\\' && i + 1 < end) {
                char next = src.charAt(i + 1);
                if (next == '*' || next == '_' || next == '\\') {
                    plain.append(next);
                    i += 2;
                    continue;
                }
            }

            if (ch == '*' || ch == '_') {
                // 1. Check triple delimiter: ***...*** or ___...___
                if (i + 2 < end && src.charAt(i + 1) == ch && src.charAt(i + 2) == ch) {
                    if (i + 3 < end && !Character.isWhitespace(src.charAt(i + 3))) {
                        int closeIdx = findClosingTriple(src, i + 3, end, ch);
                        if (closeIdx > i + 3) {
                            flushPlain(plain, bold, italic, out);
                            parseInlineRecursive(src, i + 3, closeIdx, true, true, out);
                            i = closeIdx + 3;
                            continue;
                        }
                    }
                }

                // 2. Check double delimiter: **...** or __...__
                if (i + 1 < end && src.charAt(i + 1) == ch) {
                    if (i + 2 < end && !Character.isWhitespace(src.charAt(i + 2))) {
                        int closeIdx = findClosingDouble(src, i + 2, end, ch);
                        if (closeIdx > i + 2) {
                            flushPlain(plain, bold, italic, out);
                            parseInlineRecursive(src, i + 2, closeIdx, true, italic, out);
                            i = closeIdx + 2;
                            continue;
                        }
                    }
                }

                // 3. Check single delimiter: *...* or _..._
                if (i + 1 < end && !Character.isWhitespace(src.charAt(i + 1))) {
                    boolean canOpenSingle = ch == '*'
                            || isLeftWordBoundary(src, start, i);
                    if (canOpenSingle) {
                        int closeIdx = findClosingSingle(src, i + 1, end, ch);
                        if (closeIdx > i + 1) {
                            flushPlain(plain, bold, italic, out);
                            parseInlineRecursive(src, i + 1, closeIdx, bold, true, out);
                            i = closeIdx + 1;
                            continue;
                        }
                    }
                }
            }

            plain.append(ch);
            i++;
        }

        flushPlain(plain, bold, italic, out);
    }

    private static int findClosingTriple(@NonNull String src, int from, int end, char marker) {
        for (int j = from; j <= end - 3; j++) {
            if (src.charAt(j) == '\\') {
                j++;
                continue;
            }
            if (src.charAt(j) == marker
                    && src.charAt(j + 1) == marker
                    && src.charAt(j + 2) == marker
                    && !Character.isWhitespace(src.charAt(j - 1))) {
                return j;
            }
        }
        return -1;
    }

    private static int findClosingDouble(@NonNull String src, int from, int end, char marker) {
        for (int j = from; j <= end - 2; j++) {
            if (src.charAt(j) == '\\') {
                j++;
                continue;
            }
            if (src.charAt(j) == marker && src.charAt(j + 1) == marker) {
                // Skip if part of an internal *** run only when next char is also marker unless valid end
                if (!Character.isWhitespace(src.charAt(j - 1))) {
                    return j;
                }
            }
        }
        return -1;
    }

    private static int findClosingSingle(@NonNull String src, int from, int end, char marker) {
        for (int j = from; j < end; j++) {
            if (src.charAt(j) == '\\') {
                j++;
                continue;
            }
            // Jump over inner double markers (**...**) so single * doesn't prematurely split **bold**
            if (j + 1 < end && src.charAt(j) == marker && src.charAt(j + 1) == marker) {
                j++;
                continue;
            }
            if (src.charAt(j) == marker && !Character.isWhitespace(src.charAt(j - 1))) {
                if (marker == '*' || isRightWordBoundary(src, end, j)) {
                    return j;
                }
            }
        }
        return -1;
    }

    private static boolean isLeftWordBoundary(@NonNull String src, int start, int index) {
        if (index <= start) {
            return true;
        }
        char prev = src.charAt(index - 1);
        return !Character.isLetterOrDigit(prev);
    }

    private static boolean isRightWordBoundary(@NonNull String src, int end, int index) {
        if (index + 1 >= end) {
            return true;
        }
        char next = src.charAt(index + 1);
        return !Character.isLetterOrDigit(next);
    }

    private static void flushPlain(
            @NonNull StringBuilder plain,
            boolean bold,
            boolean italic,
            @NonNull List<StyledSegment> out
    ) {
        if (plain.length() > 0) {
            out.add(new StyledSegment(plain.toString(), bold, italic));
            plain.setLength(0);
        }
    }

    @NonNull
    private static List<StyledSegment> mergeAdjacentSegments(@NonNull List<StyledSegment> raw) {
        List<StyledSegment> merged = new ArrayList<>();
        for (StyledSegment seg : raw) {
            if (seg.text.isEmpty()) {
                continue;
            }
            if (!merged.isEmpty()) {
                StyledSegment last = merged.get(merged.size() - 1);
                if (last.bold == seg.bold && last.italic == seg.italic) {
                    merged.set(
                            merged.size() - 1,
                            new StyledSegment(last.text + seg.text, last.bold, last.italic)
                    );
                    continue;
                }
            }
            merged.add(seg);
        }
        return merged;
    }

    private static void flushUnquotedBuffer(
            @NonNull StringBuilder buffer,
            @NonNull List<String> out
    ) {
        if (buffer.length() == 0) {
            return;
        }
        String text = trimOuterWhitespace(buffer.toString());
        buffer.setLength(0);
        if (!text.isEmpty()) {
            out.add(text);
        }
    }

    @NonNull
    private static List<String> parsePlainLines(@NonNull String normalized) {
        String[] lines = normalized.split("\n");
        List<String> paragraphs = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        if (paragraphs.isEmpty()) {
            return Collections.singletonList("-");
        }
        return paragraphs;
    }

    private static boolean containsAnyQuote(@NonNull String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isAnyQuote(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOpeningQuote(char ch) {
        return ch == '"' || ch == '\u201C';
    }

    private static boolean isAnyQuote(char ch) {
        return ch == '"' || ch == '\u201C' || ch == '\u201D';
    }

    private static boolean isMatchingClosingQuote(char ch, char expected) {
        if (expected == '\u201D') {
            return ch == '\u201D' || ch == '"';
        }
        return ch == '"' || ch == '\u201D';
    }

    @NonNull
    private static String trimOuterWhitespace(@NonNull String input) {
        int start = 0;
        int end = input.length();
        while (start < end && Character.isWhitespace(input.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(input.charAt(end - 1))) {
            end--;
        }
        return input.substring(start, end);
    }
}
