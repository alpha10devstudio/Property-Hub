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
  error?: string;
  inputSurfaceClass: string;
  onChange: (next: PropertyFieldValue) => void;
}

function extractDomSegments(root: HTMLElement): { plainText: string; segments: StyledSegment[] } {
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
  const plainText = merged.map((s) => s.text).join('').replace(/\n+$/, '');
  return { plainText, segments: merged };
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
  error,
  inputSurfaceClass,
  onChange,
}) => {
  const editorRef = useRef<HTMLDivElement | null>(null);
  const [boldActive, setBoldActive] = useState(false);
  const [italicActive, setItalicActive] = useState(false);

  useEffect(() => {
    if (!formattable || !editorRef.current) return;
    populateDomFromSegments(editorRef.current, value?.segments ?? []);
    setBoldActive(false);
    setItalicActive(false);
  }, [resetToken, formattable]);

  useEffect(() => {
    if (!formattable) return;
    const syncToolbar = () => {
      const el = editorRef.current;
      if (!el || document.activeElement !== el) return;
      try {
        setBoldActive(document.queryCommandState('bold'));
        setItalicActive(document.queryCommandState('italic'));
      } catch {
        // Ignore browser command state errors
      }
    };
    document.addEventListener('selectionchange', syncToolbar);
    return () => document.removeEventListener('selectionchange', syncToolbar);
  }, [formattable]);

  const applyFormat = (command: 'bold' | 'italic') => {
    const el = editorRef.current;
    if (!el) return;
    el.focus();
    try {
      document.execCommand(command, false);
      setBoldActive(document.queryCommandState('bold'));
      setItalicActive(document.queryCommandState('italic'));
    } catch {
      // Fallback ignored
    }
    const extracted = extractDomSegments(el);
    onChange({
      ...(value ?? { fieldId, label, plainText: '', segments: [] }),
      plainText: extracted.plainText,
      segments: extracted.segments,
    });
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
                e.preventDefault();
                applyFormat('bold');
              }}
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
                e.preventDefault();
                applyFormat('italic');
              }}
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
            onInput={(e) => {
              const extracted = extractDomSegments(e.currentTarget);
              onChange({
                ...(value ?? { fieldId, label, plainText: '', segments: [] }),
                plainText: extracted.plainText,
                segments: extracted.segments,
              });
            }}
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
