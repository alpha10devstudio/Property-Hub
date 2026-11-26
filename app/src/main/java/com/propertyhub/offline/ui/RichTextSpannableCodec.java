package com.propertyhub.offline.ui;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.propertyhub.offline.model.PropertyFieldValue;
import com.propertyhub.offline.poster.DetailsParagraphParser.StyledSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles WYSIWYG Bold/Italic spans inside Android EditText and converts
 * cleanly to/from {@link StyledSegment} runs without exposing markdown characters.
 */
public final class RichTextSpannableCodec {

    public static final class StyleState {
        public final boolean bold;
        public final boolean italic;

        public StyleState(boolean bold, boolean italic) {
            this.bold = bold;
            this.italic = italic;
        }
    }

    public interface StyleStateListener {
        void onStyleStateChanged(@NonNull StyleState state);
    }

    private RichTextSpannableCodec() {
    }

    @NonNull
    public static List<StyledSegment> fromSpanned(@Nullable Spanned spanned) {
        if (spanned == null || spanned.length() == 0) {
            return Collections.emptyList();
        }
        String text = spanned.toString();
        int len = text.length();
        boolean[] boldFlags = new boolean[len];
        boolean[] italicFlags = new boolean[len];

        StyleSpan[] spans = spanned.getSpans(0, len, StyleSpan.class);
        if (spans != null) {
            for (StyleSpan span : spans) {
                int s = Math.max(0, spanned.getSpanStart(span));
                int e = Math.min(len, spanned.getSpanEnd(span));
                int style = span.getStyle();
                boolean b = (style & Typeface.BOLD) != 0;
                boolean it = (style & Typeface.ITALIC) != 0;
                for (int i = s; i < e; i++) {
                    if (b) {
                        boldFlags[i] = true;
                    }
                    if (it) {
                        italicFlags[i] = true;
                    }
                }
            }
        }

        List<StyledSegment> segments = new ArrayList<>();
        int runStart = 0;
        for (int i = 1; i <= len; i++) {
            boolean end = (i == len)
                    || (boldFlags[i] != boldFlags[runStart])
                    || (italicFlags[i] != italicFlags[runStart]);
            if (end) {
                segments.add(new StyledSegment(
                        text.substring(runStart, i),
                        boldFlags[runStart],
                        italicFlags[runStart]
                ));
                runStart = i;
            }
        }
        return PropertyFieldValue.mergeSegments(segments);
    }

    @NonNull
    public static SpannableStringBuilder toSpannable(@Nullable List<StyledSegment> segments) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        if (segments == null || segments.isEmpty()) {
            return ssb;
        }
        for (StyledSegment seg : segments) {
            if (seg == null || seg.getText().isEmpty()) {
                continue;
            }
            int start = ssb.length();
            ssb.append(seg.getText());
            int end = ssb.length();
            applyFlags(ssb, start, end, seg.isBold(), seg.isItalic());
        }
        return ssb;
    }

    /**
     * Attaches cursor-typing Bold/Italic support and selection-aware toolbar callbacks
     * for an EditText without disturbing IME composition.
     */
    @NonNull
    public static Controller attach(
            @NonNull EditText editText,
            @NonNull StyleStateListener listener
    ) {
        return new Controller(editText, listener);
    }

    public static final class Controller {
        private final EditText editText;
        private final StyleStateListener listener;
        private boolean pendingBold = false;
        private boolean pendingItalic = false;
        private boolean internalChange = false;

        Controller(@NonNull EditText editText, @NonNull StyleStateListener listener) {
            this.editText = editText;
            this.listener = listener;
            installWatcher();
        }

        public void toggleBold() {
            toggleStyle(Typeface.BOLD);
        }

        public void toggleItalic() {
            toggleStyle(Typeface.ITALIC);
        }

        public void syncFromCursor() {
            Editable text = editText.getText();
            if (text == null) {
                pendingBold = false;
                pendingItalic = false;
                listener.onStyleStateChanged(new StyleState(false, false));
                return;
            }
            int selStart = Math.max(0, editText.getSelectionStart());
            int selEnd = Math.max(0, editText.getSelectionEnd());
            if (selStart != selEnd) {
                StyleState rangeState = inspectRange(text, Math.min(selStart, selEnd), Math.max(selStart, selEnd));
                listener.onStyleStateChanged(rangeState);
                return;
            }
            if (selStart > 0 && selStart <= text.length()) {
                StyleState charBefore = inspectChar(text, selStart - 1);
                pendingBold = charBefore.bold;
                pendingItalic = charBefore.italic;
            } else if (text.length() == 0) {
                // Preserve user's pending toggles on an empty field
            } else {
                pendingBold = false;
                pendingItalic = false;
            }
            listener.onStyleStateChanged(new StyleState(pendingBold, pendingItalic));
        }

        public void resetPending() {
            pendingBold = false;
            pendingItalic = false;
            listener.onStyleStateChanged(new StyleState(false, false));
        }

        private void toggleStyle(int targetStyle) {
            Editable text = editText.getText();
            if (text == null) {
                return;
            }
            int selStart = Math.max(0, editText.getSelectionStart());
            int selEnd = Math.max(0, editText.getSelectionEnd());
            int start = Math.min(selStart, selEnd);
            int end = Math.max(selStart, selEnd);

            if (start == end) {
                if (targetStyle == Typeface.BOLD) {
                    pendingBold = !pendingBold;
                } else {
                    pendingItalic = !pendingItalic;
                }
                listener.onStyleStateChanged(new StyleState(pendingBold, pendingItalic));
                return;
            }

            boolean applyBold = targetStyle == Typeface.BOLD;
            StyleState currentRange = inspectRange(text, start, end);
            boolean enableTarget = applyBold ? !currentRange.bold : !currentRange.italic;

            internalChange = true;
            try {
                updateRangeStyle(text, start, end, applyBold, enableTarget);
            } finally {
                internalChange = false;
            }

            StyleState updated = inspectRange(text, start, end);
            pendingBold = updated.bold;
            pendingItalic = updated.italic;
            listener.onStyleStateChanged(updated);
        }

        private void installWatcher() {
            editText.addTextChangedListener(new TextWatcher() {
                private int insertStart = 0;
                private int insertCount = 0;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    insertStart = start;
                    insertCount = after;
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    insertStart = start;
                    insertCount = count;
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (internalChange || s == null || insertCount <= 0) {
                        return;
                    }
                    int end = Math.min(s.length(), insertStart + insertCount);
                    if (end <= insertStart) {
                        return;
                    }
                    internalChange = true;
                    try {
                        stripOrSplitSpansInRange(s, insertStart, end);
                        applyFlags(s, insertStart, end, pendingBold, pendingItalic);
                    } finally {
                        internalChange = false;
                    }
                }
            });

            editText.setOnClickListener(v -> syncFromCursor());
            editText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    syncFromCursor();
                }
            });
            editText.setAccessibilityDelegate(new android.view.View.AccessibilityDelegate() {
                @Override
                public void sendAccessibilityEvent(@NonNull android.view.View host, int eventType) {
                    super.sendAccessibilityEvent(host, eventType);
                    if (eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                        syncFromCursor();
                    }
                }
            });
        }
    }

    @NonNull
    private static StyleState inspectChar(@NonNull Spanned spanned, int index) {
        if (index < 0 || index >= spanned.length()) {
            return new StyleState(false, false);
        }
        StyleSpan[] spans = spanned.getSpans(index, index + 1, StyleSpan.class);
        boolean b = false;
        boolean it = false;
        if (spans != null) {
            for (StyleSpan span : spans) {
                int s = spanned.getSpanStart(span);
                int e = spanned.getSpanEnd(span);
                if (s <= index && e > index) {
                    if ((span.getStyle() & Typeface.BOLD) != 0) {
                        b = true;
                    }
                    if ((span.getStyle() & Typeface.ITALIC) != 0) {
                        it = true;
                    }
                }
            }
        }
        return new StyleState(b, it);
    }

    @NonNull
    private static StyleState inspectRange(@NonNull Spanned spanned, int start, int end) {
        if (end <= start) {
            return new StyleState(false, false);
        }
        boolean allBold = true;
        boolean allItalic = true;
        for (int i = start; i < end; i++) {
            StyleState st = inspectChar(spanned, i);
            if (!st.bold) {
                allBold = false;
            }
            if (!st.italic) {
                allItalic = false;
            }
            if (!allBold && !allItalic) {
                break;
            }
        }
        return new StyleState(allBold, allItalic);
    }

    private static void updateRangeStyle(
            @NonNull Editable editable,
            int start,
            int end,
            boolean modifyBold,
            boolean enableTarget
    ) {
        int len = end - start;
        if (len <= 0) {
            return;
        }
        boolean[] boldFlags = new boolean[len];
        boolean[] italicFlags = new boolean[len];
        for (int i = 0; i < len; i++) {
            StyleState existing = inspectChar(editable, start + i);
            boldFlags[i] = modifyBold ? enableTarget : existing.bold;
            italicFlags[i] = modifyBold ? existing.italic : enableTarget;
        }

        stripOrSplitSpansInRange(editable, start, end);

        int runStart = 0;
        for (int i = 1; i <= len; i++) {
            boolean endRun = (i == len)
                    || (boldFlags[i] != boldFlags[runStart])
                    || (italicFlags[i] != italicFlags[runStart]);
            if (endRun) {
                applyFlags(editable, start + runStart, start + i, boldFlags[runStart], italicFlags[runStart]);
                runStart = i;
            }
        }
    }

    private static void stripOrSplitSpansInRange(
            @NonNull Editable editable,
            int start,
            int end
    ) {
        StyleSpan[] overlapping = editable.getSpans(start, end, StyleSpan.class);
        if (overlapping == null) {
            return;
        }
        for (StyleSpan span : overlapping) {
            int s = editable.getSpanStart(span);
            int e = editable.getSpanEnd(span);
            int style = span.getStyle();
            if (s < 0 || e <= s || e <= start || s >= end) {
                continue;
            }
            editable.removeSpan(span);
            if (s < start) {
                editable.setSpan(new StyleSpan(style), s, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (e > end) {
                editable.setSpan(new StyleSpan(style), end, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void applyFlags(
            @NonNull Spannable spannable,
            int start,
            int end,
            boolean bold,
            boolean italic
    ) {
        if (end <= start) {
            return;
        }
        if (bold && italic) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (bold) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (italic) {
            spannable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
