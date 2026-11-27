package com.propertyhub.offline.ui;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
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
     * Converts whole-field plain text + bold/italic flags into a single {@link StyledSegment} list.
     */
    @NonNull
    public static List<StyledSegment> buildWholeFieldSegments(
            @Nullable String plainText,
            boolean bold,
            boolean italic
    ) {
        if (plainText == null || plainText.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new StyledSegment(plainText, bold, italic));
    }

    /**
     * Attaches whole-field sticky Bold/Italic support and toolbar callbacks for an EditText.
     * Whenever Bold or Italic is enabled, 100% of the field (defaults, existing text, and newly typed
     * or IME-composed text) renders and exports in that style until manually turned off.
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
        private final Typeface baseTypeface;
        @Nullable
        private Runnable onUserTextEdited;
        private boolean pendingBold = false;
        private boolean pendingItalic = false;
        private boolean internalChange = false;
        private int lastAppliedTypefaceStyle = -1;

        Controller(@NonNull EditText editText, @NonNull StyleStateListener listener) {
            this.editText = editText;
            this.listener = listener;
            Typeface current = editText.getTypeface();
            this.baseTypeface = current != null
                    ? Typeface.create(current, Typeface.NORMAL)
                    : Typeface.DEFAULT;
            installWatcher();
            applyWholeFieldVisuals();
        }

        public void setOnUserContentOrStyleEdited(@Nullable Runnable callback) {
            this.onUserTextEdited = callback;
        }

        public void bindFormatButtons(@NonNull View btnBold, @NonNull View btnItalic) {
            btnBold.setOnTouchListener(null);
            btnItalic.setOnTouchListener(null);
            btnBold.setOnClickListener(v -> toggleBold());
            btnItalic.setOnClickListener(v -> toggleItalic());
        }

        public void snapshotSelection() {
            // Selection snapshots are no longer needed in whole-field mode.
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
            applyWholeFieldVisuals();
            listener.onStyleStateChanged(new StyleState(pendingBold, pendingItalic));
        }

        @NonNull
        public List<StyledSegment> extractWholeFieldSegments(@Nullable String plainText) {
            return buildWholeFieldSegments(plainText, pendingBold, pendingItalic);
        }

        /**
         * Syncs toolbar toggle state without auto-disabling sticky whole-field Bold/Italic.
         */
        public void syncFromCursor() {
            applyWholeFieldVisuals();
            listener.onStyleStateChanged(new StyleState(pendingBold, pendingItalic));
        }

        public void resetPending() {
            pendingBold = false;
            pendingItalic = false;
            applyWholeFieldVisuals();
            listener.onStyleStateChanged(new StyleState(false, false));
        }

        private void toggleStyle(int targetStyle) {
            if (targetStyle == Typeface.BOLD) {
                pendingBold = !pendingBold;
            } else {
                pendingItalic = !pendingItalic;
            }
            applyWholeFieldVisuals();
            editText.invalidate();
            listener.onStyleStateChanged(new StyleState(pendingBold, pendingItalic));
        }

        private void applyWholeFieldVisuals() {
            Editable text = editText.getText();
            boolean hasText = text != null && text.length() > 0;
            if (hasText) {
                StyleSpan[] spans = text.getSpans(0, text.length(), StyleSpan.class);
                if (spans != null && spans.length > 0) {
                    internalChange = true;
                    try {
                        for (StyleSpan span : spans) {
                            text.removeSpan(span);
                        }
                    } finally {
                        internalChange = false;
                    }
                }
            }

            int targetStyle = Typeface.NORMAL;
            if (hasText) {
                if (pendingBold && pendingItalic) {
                    targetStyle = Typeface.BOLD_ITALIC;
                } else if (pendingBold) {
                    targetStyle = Typeface.BOLD;
                } else if (pendingItalic) {
                    targetStyle = Typeface.ITALIC;
                }
            }

            if (targetStyle != lastAppliedTypefaceStyle) {
                editText.setTypeface(baseTypeface, targetStyle);
                lastAppliedTypefaceStyle = targetStyle;
            }
        }

        private void installWatcher() {
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (internalChange) {
                        return;
                    }
                    applyWholeFieldVisuals();
                    if (onUserTextEdited != null) {
                        onUserTextEdited.run();
                    }
                }
            });

            editText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    syncFromCursor();
                }
            });
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
