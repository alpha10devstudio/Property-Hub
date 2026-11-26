export type CategoryCode = 'HOUSE_FOR_RENT' | 'LAND_FOR_SALE';
export type FieldInputType = 'SINGLE_LINE' | 'MULTILINE' | 'PHONE' | 'NUMBER';

export interface StyledSegment {
  text: string;
  bold: boolean;
  italic: boolean;
}

export interface PropertyFieldConfig {
  fieldId: string;
  category: CategoryCode;
  label: string;
  defaultValue: string;
  placeholder: string;
  inputType: FieldInputType;
  required: boolean;
  formattable: boolean;
  displayOrder: number;
  showOnPosterBody: boolean;
}

export interface PropertyFieldValue {
  fieldId: string;
  label: string;
  plainText: string;
  segments: StyledSegment[];
}

export interface PropertyRecord {
  recordId: string;
  date: string;
  time: string;
  category: CategoryCode;
  title: string;
  mobile: string;
  details: string;
  fieldValues: PropertyFieldValue[];
  sourceFileName: string;
}

export interface PosterEntry {
  recordId: string;
  fileName: string;
  category: CategoryCode;
  date: string;
  time: string;
  sourceTxtFile: string;
  dataUrl: string;
  createdAt: number;
}

export interface ZipArchiveEntry {
  fileName: string;
  imageCount: number;
  txtCount: number;
  createdAt: string;
  contents: string[];
}

export const HOUSE_DEFAULT_TITLE = 'ফ্ল্যাট / বাসা ভাড়া দেওয়া হবে';
export const LAND_DEFAULT_TITLE = 'জমি বিক্রি করা হবে';

export const HOUSE_FIELDS: PropertyFieldConfig[] = [
  {
    fieldId: 'title',
    category: 'HOUSE_FOR_RENT',
    label: 'টাইটেল',
    defaultValue: HOUSE_DEFAULT_TITLE,
    placeholder: 'পোস্টারের শিরোনাম লিখুন',
    inputType: 'SINGLE_LINE',
    required: true,
    formattable: false,
    displayOrder: 1,
    showOnPosterBody: false,
  },
  {
    fieldId: 'description',
    category: 'HOUSE_FOR_RENT',
    label: 'বর্ণনা',
    defaultValue: '',
    placeholder: 'রুম সংখ্যা, সুবিধা, ভাড়া ইত্যাদি লিখুন',
    inputType: 'MULTILINE',
    required: true,
    formattable: true,
    displayOrder: 2,
    showOnPosterBody: true,
  },
  {
    fieldId: 'address',
    category: 'HOUSE_FOR_RENT',
    label: 'ঠিকানা',
    defaultValue: '',
    placeholder: 'বাসা / ফ্ল্যাটের সম্পূর্ণ ঠিকানা লিখুন',
    inputType: 'MULTILINE',
    required: false,
    formattable: true,
    displayOrder: 3,
    showOnPosterBody: true,
  },
  {
    fieldId: 'mobile',
    category: 'HOUSE_FOR_RENT',
    label: 'মোবাইল',
    defaultValue: '',
    placeholder: '01XXXXXXXXX (১১ ডিজিট)',
    inputType: 'PHONE',
    required: true,
    formattable: false,
    displayOrder: 4,
    showOnPosterBody: false,
  },
];

export const LAND_FIELDS: PropertyFieldConfig[] = [
  {
    fieldId: 'title',
    category: 'LAND_FOR_SALE',
    label: 'টাইটেল',
    defaultValue: LAND_DEFAULT_TITLE,
    placeholder: 'পোস্টারের শিরোনাম লিখুন',
    inputType: 'SINGLE_LINE',
    required: true,
    formattable: false,
    displayOrder: 1,
    showOnPosterBody: false,
  },
  {
    fieldId: 'land_address',
    category: 'LAND_FOR_SALE',
    label: 'জমির ঠিকানা',
    defaultValue: '',
    placeholder: 'জমির অবস্থান ও ঠিকানা লিখুন',
    inputType: 'MULTILINE',
    required: true,
    formattable: true,
    displayOrder: 2,
    showOnPosterBody: true,
  },
  {
    fieldId: 'mouza',
    category: 'LAND_FOR_SALE',
    label: 'মৌজা',
    defaultValue: '',
    placeholder: 'মৌজার নাম লিখুন',
    inputType: 'SINGLE_LINE',
    required: false,
    formattable: true,
    displayOrder: 3,
    showOnPosterBody: true,
  },
  {
    fieldId: 'land_area',
    category: 'LAND_FOR_SALE',
    label: 'জমির পরিমাণ',
    defaultValue: '',
    placeholder: 'যেমন: ৩ শতক, ৫ শতক, ২ কাঠা',
    inputType: 'SINGLE_LINE',
    required: false,
    formattable: true,
    displayOrder: 4,
    showOnPosterBody: true,
  },
  {
    fieldId: 'land_price',
    category: 'LAND_FOR_SALE',
    label: 'জমির মূল্য',
    defaultValue: '',
    placeholder: 'মূল্য লিখুন (যেমন: ১৫ লক্ষ টাকা)',
    inputType: 'SINGLE_LINE',
    required: false,
    formattable: true,
    displayOrder: 5,
    showOnPosterBody: true,
  },
  {
    fieldId: 'dag_number',
    category: 'LAND_FOR_SALE',
    label: 'দাগ নম্বর',
    defaultValue: '',
    placeholder: 'দাগ নম্বর লিখুন',
    inputType: 'SINGLE_LINE',
    required: false,
    formattable: true,
    displayOrder: 6,
    showOnPosterBody: true,
  },
  {
    fieldId: 'khatian_number',
    category: 'LAND_FOR_SALE',
    label: 'খতিয়ান নম্বর',
    defaultValue: '',
    placeholder: 'খতিয়ান নম্বর লিখুন',
    inputType: 'SINGLE_LINE',
    required: false,
    formattable: true,
    displayOrder: 7,
    showOnPosterBody: true,
  },
  {
    fieldId: 'additional_info',
    category: 'LAND_FOR_SALE',
    label: 'অতিরিক্ত তথ্য',
    defaultValue: '',
    placeholder: 'রাস্তা, জমির ধরন বা অন্যান্য তথ্য লিখুন',
    inputType: 'MULTILINE',
    required: false,
    formattable: true,
    displayOrder: 8,
    showOnPosterBody: true,
  },
  {
    fieldId: 'mobile',
    category: 'LAND_FOR_SALE',
    label: 'যোগাযোগ নম্বর',
    defaultValue: '',
    placeholder: '01XXXXXXXXX (১১ ডিজিট)',
    inputType: 'PHONE',
    required: true,
    formattable: false,
    displayOrder: 9,
    showOnPosterBody: false,
  },
];

export function getFieldsForCategory(category: CategoryCode): PropertyFieldConfig[] {
  return category === 'HOUSE_FOR_RENT' ? HOUSE_FIELDS : LAND_FIELDS;
}

export function getDefaultTitle(category: CategoryCode): string {
  return category === 'HOUSE_FOR_RENT' ? HOUSE_DEFAULT_TITLE : LAND_DEFAULT_TITLE;
}

export function isValidBangladeshiMobile(rawInput: string): boolean {
  const trimmed = rawInput.trim();
  if (!trimmed) return false;

  let digits = '';
  let sawPlus = false;
  for (let i = 0; i < trimmed.length; i++) {
    const code = trimmed.charCodeAt(i);
    const ch = trimmed[i];
    if (ch >= '0' && ch <= '9') {
      digits += ch;
    } else if (code >= 0x09e6 && code <= 0x09ef) {
      digits += String(code - 0x09e6);
    } else if (ch === '+' && !sawPlus && digits.length === 0) {
      sawPlus = true;
    } else if (!['-', ' ', '(', ')'].includes(ch)) {
      return false;
    }
  }
  if (digits.startsWith('880') && digits.length === 13) {
    digits = digits.slice(2);
  }
  return /^01[3-9]\d{8}$/.test(digits);
}

export function mergeStyledSegments(segments: StyledSegment[]): StyledSegment[] {
  const out: StyledSegment[] = [];
  for (const seg of segments) {
    if (!seg.text) continue;
    const prev = out[out.length - 1];
    if (prev && prev.bold === seg.bold && prev.italic === seg.italic) {
      prev.text += seg.text;
    } else {
      out.push({ ...seg });
    }
  }
  return out;
}

export function buildDatedRecordId(category: CategoryCode, dateYmd: string, seq: number): string {
  const compact = dateYmd.replace(/\D/g, '').slice(0, 8).padEnd(8, '0');
  const prefix = category === 'HOUSE_FOR_RENT' ? 'PH_RENT_' : 'PH_LAND_';
  return `${prefix}${compact}_${String(seq).padStart(4, '0')}`;
}

export function buildPosterFileName(category: CategoryCode, recordId: string): string {
  const clean = recordId.trim();
  const upper = clean.toUpperCase();
  if (upper.startsWith('PH_RENT_') || upper.startsWith('PH_LAND_')) {
    return `${clean}.jpg`;
  }
  const legacyPrefix = category === 'HOUSE_FOR_RENT' ? 'HOUSE_' : 'LAND_';
  const legacyPoster = category === 'HOUSE_FOR_RENT' ? 'House_' : 'Land_';
  const suffix = upper.startsWith(legacyPrefix) ? clean.slice(legacyPrefix.length) : clean;
  return `${legacyPoster}${suffix}.jpg`;
}

export function buildHumanReadableSummary(
  category: CategoryCode,
  fieldValues: PropertyFieldValue[]
): string {
  const configs = getFieldsForCategory(category);
  const byId = new Map(fieldValues.map((v) => [v.fieldId, v]));
  const lines: string[] = [];
  for (const cfg of configs) {
    if (!cfg.showOnPosterBody) continue;
    const val = byId.get(cfg.fieldId);
    if (val && val.plainText.trim()) {
      lines.push(`${cfg.label}: ${val.plainText.trim()}`);
    }
  }
  return lines.join('\n');
}

export function getPosterVisibleFieldValues(record: PropertyRecord): PropertyFieldValue[] {
  if (record.fieldValues && record.fieldValues.length > 0) {
    const configs = getFieldsForCategory(record.category);
    const bodyIds = new Set(configs.filter((c) => c.showOnPosterBody).map((c) => c.fieldId));
    return record.fieldValues.filter(
      (fv) => bodyIds.has(fv.fieldId) && fv.plainText.trim().length > 0
    );
  }
  if (!record.details.trim()) return [];
  const label = record.category === 'HOUSE_FOR_RENT' ? 'বর্ণনা' : 'জমির তথ্য';
  return [
    {
      fieldId: 'details',
      label,
      plainText: record.details.trim(),
      segments: [{ text: record.details.trim(), bold: false, italic: false }],
    },
  ];
}

export function formatRecordBlock(rec: PropertyRecord): string {
  const hasStructured = rec.fieldValues && rec.fieldValues.length > 0;
  const serializedFields = hasStructured
    ? JSON.stringify(
        rec.fieldValues.map((fv) => ({
          id: fv.fieldId,
          label: fv.label,
          text: fv.plainText,
          runs: fv.segments.map((s) => ({ t: s.text, b: s.bold, i: s.italic })),
        }))
      )
    : '';

  const lines = [
    '========================================',
    'PROPERTY RECORD',
    `RECORD_ID: ${rec.recordId}`,
    `DATE: ${rec.date}`,
    `TIME: ${rec.time}`,
    `CATEGORY: ${rec.category}`,
  ];
  if (hasStructured) {
    lines.push(`TITLE: ${rec.title.trim()}`);
  }
  lines.push(`MOBILE: ${rec.mobile.trim()}`);
  if (hasStructured) {
    lines.push(`FIELDS_JSON: ${serializedFields}`);
  }
  lines.push('DETAILS:');
  lines.push(rec.details.replace(/\r\n/g, '\n'));
  lines.push('');
  return lines.join('\n');
}

export function appendRecord(existing: string, rec: PropertyRecord): string {
  const block = formatRecordBlock(rec);
  if (!existing.trim()) return block;
  let prefix = existing;
  if (!prefix.endsWith('\n')) prefix += '\n';
  if (!prefix.endsWith('\n\n')) prefix += '\n';
  return `${prefix}${block}`;
}

export function removeRecordFromTxt(
  existing: string,
  targetRecordId: string
): { removed: boolean; updated: string } {
  if (!existing.trim()) return { removed: false, updated: '' };
  const targetUpper = targetRecordId.trim().toUpperCase();
  const blocks = existing
    .replace(/\r\n/g, '\n')
    .split(/^={10,}\s*$/m)
    .map((b) => b.trim())
    .filter(Boolean);

  let removed = false;
  const kept: string[] = [];
  for (const b of blocks) {
    const lines = b.split('\n');
    let idUpper = '';
    for (const line of lines) {
      const t = line.trim();
      if (t.startsWith('RECORD_ID:')) {
        idUpper = t.slice(10).trim().toUpperCase();
        break;
      }
      if (t === 'DETAILS:' || t === 'Details:') break;
    }
    if (idUpper === targetUpper) {
      removed = true;
    } else {
      kept.push(`========================================\n${b}\n`);
    }
  }
  return {
    removed,
    updated: removed ? kept.join('\n') : existing,
  };
}

export function parseTxtContent(
  content: string,
  fallbackCategory: CategoryCode,
  sourceFileName: string
): PropertyRecord[] {
  if (!content.trim()) return [];
  const normalized = content.replace(/\r\n/g, '\n');
  const blocks = normalized
    .split(/^={10,}\s*$/m)
    .map((b) => b.trim())
    .filter(Boolean);

  const out: PropertyRecord[] = [];
  for (const block of blocks) {
    const lines = block.split('\n');
    let recordId = '';
    let date = sourceFileName.replace(/\.txt$/, '');
    let time = '';
    let category = fallbackCategory;
    let title = '';
    let mobile = '';
    let fieldValues: PropertyFieldValue[] = [];
    const detailsLines: string[] = [];
    let inDetails = false;

    for (const line of lines) {
      if (inDetails) {
        detailsLines.push(line);
        continue;
      }
      const t = line.trim();
      if (!t || t === 'PROPERTY RECORD') continue;
      if (t.startsWith('RECORD_ID:')) recordId = t.slice(10).trim();
      else if (t.startsWith('DATE:')) date = t.slice(5).trim();
      else if (t.startsWith('TIME:')) time = t.slice(5).trim();
      else if (t.startsWith('CATEGORY:')) {
        const raw = t.slice(9).trim();
        if (raw === 'HOUSE_FOR_RENT' || raw === 'LAND_FOR_SALE') category = raw;
      } else if (t.startsWith('TITLE:')) title = t.slice(6).trim();
      else if (t.startsWith('MOBILE:')) mobile = t.slice(7).trim();
      else if (t.startsWith('FIELDS_JSON:')) {
        try {
          const parsed = JSON.parse(t.slice(12).trim()) as {
            id?: string;
            label?: string;
            text?: string;
            runs?: { t?: string; b?: boolean; i?: boolean }[];
          }[];
          if (Array.isArray(parsed)) {
            fieldValues = parsed
              .filter((p) => p && typeof p.id === 'string' && p.id.length > 0)
              .map((p) => {
                const plainText = typeof p.text === 'string' ? p.text : '';
                const runs = Array.isArray(p.runs)
                  ? p.runs
                      .filter((r) => r && typeof r.t === 'string' && r.t.length > 0)
                      .map((r) => ({
                        text: r.t ?? '',
                        bold: Boolean(r.b),
                        italic: Boolean(r.i),
                      }))
                  : [];
                return {
                  fieldId: p.id!,
                  label: typeof p.label === 'string' ? p.label : p.id!,
                  plainText,
                  segments:
                    runs.length > 0
                      ? mergeStyledSegments(runs)
                      : plainText
                        ? [{ text: plainText, bold: false, italic: false }]
                        : [],
                };
              });
          }
        } catch {
          fieldValues = [];
        }
      } else if (t === 'DETAILS:') {
        inDetails = true;
      }
    }

    if (recordId.toUpperCase().startsWith('PH_LAND_') || recordId.toUpperCase().startsWith('LAND_')) {
      category = 'LAND_FOR_SALE';
    } else if (
      recordId.toUpperCase().startsWith('PH_RENT_') ||
      recordId.toUpperCase().startsWith('HOUSE_')
    ) {
      category = 'HOUSE_FOR_RENT';
    }

    const details =
      detailsLines.join('\n').replace(/\n+$/, '') ||
      (fieldValues.length > 0 ? buildHumanReadableSummary(category, fieldValues) : '');

    if (mobile || details || fieldValues.length > 0) {
      out.push({
        recordId: recordId || `${category === 'HOUSE_FOR_RENT' ? 'PH_RENT' : 'PH_LAND'}_LEGACY`,
        date,
        time,
        category,
        title: title || getDefaultTitle(category),
        mobile,
        details,
        fieldValues,
        sourceFileName,
      });
    }
  }
  return out;
}
