package com.propertyhub.offline.poster;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses dynamic property Details input into clean paragraphs:
 * - Segments enclosed in quotes ("..." or “...”) become individual paragraphs.
 * - Commas and whitespace outside quotes act only as paragraph separators and are stripped.
 * - Commas inside quotes are preserved verbatim.
 * - If input has no quoted segments, non-empty lines are treated as paragraphs for backward compatibility.
 */
public final class DetailsParagraphParser {

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
