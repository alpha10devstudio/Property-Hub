import React from 'react';
import { PropertyFieldValue, StyledSegment } from './propertyCatalog';

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
  resetToken?: number;
  stickyBold?: boolean;
  stickyItalic?: boolean;
  onStickyChange?: (bold: boolean, italic: boolean) => void;
  error?: string;
  inputSurfaceClass: string;
  onChange: (next: PropertyFieldValue) => void;
}

export function buildWholeFieldSegments(
  text: string,
  bold: boolean,
  italic: boolean
): StyledSegment[] {
  return text ? [{ text, bold, italic }] : [];
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
  stickyBold = false,
  stickyItalic = false,
  onStickyChange,
  error,
  inputSurfaceClass,
  onChange,
}) => {
  const focusBorder =
    accent === 'blue' ? 'focus:border-[#0B41AD]' : 'focus:border-[#0B8A38]';
  const activeToggleCls =
    accent === 'blue'
      ? 'border-[#0B41AD] bg-[#0B41AD] text-white'
      : 'border-[#0B8A38] bg-[#0B8A38] text-white';

  const currentText = value?.plainText ?? '';
  const wholeFieldTextStyle = `${stickyBold ? 'font-bold' : 'font-normal'} ${
    stickyItalic ? 'italic' : ''
  } placeholder:font-normal placeholder:not-italic`;

  const emitTextChange = (nextText: string) => {
    onChange({
      ...(value ?? { fieldId, label, plainText: '', segments: [] }),
      plainText: nextText,
      segments: formattable
        ? buildWholeFieldSegments(nextText, stickyBold, stickyItalic)
        : nextText
          ? [{ text: nextText, bold: false, italic: false }]
          : [],
    });
  };

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
              }}
              onClick={() => onStickyChange?.(!stickyBold, stickyItalic)}
              aria-label={`Bold ${label}`}
              aria-pressed={stickyBold}
              className={`flex h-6 w-7 items-center justify-center rounded-md border text-[12px] font-bold transition-colors ${
                stickyBold
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
              }}
              onClick={() => onStickyChange?.(stickyBold, !stickyItalic)}
              aria-label={`Italic ${label}`}
              aria-pressed={stickyItalic}
              className={`flex h-6 w-7 items-center justify-center rounded-md border font-serif text-[12px] italic transition-colors ${
                stickyItalic
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
        multiline ? (
          <textarea
            value={currentText}
            onChange={(e) => emitTextChange(e.target.value)}
            placeholder={placeholder}
            rows={3}
            className={`min-h-[82px] w-full rounded-xl border px-3 py-2 text-[13.5px] leading-relaxed focus:outline-none ${focusBorder} ${inputSurfaceClass} ${wholeFieldTextStyle}`}
          />
        ) : (
          <input
            type="text"
            value={currentText}
            onChange={(e) => emitTextChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
              }
            }}
            placeholder={placeholder}
            className={`h-10.5 w-full rounded-xl border px-3 text-[13.5px] focus:outline-none ${focusBorder} ${inputSurfaceClass} ${wholeFieldTextStyle}`}
          />
        )
      ) : (
        <input
          type={inputType === 'PHONE' ? 'tel' : 'text'}
          value={currentText}
          onChange={(e) => emitTextChange(e.target.value)}
          placeholder={placeholder}
          className={`h-10.5 w-full rounded-xl border px-3 text-[13.5px] focus:outline-none ${focusBorder} ${inputSurfaceClass}`}
        />
      )}

      {error && <p className="mt-1 text-xs font-medium text-red-500">{error}</p>}
    </div>
  );
};
