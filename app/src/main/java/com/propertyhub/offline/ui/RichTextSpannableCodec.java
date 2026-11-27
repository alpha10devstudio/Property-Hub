package com.propertyhub.offline.ui;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.MotionEvent;
import android.view.View;
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
        @Nullable
        private Runnable onUserContentOrStyleEdited;
        private boolean pendingBold = false;
        private boolean pendingItalic = false;
        private boolean internalChange = false;
        private int touchedSelStart = -1;
        private int touchedSelEnd = -1;

        Controller(@NonNull EditText editText, @NonNull StyleStateListener listener) {
            this.editText = editText;
            this.listener = listener;
            installWatcher();
        }

        public void setOnUserContentOrStyleEdited(@Nullable Runnable callback) {
            this.onUserContentOrStyleEdited = callback;
        }

        public void bindFormatButtons(@NonNull View btnBold, @NonNull View btnItalic) {
            View.OnTouchListener snapshotTouch = (v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    snapshotSelection();
                }
                return false;
            };
            btnBold.setOnTouchListener(snapshotTouch);
            btnItalic.setOnTouchListener(snapshotTouch);
            btnBold.setOnClickListener(v -> toggleBold());
            btnItalic.setOnClickListener(v -> toggleItalic());
        }

        public void snapshotSelection() {
            int s = editText.getSelectionStart();
            int e = editText.getSelectionEnd();
            if (s >= 0 && e >= 0 && s != e) {
                touchedSelStart = Math.min(s, e);
                touchedSelEnd = Math.max(s, e);
            } else {
                touchedSelStart = -1;
                touchedSelEnd = -1;
            }
        }

        public void toggleBold() {
            toggleStyle(Typeface.BOLD);
        }

        public void toggleItalic() {
            toggleStyle(Typeface.ITALIC);
        }

        @NonNull
        public StyleState getPendingState() {
            return new StyleState(pendingBold, pendingItalic);
        }

        public void setPendingState(boolean bold, boolean italic) {
            this.pendingBold = bold;
            this.pendingItalic = italic;
            syncFromCursor();
        }

        /**
         * Updates toolbar state when selection changes WITHOUT auto-disabling the user's
         * sticky manual Bold/Italic typing toggles.
         */
        public void syncFromCursor() {
            Editable text = editText.getText();
            if (text == null) {
                listener.onStyleStateChanged(new StyleState(pendingBold, pendingItalic));
                return;
            }
            int selStart = Math.max(0, editText.getSelectionStart());
            int selEnd = Math.max(0, editText.getSelectionEnd());
            if (selStart != selEnd) {
                StyleState rangeState = inspectRange(text, Math.min(selStart, selEnd), Math.max(selStart, selEnd));
                listener.onStyleStateChanged(rangeState);
                return;
            }
            // Collapsed cursor: preserve sticky manual pendingBold / pendingItalic until user turns them off
            listener.onStyleStateChanged(new StyleState(pendingBold, pendingItalic));
        }

        public void resetPending() {
            pendingBold = false;
            pendingItalic = false;
            touchedSelStart = -1;
            touchedSelEnd = -1;
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

            // Rescue selection if Android ActionMode/touch transition collapsed it right before onClick
            if (start == end
                    && touchedSelStart >= 0
                    && touchedSelEnd > touchedSelStart
                    && touchedSelEnd <= text.length()) {
                start = touchedSelStart;
                end = touchedSelEnd;
            }
            touchedSelStart = -1;
            touchedSelEnd = -1;

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
                if (editText.hasFocus() || editText.requestFocus()) {
                    editText.setSelection(start, end);
                }
                editText.invalidate();
            } finally {
                internalChange = false;
            }

            if (onUserContentOrStyleEdited != null) {
                onUserContentOrStyleEdited.run();
            }

            StyleState updated = inspectRange(text, start, end);
            listener.onStyleStateChanged(updated);
        }

        private void installWatcher() {
            editText.addTextChangedListener(new TextWatcher() {
                private String oldReplacedSlice = "";
                private boolean[] oldBoldFlags = new boolean[0];
                private boolean[] oldItalicFlags = new boolean[0];
                private int changeStart = 0;
                private int newCount = 0;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (internalChange || s == null) {
                        oldReplacedSlice = "";
                        oldBoldFlags = new boolean[0];
                        oldItalicFlags = new boolean[0];
                        return;
                    }
                    int safeStart = Math.max(0, Math.min(start, s.length()));
                    int safeEnd = Math.max(safeStart, Math.min(safeStart + count, s.length()));
                    oldReplacedSlice = s.subSequence(safeStart, safeEnd).toString();
                    int oldLen = safeEnd - safeStart;
                    oldBoldFlags = new boolean[oldLen];
                    oldItalicFlags = new boolean[oldLen];
                    if (oldLen > 0 && s instanceof Spanned) {
                        Spanned spanned = (Spanned) s;
                        for (int i = 0; i < oldLen; i++) {
                            StyleState st = inspectChar(spanned, safeStart + i);
                            oldBoldFlags[i] = st.bold;
                            oldItalicFlags[i] = st.italic;
                        }
                    }
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    changeStart = Math.max(0, start);
                    newCount = Math.max(0, count);
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (internalChange || s == null) {
                        return;
                    }
                    if (onUserContentOrStyleEdited != null) {
                        onUserContentOrStyleEdited.run();
                    }
                    if (newCount <= 0) {
                        return;
                    }
                    int safeEnd = Math.min(s.length(), changeStart + newCount);
                    if (safeEnd <= changeStart) {
                        return;
                    }

                    // Preserve exact prior Bold/Italic spans on prefix/suffix wiped by IME composing replacement,
                    // while applying current sticky (pendingBold, pendingItalic) to newly inserted characters.
                    String newSlice = s.subSequence(changeStart, safeEnd).toString();
                    int oldLen = oldReplacedSlice.length();
                    int newLen = newSlice.length();

                    int selEnd = Selection.getSelectionEnd(s);
                    int maxSuffix = newLen;
                    if (selEnd >= changeStart && selEnd <= safeEnd) {
                        maxSuffix = Math.max(0, safeEnd - selEnd);
                    }

                    int suffix = 0;
                    while (suffix < maxSuffix
                            && suffix < oldLen
                            && suffix < newLen
                            && oldReplacedSlice.charAt(oldLen - 1 - suffix)
                            == newSlice.charAt(newLen - 1 - suffix)) {
                        suffix++;
                    }

                    int maxPrefixLimit = Math.min(oldLen - suffix, newLen - suffix);
                    int prefix = 0;
                    while (prefix < maxPrefixLimit
                            && oldReplacedSlice.charAt(prefix) == newSlice.charAt(prefix)) {
                        prefix++;
                    }

                    boolean[] targetBold = new boolean[newLen];
                    boolean[] targetItalic = new boolean[newLen];
                    for (int i = 0; i < prefix; i++) {
                        targetBold[i] = oldBoldFlags[i];
                        targetItalic[i] = oldItalicFlags[i];
                    }
                    int insertedEnd = newLen - suffix;
                    for (int i = prefix; i < insertedEnd; i++) {
                        targetBold[i] = pendingBold;
                        targetItalic[i] = pendingItalic;
                    }
                    for (int j = 0; j < suffix; j++) {
                        int newIdx = insertedEnd + j;
                        int oldIdx = oldLen - suffix + j;
                        targetBold[newIdx] = oldBoldFlags[oldIdx];
                        targetItalic[newIdx] = oldItalicFlags[oldIdx];
                    }

                    internalChange = true;
                    try {
                        stripOrSplitSpansInRange(s, changeStart, safeEnd);
                        int runStart = 0;
                        for (int i = 1; i <= newLen; i++) {
                            boolean endRun = (i == newLen)
                                    || (targetBold[i] != targetBold[runStart])
                                    || (targetItalic[i] != targetItalic[runStart]);
                            if (endRun) {
                                applyFlags(
                                        s,
                                        changeStart + runStart,
                                        changeStart + i,
                                        targetBold[runStart],
                                        targetItalic[runStart]
                                );
                                runStart = i;
                            }
                        }
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
            editText.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void sendAccessibilityEvent(@NonNull View host, int eventType) {
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
