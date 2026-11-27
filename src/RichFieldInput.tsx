import React, { useEffect, useRef, useState } from 'react';
import { mergeStyledSegments, PropertyFieldValue, StyledSegment } from './propertyCatalog';

interface RichFieldInputProps {
  fieldId: string;
  label: string;
  required: boolean;
  multiline: boolean;
  formattable: boolean;
  inputType: 'SINGLE_LINE' | 'MULTILINE' | 'PHONE' | 'NUMBER';
  placeholder: string;
  accent: 'blue' | 'green';
  value: PropertyFieldValue;
  resetToken: number;
  stickyBold?: boolean;
  stickyItalic?: boolean;
  onStickyChange?: (bold: boolean, italic: boolean) => void;
  error?: string;
  inputSurfaceClass: string;
  onChange: (next: PropertyFieldValue) => void;
}

interface TextOffsets {
  start: number;
  end: number;
}

function extractDomSegments(root: HTMLElement): {
  plainText: string;
  rawText: string;
  segments: StyledSegment[];
} {
  const raw: StyledSegment[] = [];

  const walk = (node: Node, bold: boolean, italic: boolean, isFirstChildInBlock: boolean) => {
    if (node.nodeType === Node.TEXT_NODE) {
      const text = node.textContent ?? '';
      if (text) {
        raw.push({ text, bold, italic });
      }
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;

    const el = node as HTMLElement;
    const tag = el.tagName.toUpperCase();
    if (tag === 'BR') {
      raw.push({ text: '\n', bold: false, italic: false });
      return;
    }

    const isBlock = tag === 'DIV' || tag === 'P';
    if (isBlock && !isFirstChildInBlock && raw.length > 0) {
      const lastText = raw[raw.length - 1].text;
      if (!lastText.endsWith('\n')) {
        raw.push({ text: '\n', bold: false, italic: false });
      }
    }

    const styleWeight = el.style.fontWeight;
    const nextBold =
      bold ||
      tag === 'B' ||
      tag === 'STRONG' ||
      styleWeight === 'bold' ||
      Number(styleWeight) >= 600;
    const nextItalic = italic || tag === 'I' || tag === 'EM' || el.style.fontStyle === 'italic';

    const children = Array.from(el.childNodes);
    children.forEach((child, idx) => walk(child, nextBold, nextItalic, idx === 0));
  };

  Array.from(root.childNodes).forEach((child, idx) => walk(child, false, false, idx === 0));

  const merged = mergeStyledSegments(raw);
  const rawText = merged.map((s) => s.text).join('');
  const plainText = rawText.replace(/\n+$/, '');
  return { plainText, rawText, segments: merged };
}

function populateDomFromSegments(root: HTMLElement, segments: StyledSegment[]) {
  root.textContent = '';
  for (const seg of segments) {
    if (!seg.text) continue;
    const lines = seg.text.split('\n');
    lines.forEach((lineText, lineIdx) => {
      if (lineText) {
        let node: Node = document.createTextNode(lineText);
        if (seg.italic) {
          const em = document.createElement('i');
          em.appendChild(node);
          node = em;
        }
        if (seg.bold) {
          const strong = document.createElement('b');
          strong.appendChild(node);
          node = strong;
        }
        root.appendChild(node);
      }
      if (lineIdx < lines.length - 1) {
        root.appendChild(document.createElement('br'));
      }
    });
  }
}

function getSelectionRangeOffsets(root: HTMLElement): TextOffsets | null {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return null;
  const range = sel.getRangeAt(0);
  if (!root.contains(range.startContainer) || !root.contains(range.endContainer)) {
    return null;
  }

  let charOffset = 0;
  let lastEndedWithNewline = true;
  let startPos: number | null = null;
  let endPos: number | null = null;

  const checkElementBoundary = (el: Node, childIndex: number) => {
    if (range.startContainer === el && range.startOffset === childIndex && startPos === null) {
      startPos = charOffset;
    }
    if (range.endContainer === el && range.endOffset === childIndex && endPos === null) {
      endPos = charOffset;
    }
  };

  const walk = (node: Node, isFirstChildInBlock: boolean) => {
    if (node.nodeType === Node.TEXT_NODE) {
      const len = (node.textContent ?? '').length;
      if (range.startContainer === node && startPos === null) {
        startPos = charOffset + Math.min(range.startOffset, len);
      }
      if (range.endContainer === node && endPos === null) {
        endPos = charOffset + Math.min(range.endOffset, len);
      }
      charOffset += len;
      if (len > 0) {
        lastEndedWithNewline = (node.textContent ?? '').endsWith('\n');
      }
      return;
    }

    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const el = node as HTMLElement;
    const tag = el.tagName.toUpperCase();

    if (tag === 'BR') {
      checkElementBoundary(el, 0);
      charOffset += 1;
      lastEndedWithNewline = true;
      return;
    }

    const isBlock = tag === 'DIV' || tag === 'P';
    if (isBlock && !isFirstChildInBlock && charOffset > 0 && !lastEndedWithNewline) {
      charOffset += 1;
      lastEndedWithNewline = true;
    }

    const children = Array.from(el.childNodes);
    for (let i = 0; i < children.length; i++) {
      checkElementBoundary(el, i);
      walk(children[i], i === 0);
    }
    checkElementBoundary(el, children.length);
  };

  walk(root, true);

  if (startPos === null || endPos === null) return null;
  return {
    start: Math.min(startPos, endPos),
    end: Math.max(startPos, endPos),
  };
}

function setSelectionRangeOffsets(root: HTMLElement, start: number, end: number) {
  const sel = window.getSelection();
  if (!sel) return;

  const targetStart = Math.max(0, Math.min(start, end));
  const targetEnd = Math.max(targetStart, Math.max(start, end));

  let charOffset = 0;
  let lastEndedWithNewline = true;
  let startPoint: { node: Node; offset: number } | null = null;
  let endPoint: { node: Node; offset: number } | null = null;
  let lastBoundary: { node: Node; offset: number } = { node: root, offset: root.childNodes.length };

  const resolvePoint = (
    target: number,
    node: Node,
    offsetInNode: number
  ): { node: Node; offset: number } => {
    void target;
    return { node, offset: offsetInNode };
  };

  const walk = (node: Node, isFirstChildInBlock: boolean) => {
    if (node.nodeType === Node.TEXT_NODE) {
      const len = (node.textContent ?? '').length;
      if (startPoint === null && targetStart <= charOffset + len) {
        startPoint = resolvePoint(targetStart, node, Math.max(0, targetStart - charOffset));
      }
      if (endPoint === null && targetEnd <= charOffset + len) {
        endPoint = resolvePoint(targetEnd, node, Math.max(0, targetEnd - charOffset));
      }
      charOffset += len;
      lastBoundary = { node, offset: len };
      if (len > 0) {
        lastEndedWithNewline = (node.textContent ?? '').endsWith('\n');
      }
      return;
    }

    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const el = node as HTMLElement;
    const tag = el.tagName.toUpperCase();

    if (tag === 'BR') {
      const parent = el.parentNode;
      const idx = parent ? Array.from(parent.childNodes).indexOf(el) : 0;
      if (startPoint === null && targetStart <= charOffset && parent) {
        startPoint = { node: parent, offset: idx };
      }
      if (endPoint === null && targetEnd <= charOffset && parent) {
        endPoint = { node: parent, offset: idx };
      }
      charOffset += 1;
      lastEndedWithNewline = true;
      if (parent) {
        lastBoundary = { node: parent, offset: idx + 1 };
      }
      return;
    }

    const isBlock = tag === 'DIV' || tag === 'P';
    if (isBlock && !isFirstChildInBlock && charOffset > 0 && !lastEndedWithNewline) {
      charOffset += 1;
      lastEndedWithNewline = true;
    }

    const children = Array.from(el.childNodes);
    for (let i = 0; i < children.length; i++) {
      if (startPoint === null && targetStart === charOffset && children[i].nodeType !== Node.TEXT_NODE) {
        startPoint = { node: el, offset: i };
      }
      walk(children[i], i === 0);
    }
    lastBoundary = { node: el, offset: children.length };
  };

  walk(root, true);

  if (startPoint === null) startPoint = lastBoundary;
  if (endPoint === null) endPoint = lastBoundary;

  const range = document.createRange();
  range.setStart(startPoint.node, startPoint.offset);
  range.setEnd(endPoint.node, endPoint.offset);
  sel.removeAllRanges();
  sel.addRange(range);
}

function inspectSegmentRange(
  segments: StyledSegment[],
  start: number,
  end: number
): { bold: boolean; italic: boolean } {
  if (end <= start) return { bold: false, italic: false };
  let pos = 0;
  let allBold = true;
  let allItalic = true;
  let coveredAny = false;

  for (const seg of segments) {
    const len = seg.text.length;
    const segStart = pos;
    const segEnd = pos + len;
    pos = segEnd;
    if (segEnd <= start || segStart >= end || len === 0) continue;
    coveredAny = true;
    if (!seg.bold) allBold = false;
    if (!seg.italic) allItalic = false;
    if (!allBold && !allItalic) break;
  }

  return coveredAny ? { bold: allBold, italic: allItalic } : { bold: false, italic: false };
}

function applyStyleToSegmentRange(
  segments: StyledSegment[],
  start: number,
  end: number,
  target: 'bold' | 'italic',
  enable: boolean
): StyledSegment[] {
  if (end <= start) return segments;
  const out: StyledSegment[] = [];
  let pos = 0;

  for (const seg of segments) {
    const len = seg.text.length;
    const segStart = pos;
    const segEnd = pos + len;
    pos = segEnd;
    if (len === 0) continue;

    if (segEnd <= start || segStart >= end) {
      out.push(seg);
      continue;
    }

    if (segStart < start) {
      out.push({
        text: seg.text.slice(0, start - segStart),
        bold: seg.bold,
        italic: seg.italic,
      });
    }

    const overlapStart = Math.max(start, segStart) - segStart;
    const overlapEnd = Math.min(end, segEnd) - segStart;
    out.push({
      text: seg.text.slice(overlapStart, overlapEnd),
      bold: target === 'bold' ? enable : seg.bold,
      italic: target === 'italic' ? enable : seg.italic,
    });

    if (segEnd > end) {
      out.push({
        text: seg.text.slice(end - segStart),
        bold: seg.bold,
        italic: seg.italic,
      });
    }
  }

  return mergeStyledSegments(out);
}

function sliceSegments(segments: StyledSegment[], start: number, end: number): StyledSegment[] {
  if (end <= start) return [];
  const out: StyledSegment[] = [];
  let pos = 0;
  for (const seg of segments) {
    const len = seg.text.length;
    const segStart = pos;
    const segEnd = pos + len;
    pos = segEnd;
    if (segEnd <= start || segStart >= end || len === 0) continue;
    const s = Math.max(start, segStart) - segStart;
    const e = Math.min(end, segEnd) - segStart;
    if (e > s) {
      out.push({
        text: seg.text.slice(s, e),
        bold: seg.bold,
        italic: seg.italic,
      });
    }
  }
  return out;
}

function segmentsEqual(a: StyledSegment[], b: StyledSegment[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (a[i].text !== b[i].text || a[i].bold !== b[i].bold || a[i].italic !== b[i].italic) {
      return false;
    }
  }
  return true;
}

export const RichFieldInput: React.FC<RichFieldInputProps> = ({
  fieldId,
  label,
  required,
  multiline,
  formattable,
  inputType,
  placeholder,
  accent,
  value,
  resetToken,
  stickyBold = false,
  stickyItalic = false,
  onStickyChange,
  error,
  inputSurfaceClass,
  onChange,
}) => {
  const editorRef = useRef<HTMLDivElement | null>(null);
  const manualBoldRef = useRef(stickyBold);
  const manualItalicRef = useRef(stickyItalic);
  const [boldActive, setBoldActive] = useState(stickyBold);
  const [italicActive, setItalicActive] = useState(stickyItalic);

  const savedOffsetsRef = useRef<TextOffsets | null>(null);
  const lastSegmentsRef = useRef<StyledSegment[]>(value?.segments ?? []);
  const isComposingRef = useRef(false);

  useEffect(() => {
    manualBoldRef.current = stickyBold;
    manualItalicRef.current = stickyItalic;
    const el = editorRef.current;
    const offsets = el ? getSelectionRangeOffsets(el) : null;
    if (!offsets || offsets.start === offsets.end) {
      setBoldActive(stickyBold);
      setItalicActive(stickyItalic);
    }
  }, [stickyBold, stickyItalic]);

  useEffect(() => {
    if (!formattable || !editorRef.current) return;
    const initialSegs = value?.segments ?? [];
    populateDomFromSegments(editorRef.current, initialSegs);
    lastSegmentsRef.current = initialSegs;
    savedOffsetsRef.current = null;
    setBoldActive(manualBoldRef.current);
    setItalicActive(manualItalicRef.current);
  }, [resetToken, formattable]);

  const captureEditorSelection = () => {
    const el = editorRef.current;
    if (!el) return;
    const offsets = getSelectionRangeOffsets(el);
    if (!offsets) return;
    savedOffsetsRef.current = offsets;
    if (offsets.start < offsets.end) {
      const segs = extractDomSegments(el).segments;
      const rangeState = inspectSegmentRange(segs, offsets.start, offsets.end);
      setBoldActive(rangeState.bold);
      setItalicActive(rangeState.italic);
    } else {
      // Collapsed cursor: NEVER auto-disable sticky manual B/I toggles
      setBoldActive(manualBoldRef.current);
      setItalicActive(manualItalicRef.current);
    }
  };

  useEffect(() => {
    if (!formattable) return;
    const handleSelectionChange = () => {
      const el = editorRef.current;
      if (!el) return;
      const sel = window.getSelection();
      if (!sel || sel.rangeCount === 0) return;
      const range = sel.getRangeAt(0);
      if (!el.contains(range.startContainer) || !el.contains(range.endContainer)) return;
      captureEditorSelection();
    };
    document.addEventListener('selectionchange', handleSelectionChange);
    return () => document.removeEventListener('selectionchange', handleSelectionChange);
  }, [formattable]);

  const processEditorInput = () => {
    const el = editorRef.current;
    if (!el) return;
    const extracted = extractDomSegments(el);
    if (isComposingRef.current) {
      onChange({
        ...(value ?? { fieldId, label, plainText: '', segments: [] }),
        plainText: extracted.plainText,
        segments: extracted.segments,
      });
      return;
    }

    const prevSegments = lastSegmentsRef.current;
    const prevRaw = prevSegments.map((s) => s.text).join('');
    const nextRaw = extracted.rawText;
    const offsets = getSelectionRangeOffsets(el);
    const caretOffset = offsets ? offsets.end : null;

    const maxSuffix = caretOffset !== null ? Math.max(0, nextRaw.length - caretOffset) : nextRaw.length;
    let suffix = 0;
    while (
      suffix < maxSuffix &&
      suffix < prevRaw.length &&
      prevRaw[prevRaw.length - 1 - suffix] === nextRaw[nextRaw.length - 1 - suffix]
    ) {
      suffix++;
    }

    const prevSel = savedOffsetsRef.current;
    const maxPrefixLimit = Math.min(prevRaw.length - suffix, nextRaw.length - suffix);
    let prefix = 0;
    while (
      prefix < maxPrefixLimit &&
      prevRaw[prefix] === nextRaw[prefix]
    ) {
      prefix++;
    }
    if (prevSel && prevSel.start < prevSel.end && prefix > prevSel.start) {
      prefix = prevSel.start;
    }

    const insertedEnd = nextRaw.length - suffix;
    let finalSegments = extracted.segments;

    if (insertedEnd > prefix) {
      const insertedText = nextRaw.slice(prefix, insertedEnd);
      const patched = mergeStyledSegments([
        ...sliceSegments(prevSegments, 0, prefix),
        {
          text: insertedText,
          bold: manualBoldRef.current,
          italic: manualItalicRef.current,
        },
        ...sliceSegments(prevSegments, prevRaw.length - suffix, prevRaw.length),
      ]);

      if (!segmentsEqual(patched, extracted.segments)) {
        finalSegments = patched;
        const targetCaret = caretOffset ?? insertedEnd;
        populateDomFromSegments(el, finalSegments);
        setSelectionRangeOffsets(el, targetCaret, targetCaret);
      }
    }

    lastSegmentsRef.current = finalSegments;
    const nextOffsets = getSelectionRangeOffsets(el);
    if (nextOffsets) {
      savedOffsetsRef.current = nextOffsets;
    }

    const finalRaw = finalSegments.map((s) => s.text).join('');
    onChange({
      ...(value ?? { fieldId, label, plainText: '', segments: [] }),
      plainText: finalRaw.replace(/\n+$/, ''),
      segments: finalSegments,
    });
  };

  const applyFormat = (command: 'bold' | 'italic') => {
    const el = editorRef.current;
    if (!el) return;

    const liveOffsets = getSelectionRangeOffsets(el);
    const activeOffsets =
      liveOffsets && liveOffsets.start < liveOffsets.end
        ? liveOffsets
        : savedOffsetsRef.current && savedOffsetsRef.current.start < savedOffsetsRef.current.end
          ? savedOffsetsRef.current
          : liveOffsets ?? savedOffsetsRef.current;

    if (activeOffsets && activeOffsets.start < activeOffsets.end) {
      const { start, end } = activeOffsets;
      const extracted = extractDomSegments(el);
      const currentRange = inspectSegmentRange(extracted.segments, start, end);
      const enableTarget = command === 'bold' ? !currentRange.bold : !currentRange.italic;
      const nextSegments = applyStyleToSegmentRange(
        extracted.segments,
        start,
        end,
        command,
        enableTarget
      );

      populateDomFromSegments(el, nextSegments);
      el.focus();
      setSelectionRangeOffsets(el, start, end);

      lastSegmentsRef.current = nextSegments;
      savedOffsetsRef.current = { start, end };

      const updatedState = inspectSegmentRange(nextSegments, start, end);
      setBoldActive(updatedState.bold);
      setItalicActive(updatedState.italic);

      const finalRaw = nextSegments.map((s) => s.text).join('');
      onChange({
        ...(value ?? { fieldId, label, plainText: '', segments: [] }),
        plainText: finalRaw.replace(/\n+$/, ''),
        segments: nextSegments,
      });
      return;
    }

    // Collapsed cursor (or empty field): toggle sticky manual B/I typing state
    if (command === 'bold') {
      manualBoldRef.current = !manualBoldRef.current;
      setBoldActive(manualBoldRef.current);
    } else {
      manualItalicRef.current = !manualItalicRef.current;
      setItalicActive(manualItalicRef.current);
    }
    onStickyChange?.(manualBoldRef.current, manualItalicRef.current);

    el.focus();
    if (activeOffsets) {
      setSelectionRangeOffsets(el, activeOffsets.start, activeOffsets.end);
      savedOffsetsRef.current = activeOffsets;
    }
    try {
      const targetState = command === 'bold' ? manualBoldRef.current : manualItalicRef.current;
      if (document.queryCommandState(command) !== targetState) {
        document.execCommand(command, false);
      }
    } catch {
      // Ignore browser execCommand errors; processEditorInput enforces sticky style directly
    }
  };

  const focusBorder =
    accent === 'blue' ? 'focus:border-[#0B41AD]' : 'focus:border-[#0B8A38]';
  const activeToggleCls =
    accent === 'blue'
      ? 'border-[#0B41AD] bg-[#0B41AD] text-white'
      : 'border-[#0B8A38] bg-[#0B8A38] text-white';

  return (
    <div data-field-id={fieldId}>
      <div className="mb-1 flex items-center justify-between gap-2">
        <label className="text-[13px] font-bold">
          {label}
          {required ? ' *' : ''}
        </label>
        {formattable && (
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onMouseDown={(e) => {
                captureEditorSelection();
                e.preventDefault();
              }}
              onClick={() => applyFormat('bold')}
              aria-label={`Bold ${label}`}
              aria-pressed={boldActive}
              className={`flex h-6 w-7 items-center justify-center rounded-md border text-[12px] font-bold transition-colors ${
                boldActive
                  ? activeToggleCls
                  : 'border-slate-300 text-slate-500 hover:bg-slate-100'
              }`}
            >
              B
            </button>
            <button
              type="button"
              onMouseDown={(e) => {
                captureEditorSelection();
                e.preventDefault();
              }}
              onClick={() => applyFormat('italic')}
              aria-label={`Italic ${label}`}
              aria-pressed={italicActive}
              className={`flex h-6 w-7 items-center justify-center rounded-md border font-serif text-[12px] italic transition-colors ${
                italicActive
                  ? activeToggleCls
                  : 'border-slate-300 text-slate-500 hover:bg-slate-100'
              }`}
            >
              I
            </button>
          </div>
        )}
      </div>

      {formattable ? (
        <div className="relative">
          {!(value?.plainText ?? '') && (
            <span className="pointer-events-none absolute left-3 top-2.5 text-[13px] text-slate-400">
              {placeholder}
            </span>
          )}
          <div
            ref={editorRef}
            contentEditable
            suppressContentEditableWarning
            onCompositionStart={() => {
              isComposingRef.current = true;
            }}
            onCompositionEnd={() => {
              isComposingRef.current = false;
              processEditorInput();
            }}
            onInput={processEditorInput}
            onMouseUp={captureEditorSelection}
            onKeyUp={captureEditorSelection}
            onSelect={captureEditorSelection}
            onKeyDown={(e) => {
              if (!multiline && e.key === 'Enter') {
                e.preventDefault();
              }
            }}
            className={`w-full rounded-xl border px-3 py-2 text-[13.5px] leading-relaxed focus:outline-none ${focusBorder} ${inputSurfaceClass} ${
              multiline ? 'min-h-[82px]' : 'min-h-[42px]'
            }`}
          />
        </div>
      ) : (
        <input
          type={inputType === 'PHONE' ? 'tel' : 'text'}
          value={value?.plainText ?? ''}
          onChange={(e) => {
            const text = e.target.value;
            onChange({
              ...(value ?? { fieldId, label, plainText: '', segments: [] }),
              plainText: text,
              segments: text ? [{ text, bold: false, italic: false }] : [],
            });
          }}
          placeholder={placeholder}
          className={`h-10.5 w-full rounded-xl border px-3 text-[13.5px] focus:outline-none ${focusBorder} ${inputSurfaceClass}`}
        />
      )}

      {error && <p className="mt-1 text-xs font-medium text-red-500">{error}</p>}
    </div>
  );
};
