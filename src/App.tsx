/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  Archive,
  ArrowLeft,
  Calendar,
  CheckCircle2,
  ChevronRight,
  Download,
  Eye,
  FileText,
  Folder,
  FolderOpen,
  Globe,
  Home,
  Image as ImageIcon,
  Info,
  Layers,
  MapPin,
  Palette,
  Phone,
  Send,
  Settings,
  Trash2,
  X,
} from 'lucide-react';

type CategoryCode = 'HOUSE_FOR_RENT' | 'LAND_FOR_SALE';
type NavTab = 'collect' | 'settings' | 'files';
type FilesSubTab = 'records' | 'bulk' | 'posters' | 'zip';
type ThemeMode = 'blue' | 'light' | 'dark';

interface PropertyRecord {
  recordId: string;
  date: string;
  time: string;
  category: CategoryCode;
  mobile: string;
  details: string;
  sourceFileName: string;
}

interface PosterEntry {
  recordId: string;
  fileName: string;
  category: CategoryCode;
  date: string;
  sourceTxtFile: string;
  dataUrl: string;
  createdAt: number;
}

interface ZipArchiveEntry {
  fileName: string;
  imageCount: number;
  txtCount: number;
  createdAt: string;
  contents: string[];
}

interface SimulatorState {
  authorizedFolder: string | null;
  autoJpgOnSubmit: boolean;
  themeMode: ThemeMode;
  houseSeq: number;
  landSeq: number;
  houseFiles: Record<string, string>;
  landFiles: Record<string, string>;
  posters: Record<string, PosterEntry>;
  zips: ZipArchiveEntry[];
}

const STORAGE_KEY = 'property_hub_offline_v2';

function getLocalDateStr(date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function getLocalTimeStr(date = new Date()): string {
  let hours = date.getHours();
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const ampm = hours >= 12 ? 'PM' : 'AM';
  hours = hours % 12;
  if (hours === 0) hours = 12;
  return `${String(hours).padStart(2, '0')}:${minutes} ${ampm}`;
}

function buildPosterFileName(category: CategoryCode, recordId: string): string {
  const prefix = category === 'HOUSE_FOR_RENT' ? 'HOUSE_' : 'LAND_';
  const posterPrefix = category === 'HOUSE_FOR_RENT' ? 'House_' : 'Land_';
  const suffix = recordId.startsWith(prefix) ? recordId.slice(prefix.length) : recordId;
  return `${posterPrefix}${suffix}.jpg`;
}

function formatRecordBlock(rec: PropertyRecord): string {
  return [
    '========================================',
    'PROPERTY RECORD',
    `RECORD_ID: ${rec.recordId}`,
    `DATE: ${rec.date}`,
    `TIME: ${rec.time}`,
    `CATEGORY: ${rec.category}`,
    `MOBILE: ${rec.mobile.trim()}`,
    'DETAILS:',
    rec.details.replace(/\r\n/g, '\n'),
    '',
  ].join('\n');
}

function appendRecord(existing: string, rec: PropertyRecord): string {
  const block = formatRecordBlock(rec);
  if (!existing.trim()) return block;
  let prefix = existing;
  if (!prefix.endsWith('\n')) prefix += '\n';
  if (!prefix.endsWith('\n\n')) prefix += '\n';
  return `${prefix}${block}`;
}

function parseTxtContent(
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
    let mobile = '';
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
      } else if (t.startsWith('MOBILE:')) mobile = t.slice(7).trim();
      else if (t === 'DETAILS:') inDetails = true;
    }

    if (mobile || detailsLines.length > 0) {
      out.push({
        recordId: recordId || `${category === 'HOUSE_FOR_RENT' ? 'HOUSE' : 'LAND'}_LEGACY`,
        date,
        time,
        category,
        mobile,
        details: detailsLines.join('\n').replace(/\n+$/, ''),
        sourceFileName,
      });
    }
  }
  return out;
}

function wrapLines(
  ctx: CanvasRenderingContext2D,
  text: string,
  maxWidth: number
): string[] {
  const paragraphs = text.replace(/\r\n/g, '\n').split('\n');
  const lines: string[] = [];
  for (const para of paragraphs) {
    if (!para.trim()) {
      lines.push('');
      continue;
    }
    const words = para.split(/\s+/);
    let current = '';
    for (const word of words) {
      const candidate = current ? `${current} ${word}` : word;
      if (ctx.measureText(candidate).width <= maxWidth) {
        current = candidate;
      } else {
        if (current) lines.push(current);
        current = word;
      }
    }
    if (current) lines.push(current);
  }
  return lines;
}

function renderPosterDataUrl(record: PropertyRecord): string {
  const width = 1440;
  const height = 1080;
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';

  const isHouse = record.category === 'HOUSE_FOR_RENT';

  if (isHouse) {
    // Deep architectural navy backdrop
    const bgGrad = ctx.createLinearGradient(0, 0, width, height);
    bgGrad.addColorStop(0, '#072259');
    bgGrad.addColorStop(1, '#0F3D9E');
    ctx.fillStyle = bgGrad;
    ctx.fillRect(0, 0, width, height);

    // Subtle geometric roof grid accents
    ctx.strokeStyle = 'rgba(147, 197, 253, 0.16)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(960, 0);
    ctx.lineTo(width, 260);
    ctx.moveTo(0, 820);
    ctx.lineTo(360, height);
    ctx.stroke();

    // Top Header Band
    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 64px "Plus Jakarta Sans", sans-serif';
    ctx.fillText('HOUSE FOR RENT', 80, 126);

    ctx.fillStyle = '#BFDBFE';
    ctx.font = '600 26px "Plus Jakarta Sans", sans-serif';
    ctx.fillText(`PROPERTY HUB  •  ${record.recordId}  •  ${record.date}`, 80, 174);

    // House emblem right
    ctx.strokeStyle = 'rgba(255,255,255,0.85)';
    ctx.lineWidth = 6;
    ctx.beginPath();
    ctx.moveTo(1235, 130);
    ctx.lineTo(1295, 76);
    ctx.lineTo(1355, 130);
    ctx.stroke();
    ctx.strokeRect(1253, 130, 84, 54);

    // Crisp elevated white card
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(72, 224, width - 144, 624, 34);
    ctx.fill();

    // Blue left accent bar
    ctx.fillStyle = '#1D4ED8';
    ctx.beginPath();
    ctx.roundRect(72, 264, 14, 544, 7);
    ctx.fill();

    ctx.fillStyle = '#0B3A96';
    ctx.font = 'bold 28px "Plus Jakarta Sans", sans-serif';
    ctx.fillText('PROPERTY DETAILS', 124, 294);

    ctx.strokeStyle = '#E2E8F0';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(124, 316);
    ctx.lineTo(width - 124, 316);
    ctx.stroke();

    // Dynamic text fit
    let fontSize = 38;
    let fittedLines: string[] = [];
    while (fontSize >= 18) {
      ctx.font = `500 ${fontSize}px "Plus Jakarta Sans", sans-serif`;
      fittedLines = wrapLines(ctx, record.details, width - 260);
      if (fittedLines.length * (fontSize * 1.42) <= 465) break;
      fontSize -= 2;
    }

    ctx.fillStyle = '#0F172A';
    ctx.font = `500 ${fontSize}px "Plus Jakarta Sans", sans-serif`;
    const lineHeight = fontSize * 1.42;
    let y = 376;
    for (const line of fittedLines) {
      if (y > 815) break;
      ctx.fillText(line, 124, y);
      y += lineHeight;
    }

    // Bottom Call CTA Bar
    ctx.fillStyle = '#1D4ED8';
    ctx.beginPath();
    ctx.roundRect(72, 892, width - 144, 124, 28);
    ctx.fill();

    ctx.fillStyle = '#DBEAFE';
    ctx.font = 'bold 24px "Plus Jakarta Sans", sans-serif';
    ctx.fillText('CONTACT / INQUIRY', 120, 942);

    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 48px "Plus Jakarta Sans", sans-serif';
    ctx.fillText(record.mobile, 120, 994);

    ctx.fillStyle = 'rgba(255,255,255,0.85)';
    ctx.font = '600 22px "Plus Jakarta Sans", sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText('Property Hub • Offline Verified', width - 116, 965);
    ctx.textAlign = 'left';
  } else {
    // LAND FOR SALE: Distinct Forest & Emerald Asymmetric Editorial Layout
    ctx.fillStyle = '#F3FAF5';
    ctx.fillRect(0, 0, width, height);

    // Left emerald forest pillar
    const leftGrad = ctx.createLinearGradient(0, 0, 0, height);
    leftGrad.addColorStop(0, '#065F46');
    leftGrad.addColorStop(1, '#047857');
    ctx.fillStyle = leftGrad;
    ctx.fillRect(0, 0, 440, height);

    // Surveyor corner brackets on left pillar
    ctx.strokeStyle = 'rgba(167, 243, 208, 0.55)';
    ctx.lineWidth = 4;
    ctx.strokeRect(42, 42, 356, height - 84);

    ctx.fillStyle = '#A7F3D0';
    ctx.font = 'bold 22px "JetBrains Mono", monospace';
    ctx.fillText('LAND LISTING', 76, 120);

    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 68px "Plus Jakarta Sans", sans-serif';
    ctx.fillText('LAND', 76, 210);
    ctx.fillText('FOR', 76, 284);
    ctx.fillText('SALE', 76, 358);

    ctx.fillStyle = '#D1FAE5';
    ctx.font = '600 26px "JetBrains Mono", monospace';
    ctx.fillText(record.recordId, 76, 440);
    ctx.fillStyle = 'rgba(209, 250, 229, 0.8)';
    ctx.font = '500 22px "Plus Jakarta Sans", sans-serif';
    ctx.fillText(record.date, 76, 480);

    // Contact badge inside left pillar
    ctx.fillStyle = '#064E3B';
    ctx.beginPath();
    ctx.roundRect(68, 800, 304, 176, 24);
    ctx.fill();

    ctx.fillStyle = '#6EE7B7';
    ctx.font = 'bold 20px "Plus Jakarta Sans", sans-serif';
    ctx.fillText('CALL OWNER / AGENT', 94, 852);

    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 34px "Plus Jakarta Sans", sans-serif';
    ctx.fillText(record.mobile, 94, 910);

    ctx.fillStyle = '#A7F3D0';
    ctx.font = '500 18px "Plus Jakarta Sans", sans-serif';
    ctx.fillText('Property Hub', 94, 952);

    // Right white editorial canvas
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(496, 72, width - 564, height - 144, 30);
    ctx.fill();
    ctx.strokeStyle = '#C7E8D2';
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.fillStyle = '#065F46';
    ctx.font = 'bold 30px "Plus Jakarta Sans", sans-serif';
    ctx.fillText('LAND & PLOT INFORMATION', 550, 152);

    ctx.fillStyle = '#10B981';
    ctx.fillRect(550, 174, 140, 6);

    let fontSize = 38;
    let fittedLines: string[] = [];
    while (fontSize >= 18) {
      ctx.font = `500 ${fontSize}px "Plus Jakarta Sans", sans-serif`;
      fittedLines = wrapLines(ctx, record.details, width - 680);
      if (fittedLines.length * (fontSize * 1.44) <= 710) break;
      fontSize -= 2;
    }

    ctx.fillStyle = '#0F172A';
    ctx.font = `500 ${fontSize}px "Plus Jakarta Sans", sans-serif`;
    const lineHeight = fontSize * 1.44;
    let y = 246;
    for (const line of fittedLines) {
      if (y > height - 125) break;
      ctx.fillText(line, 550, y);
      y += lineHeight;
    }
  }

  return canvas.toDataURL('image/jpeg', 0.92);
}

function validatePhone(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return 'Please enter a mobile number';
  let digits = 0;
  for (const ch of trimmed) {
    if (ch >= '0' && ch <= '9') digits += 1;
    else if (!['+', '-', ' ', '(', ')'].includes(ch)) return 'Enter a valid phone number';
  }
  if (digits < 5) return 'Enter at least 5 digits';
  return null;
}

const DEFAULT_STATE: SimulatorState = {
  authorizedFolder: 'Documents',
  autoJpgOnSubmit: true,
  themeMode: 'blue',
  houseSeq: 0,
  landSeq: 0,
  houseFiles: {},
  landFiles: {},
  posters: {},
  zips: [],
};

export default function App() {
  const [state, setState] = useState<SimulatorState>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        return { ...DEFAULT_STATE, ...(JSON.parse(raw) as Partial<SimulatorState>) };
      }
    } catch {
      // Ignore invalid localStorage
    }
    return DEFAULT_STATE;
  });

  const [activeTab, setActiveTab] = useState<NavTab>('collect');
  const [filesSubTab, setFilesSubTab] = useState<FilesSubTab>('records');
  const [activeLocalDate, setActiveLocalDate] = useState<string>(() => getLocalDateStr());

  // Preserved form inputs across tab switches
  const [houseMobile, setHouseMobile] = useState('');
  const [houseDetails, setHouseDetails] = useState('');
  const [houseErrors, setHouseErrors] = useState<{ mobile?: string; details?: string }>({});

  const [landMobile, setLandMobile] = useState('');
  const [landDetails, setLandDetails] = useState('');
  const [landErrors, setLandErrors] = useState<{ mobile?: string; details?: string }>({});

  const [selectedTxtFile, setSelectedTxtFile] = useState<{
    category: CategoryCode;
    fileName: string;
  } | null>(null);
  const [previewPoster, setPreviewPoster] = useState<PosterEntry | null>(null);
  const [includeTxtInZip, setIncludeTxtInZip] = useState(false);
  const [zipFilterCat, setZipFilterCat] = useState<'ALL' | CategoryCode>('ALL');
  const [zipDateFilter, setZipDateFilter] = useState<string>('ALL');

  const [bulkProgress, setBulkProgress] = useState<{
    total: number;
    skipped: number;
    succeeded: number;
    invalid: number;
    duplicateSkipped: number;
    running: boolean;
  }>({ total: 0, skipped: 0, succeeded: 0, invalid: 0, duplicateSkipped: 0, running: false });

  const [importSummary, setImportSummary] = useState<{
    fileCount: number;
    totalScanned: number;
    alreadyGenerated: number;
    pendingRecords: PropertyRecord[];
    invalidCount: number;
    duplicateSkipped: number;
    conflicts: {
      key: string;
      first: PropertyRecord;
      second: PropertyRecord;
    }[];
  } | null>(null);
  const [conflictChoiceMap, setConflictChoiceMap] = useState<Record<string, PropertyRecord>>({});
  const [showConflictModal, setShowConflictModal] = useState(false);
  const txtFileInputRef = useRef<HTMLInputElement | null>(null);

  const [topToast, setTopToast] = useState<{
    title: string;
    message: string;
    isHouse: boolean;
  } | null>(null);
  const toastTimerRef = useRef<number | null>(null);
  const lastTapRef = useRef<{ house: number; land: number }>({ house: 0, land: 0 });

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // Ignore quota issues
    }
  }, [state]);

  function showTopToast(title: string, message: string, isHouse = true) {
    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current);
    }
    setTopToast({ title, message, isHouse });
    toastTimerRef.current = window.setTimeout(() => {
      setTopToast(null);
      toastTimerRef.current = null;
    }, 2500);
  }

  const allRecords = useMemo(() => {
    const out: PropertyRecord[] = [];
    for (const [fileName, content] of Object.entries(state.houseFiles)) {
      out.push(...parseTxtContent(content, 'HOUSE_FOR_RENT', fileName));
    }
    for (const [fileName, content] of Object.entries(state.landFiles)) {
      out.push(...parseTxtContent(content, 'LAND_FOR_SALE', fileName));
    }
    return out.sort((a, b) => b.date.localeCompare(a.date) || b.recordId.localeCompare(a.recordId));
  }, [state.houseFiles, state.landFiles]);

  const postersList = useMemo(
    () => Object.values(state.posters).sort((a, b) => b.createdAt - a.createdAt),
    [state.posters]
  );

  function handleSubmit(category: CategoryCode, e: React.FormEvent) {
    e.preventDefault();
    const isHouse = category === 'HOUSE_FOR_RENT';
    const nowMs = Date.now();
    if (nowMs - (isHouse ? lastTapRef.current.house : lastTapRef.current.land) < 650) return;

    const mobile = isHouse ? houseMobile : landMobile;
    const details = isHouse ? houseDetails : landDetails;
    const mobileErr = validatePhone(mobile);
    const detailsErr = !details.trim() ? 'Please enter property details' : undefined;

    if (mobileErr || detailsErr) {
      if (isHouse) setHouseErrors({ mobile: mobileErr ?? undefined, details: detailsErr });
      else setLandErrors({ mobile: mobileErr ?? undefined, details: detailsErr });
      return;
    }

    if (isHouse) {
      setHouseErrors({});
      lastTapRef.current.house = nowMs;
    } else {
      setLandErrors({});
      lastTapRef.current.land = nowMs;
    }

    const nextSeq = (isHouse ? state.houseSeq : state.landSeq) + 1;
    const prefix = isHouse ? 'HOUSE_' : 'LAND_';
    const recordId = `${prefix}${String(nextSeq).padStart(6, '0')}`;
    const dateStr = activeLocalDate || getLocalDateStr();
    const timeStr = getLocalTimeStr();
    const fileName = `${dateStr}.txt`;

    const newRecord: PropertyRecord = {
      recordId,
      date: dateStr,
      time: timeStr,
      category,
      mobile: mobile.trim(),
      details,
      sourceFileName: fileName,
    };

    const existingTxt = isHouse ? state.houseFiles[fileName] ?? '' : state.landFiles[fileName] ?? '';
    const updatedTxt = appendRecord(existingTxt, newRecord);

    let generatedPoster: PosterEntry | null = null;
    if (state.autoJpgOnSubmit) {
      const posterName = buildPosterFileName(category, recordId);
      const dataUrl = renderPosterDataUrl(newRecord);
      generatedPoster = {
        recordId,
        fileName: posterName,
        category,
        date: dateStr,
        sourceTxtFile: fileName,
        dataUrl,
        createdAt: Date.now(),
      };
    }

    setState((prev) => ({
      ...prev,
      houseSeq: isHouse ? nextSeq : prev.houseSeq,
      landSeq: !isHouse ? nextSeq : prev.landSeq,
      houseFiles: isHouse
        ? { ...prev.houseFiles, [fileName]: updatedTxt }
        : prev.houseFiles,
      landFiles: !isHouse
        ? { ...prev.landFiles, [fileName]: updatedTxt }
        : prev.landFiles,
      posters: generatedPoster
        ? { ...prev.posters, [`${category}|${recordId}`]: generatedPoster }
        : prev.posters,
    }));

    setSelectedTxtFile({ category, fileName });
    if (isHouse) {
      setHouseMobile('');
      setHouseDetails('');
    } else {
      setLandMobile('');
      setLandDetails('');
    }

    showTopToast(
      generatedPoster
        ? 'Property saved and poster generated successfully!'
        : `${recordId} Saved to TXT`,
      generatedPoster
        ? `${recordId} • ${generatedPoster.fileName}`
        : `Property Hub/${isHouse ? 'House for Rent' : 'Land for Sale'}/${fileName}`,
      isHouse
    );
  }

  async function handleImportTxtFilesSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const fileList = e.target.files;
    if (!fileList || fileList.length === 0) return;

    const firstByKey = new Map<string, PropertyRecord>();
    const conflictedKeys = new Set<string>();
    const conflicts: { key: string; first: PropertyRecord; second: PropertyRecord }[] = [];
    let totalValid = 0;
    let invalidCount = 0;
    let duplicateSkipped = 0;

    for (const file of Array.from(fileList)) {
      const content = await file.text();
      const lower = file.name.toLowerCase();
      const hint: CategoryCode =
        lower.includes('land') || lower.includes('sale') ? 'LAND_FOR_SALE' : 'HOUSE_FOR_RENT';
      const parsed = parseTxtContent(content, hint, file.name);
      const blockCount = content
        .split(/^={10,}\s*$/m)
        .map((b) => b.trim())
        .filter(Boolean).length;
      if (blockCount > parsed.length) {
        invalidCount += blockCount - parsed.length;
      }

      for (const rec of parsed) {
        totalValid += 1;
        const key = rec.recordId.trim().toUpperCase();
        const existing = firstByKey.get(key);
        if (!existing) {
          firstByKey.set(key, rec);
        } else if (
          existing.category !== rec.category ||
          existing.mobile.trim() !== rec.mobile.trim() ||
          existing.details.trim() !== rec.details.trim()
        ) {
          conflictedKeys.add(key);
          conflicts.push({ key, first: existing, second: rec });
        } else {
          duplicateSkipped += 1;
        }
      }
    }

    let alreadyGenerated = 0;
    const pendingRecords: PropertyRecord[] = [];
    for (const [key, rec] of firstByKey.entries()) {
      if (conflictedKeys.has(key)) continue;
      if (state.posters[`${rec.category}|${rec.recordId}`]) {
        alreadyGenerated += 1;
      } else {
        pendingRecords.push(rec);
      }
    }

    setConflictChoiceMap({});
    setImportSummary({
      fileCount: fileList.length,
      totalScanned: totalValid + invalidCount,
      alreadyGenerated,
      pendingRecords,
      invalidCount,
      duplicateSkipped,
      conflicts,
    });
    e.target.value = '';
  }

  function handleGenerateMissingFromImport() {
    if (!importSummary) return;
    const candidates = [
      ...importSummary.pendingRecords,
      ...Object.values(conflictChoiceMap),
    ];

    let skipped = importSummary.alreadyGenerated;
    let succeeded = 0;
    const newPosters = { ...state.posters };
    const seen = new Set<string>();

    for (const rec of candidates) {
      const idKey = rec.recordId.trim().toUpperCase();
      if (seen.has(idKey)) continue;
      seen.add(idKey);

      const posterKey = `${rec.category}|${rec.recordId}`;
      if (newPosters[posterKey]) {
        skipped += 1;
        continue;
      }
      const dataUrl = renderPosterDataUrl(rec);
      newPosters[posterKey] = {
        recordId: rec.recordId,
        fileName: buildPosterFileName(rec.category, rec.recordId),
        category: rec.category,
        date: rec.date,
        sourceTxtFile: rec.sourceFileName,
        dataUrl,
        createdAt: Date.now() + succeeded,
      };
      succeeded += 1;
    }

    setState((prev) => ({ ...prev, posters: newPosters }));
    setBulkProgress({
      total: importSummary.totalScanned,
      skipped,
      succeeded,
      invalid: importSummary.invalidCount,
      duplicateSkipped: importSummary.duplicateSkipped,
      running: false,
    });
    setImportSummary((prev) =>
      prev
        ? {
            ...prev,
            alreadyGenerated: skipped,
            pendingRecords: [],
          }
        : null
    );
    showTopToast('Missing Posters Generated', `${succeeded} created, ${skipped} skipped`, true);
  }

  function handleBulkGenerate() {
    if (allRecords.length === 0) {
      showTopToast('No Records Found', 'Collect property records first.', true);
      return;
    }

    let skipped = 0;
    let succeeded = 0;
    const newPosters = { ...state.posters };

    for (const rec of allRecords) {
      const key = `${rec.category}|${rec.recordId}`;
      if (newPosters[key]) {
        skipped += 1;
        continue;
      }
      const dataUrl = renderPosterDataUrl(rec);
      newPosters[key] = {
        recordId: rec.recordId,
        fileName: buildPosterFileName(rec.category, rec.recordId),
        category: rec.category,
        date: rec.date,
        sourceTxtFile: rec.sourceFileName,
        dataUrl,
        createdAt: Date.now() + succeeded,
      };
      succeeded += 1;
    }

    setState((prev) => ({ ...prev, posters: newPosters }));
    setBulkProgress({
      total: allRecords.length,
      skipped,
      succeeded,
      invalid: 0,
      duplicateSkipped: 0,
      running: false,
    });
    showTopToast(
      'Bulk Generation Complete',
      `${succeeded} generated, ${skipped} already existed`,
      true
    );
  }

  function handleCreateZipExport() {
    const eligiblePosters = postersList.filter((p) => {
      if (zipFilterCat !== 'ALL' && p.category !== zipFilterCat) return false;
      if (zipDateFilter !== 'ALL' && p.date !== zipDateFilter) return false;
      return true;
    });

    if (eligiblePosters.length === 0) {
      showTopToast('No Posters Selected', 'Generate posters before creating a ZIP.', false);
      return;
    }

    const catSlug =
      zipFilterCat === 'ALL'
        ? 'All_Posters'
        : zipFilterCat === 'HOUSE_FOR_RENT'
          ? 'House_Rent'
          : 'Land_Sale';
    const dateSlug = zipDateFilter === 'ALL' ? 'All_Dates' : zipDateFilter;
    const zipName = `Property_Hub_${catSlug}_${dateSlug}.zip`;

    const contents = eligiblePosters.map(
      (p) => `${p.category === 'HOUSE_FOR_RENT' ? 'House for Rent' : 'Land for Sale'}/${p.fileName}`
    );

    let txtCount = 0;
    if (includeTxtInZip) {
      if (zipFilterCat === 'ALL' || zipFilterCat === 'HOUSE_FOR_RENT') {
        for (const f of Object.keys(state.houseFiles)) {
          if (zipDateFilter === 'ALL' || f.startsWith(zipDateFilter)) {
            contents.push(`TXT Records/House for Rent/${f}`);
            txtCount += 1;
          }
        }
      }
      if (zipFilterCat === 'ALL' || zipFilterCat === 'LAND_FOR_SALE') {
        for (const f of Object.keys(state.landFiles)) {
          if (zipDateFilter === 'ALL' || f.startsWith(zipDateFilter)) {
            contents.push(`TXT Records/Land for Sale/${f}`);
            txtCount += 1;
          }
        }
      }
    }

    const entry: ZipArchiveEntry = {
      fileName: zipName,
      imageCount: eligiblePosters.length,
      txtCount,
      createdAt: `${getLocalDateStr()} ${getLocalTimeStr()}`,
      contents,
    };

    setState((prev) => ({
      ...prev,
      zips: [entry, ...prev.zips.filter((z) => z.fileName !== zipName)],
    }));
    showTopToast('ZIP Archive Created', `${zipName} (${eligiblePosters.length} JPGs)`, true);
  }

  const isDark = state.themeMode === 'dark';
  const phoneSurface = isDark
    ? 'bg-[#0F172A] text-slate-100 ring-slate-800'
    : state.themeMode === 'light'
      ? 'bg-[#F8FAFC] text-[#0B1F44] ring-slate-200'
      : 'bg-[#EDF2FA] text-[#0B1F44] ring-[#D4E0F3]';
  const cardSurface = isDark
    ? 'bg-[#1E293B] border-slate-700 text-slate-100'
    : 'bg-white border-[#D8E2F2] text-[#0B1F44]';
  const inputSurface = isDark
    ? 'bg-[#0B1120] border-slate-700 text-slate-100 placeholder-slate-500'
    : 'bg-white border-[#C4D3E8] text-[#0B1F44] placeholder-[#7A889E]';

  const activeTxtContent = selectedTxtFile
    ? selectedTxtFile.category === 'HOUSE_FOR_RENT'
      ? state.houseFiles[selectedTxtFile.fileName]
      : state.landFiles[selectedTxtFile.fileName]
    : null;

  return (
    <div className="min-h-screen bg-[#EEF3FB] text-[#0B1F44]">
      <div className="mx-auto max-w-7xl px-4 py-6 lg:px-8">
        <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
          {/* LEFT: 3-TAB MOBILE PREVIEW */}
          <div className="lg:col-span-5">
            <div
              className={`relative mx-auto flex h-[800px] max-w-[420px] flex-col overflow-hidden rounded-[28px] shadow-xl ring-1 ${phoneSurface}`}
            >
              {/* Non-blocking Top-Sliding Toast */}
              <div
                aria-live="polite"
                className={`pointer-events-none absolute inset-x-3.5 top-2.5 z-30 transition-all duration-200 ${
                  topToast ? 'translate-y-0 opacity-100' : '-translate-y-full opacity-0'
                }`}
              >
                {topToast && (
                  <div className="flex items-center gap-3 rounded-2xl border border-slate-700 bg-[#0F172A] px-3.5 py-2.5 text-white shadow-lg">
                    <CheckCircle2
                      className={`h-5 w-5 shrink-0 ${
                        topToast.isHouse ? 'text-blue-400' : 'text-emerald-400'
                      }`}
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[13px] font-bold">{topToast.title}</p>
                      <p className="mt-0.5 truncate font-mono text-[11px] text-slate-300">
                        {topToast.message}
                      </p>
                    </div>
                  </div>
                )}
              </div>

              {/* Consistent Property Hub Top Header */}
              <header className="shrink-0 rounded-b-[24px] bg-gradient-to-r from-[#062D80] to-[#0D47B8] px-4 py-3.5 text-white shadow-sm">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex h-10 w-10 items-center justify-center">
                    <Home className="h-8 w-8 text-white" />
                  </div>
                  <div className="text-center">
                    <h1 className="text-[22px] font-bold leading-tight text-white">
                      Property Hub
                    </h1>
                    <p className="mt-0.5 text-[11.5px] tracking-wide text-[#D7E4FF]">
                      Collect • Generate • Share
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setActiveTab('settings')}
                    aria-label="Settings"
                    className="flex h-10 w-10 items-center justify-center rounded-full text-white hover:bg-white/15"
                  >
                    <Settings className="h-5 w-5" />
                  </button>
                </div>
              </header>

              {/* Active Fragment Content */}
              <div className="min-h-0 flex-1 overflow-y-auto">
                {activeTab === 'collect' && (
                  <div className="space-y-4 p-3.5">
                    {/* HOUSE FOR RENT */}
                    <section className={`overflow-hidden rounded-[20px] border shadow-sm ${cardSurface}`}>
                      <div className="flex h-[74px] items-center bg-gradient-to-r from-[#06328E] to-[#1565C0] px-4 text-white">
                        <Home className="h-7 w-7" />
                        <div className="mx-3 h-9 w-px bg-white/40" />
                        <div>
                          <h2 className="text-[16px] font-bold tracking-wide">HOUSE FOR RENT</h2>
                          <p className="text-[12px] text-[#D7E4FF]">Find the right tenant</p>
                        </div>
                      </div>
                      <form onSubmit={(e) => handleSubmit('HOUSE_FOR_RENT', e)} className="space-y-3 p-4">
                        <div>
                          <label className="mb-1 block text-[13px] font-bold">Mobile Number</label>
                          <div className="relative">
                            <Phone className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-blue-600" />
                            <input
                              type="tel"
                              value={houseMobile}
                              onChange={(e) => setHouseMobile(e.target.value)}
                              placeholder="Enter mobile number"
                              className={`h-10 w-full rounded-xl border pl-9 pr-3 text-[13.5px] focus:border-blue-600 focus:outline-none ${inputSurface}`}
                            />
                          </div>
                          {houseErrors.mobile && (
                            <p className="mt-1 text-xs text-red-500">{houseErrors.mobile}</p>
                          )}
                        </div>
                        <div>
                          <label className="mb-1 block text-[13px] font-bold">Details</label>
                          <textarea
                            rows={3}
                            value={houseDetails}
                            onChange={(e) => setHouseDetails(e.target.value)}
                            placeholder="Enter address, location, monthly rent, bedrooms, facilities, floor info, etc."
                            className={`w-full resize-none rounded-xl border p-3 text-[13.5px] focus:border-blue-600 focus:outline-none ${inputSurface}`}
                          />
                          {houseErrors.details && (
                            <p className="mt-1 text-xs text-red-500">{houseErrors.details}</p>
                          )}
                        </div>
                        <button
                          type="submit"
                          className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#0B41AD] text-[14px] font-bold text-white hover:bg-[#093691]"
                        >
                          <Send className="h-4 w-4" />
                          <span>Submit House for Rent</span>
                        </button>
                      </form>
                    </section>

                    {/* LAND FOR SALE */}
                    <section className={`overflow-hidden rounded-[20px] border shadow-sm ${cardSurface}`}>
                      <div className="flex h-[74px] items-center bg-gradient-to-r from-[#007E2F] to-[#14AE47] px-4 text-white">
                        <MapPin className="h-7 w-7" />
                        <div className="mx-3 h-9 w-px bg-white/40" />
                        <div>
                          <h2 className="text-[16px] font-bold tracking-wide">LAND FOR SALE</h2>
                          <p className="text-[12px] text-[#E3FFE9]">Sell Your Land Easily</p>
                        </div>
                      </div>
                      <form onSubmit={(e) => handleSubmit('LAND_FOR_SALE', e)} className="space-y-3 p-4">
                        <div>
                          <label className="mb-1 block text-[13px] font-bold">Mobile Number</label>
                          <div className="relative">
                            <Phone className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-emerald-600" />
                            <input
                              type="tel"
                              value={landMobile}
                              onChange={(e) => setLandMobile(e.target.value)}
                              placeholder="Enter mobile number"
                              className={`h-10 w-full rounded-xl border pl-9 pr-3 text-[13.5px] focus:border-emerald-600 focus:outline-none ${inputSurface}`}
                            />
                          </div>
                          {landErrors.mobile && (
                            <p className="mt-1 text-xs text-red-500">{landErrors.mobile}</p>
                          )}
                        </div>
                        <div>
                          <label className="mb-1 block text-[13px] font-bold">Details</label>
                          <textarea
                            rows={3}
                            value={landDetails}
                            onChange={(e) => setLandDetails(e.target.value)}
                            placeholder="Enter location, land area, price, road access, land type, facilities, etc."
                            className={`w-full resize-none rounded-xl border p-3 text-[13.5px] focus:border-emerald-600 focus:outline-none ${inputSurface}`}
                          />
                          {landErrors.details && (
                            <p className="mt-1 text-xs text-red-500">{landErrors.details}</p>
                          )}
                        </div>
                        <button
                          type="submit"
                          className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#0B8A38] text-[14px] font-bold text-white hover:bg-[#09732E]"
                        >
                          <Send className="h-4 w-4" />
                          <span>Submit Land for Sale</span>
                        </button>
                      </form>
                    </section>
                  </div>
                )}

                {activeTab === 'settings' && (
                  <div className="space-y-3 p-4">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => setActiveTab('collect')}
                        className="rounded-lg p-1.5 hover:bg-black/5"
                        aria-label="Back"
                      >
                        <ArrowLeft className="h-5 w-5" />
                      </button>
                      <h2 className="text-[19px] font-bold">Settings</h2>
                    </div>

                    <div className={`flex items-center gap-3 rounded-2xl border p-3.5 ${cardSurface}`}>
                      <Folder className="h-7 w-7 shrink-0 text-blue-600" />
                      <div className="min-w-0 flex-1">
                        <p className="text-[14px] font-bold">Storage Location</p>
                        <p className="text-xs opacity-75">Property Hub</p>
                        <p className="truncate font-mono text-[11px] opacity-65">
                          /{state.authorizedFolder ?? 'Documents'}/Property Hub
                        </p>
                      </div>
                      <ChevronRight className="h-4 w-4 opacity-50" />
                    </div>

                    <div className={`flex items-center gap-3 rounded-2xl border p-3.5 ${cardSurface}`}>
                      <ImageIcon className="h-7 w-7 shrink-0 text-blue-600" />
                      <div className="min-w-0 flex-1">
                        <p className="text-[14px] font-bold">Auto JPG Image by Submit</p>
                        <p className="text-[11.5px] opacity-75">
                          Automatically generate a poster after every submission.
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() =>
                          setState((prev) => ({
                            ...prev,
                            autoJpgOnSubmit: !prev.autoJpgOnSubmit,
                          }))
                        }
                        className={`h-7 w-12 rounded-full p-0.5 transition-colors ${
                          state.autoJpgOnSubmit ? 'bg-blue-600' : 'bg-slate-400'
                        }`}
                        aria-label="Toggle Auto JPG"
                      >
                        <span
                          className={`block h-6 w-6 rounded-full bg-white shadow transition-transform ${
                            state.autoJpgOnSubmit ? 'translate-x-5' : 'translate-x-0'
                          }`}
                        />
                      </button>
                    </div>

                    <div className={`rounded-2xl border p-3.5 ${cardSurface}`}>
                      <div className="flex items-center gap-3">
                        <Palette className="h-7 w-7 shrink-0 text-blue-600" />
                        <div className="flex-1">
                          <p className="text-[14px] font-bold">App Theme</p>
                          <div className="mt-2 flex gap-2">
                            {(['blue', 'light', 'dark'] as ThemeMode[]).map((mode) => (
                              <button
                                key={mode}
                                type="button"
                                onClick={() => setState((prev) => ({ ...prev, themeMode: mode }))}
                                className={`rounded-lg border px-2.5 py-1 text-xs font-semibold capitalize ${
                                  state.themeMode === mode
                                    ? 'border-blue-600 bg-blue-600 text-white'
                                    : 'border-slate-300 text-slate-600'
                                }`}
                              >
                                {mode === 'blue' ? 'Blue (Default)' : mode}
                              </button>
                            ))}
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className={`flex items-center gap-3 rounded-2xl border p-3.5 ${cardSurface}`}>
                      <Globe className="h-7 w-7 shrink-0 text-blue-600" />
                      <div className="flex-1">
                        <p className="text-[14px] font-bold">Language</p>
                        <p className="text-xs opacity-75">English</p>
                      </div>
                    </div>

                    <div className={`flex items-start gap-3 rounded-2xl border p-3.5 ${cardSurface}`}>
                      <Info className="mt-0.5 h-7 w-7 shrink-0 text-blue-600" />
                      <div>
                        <p className="text-[14px] font-bold">About Property Hub</p>
                        <p className="text-xs opacity-75">Version 1.0.0</p>
                        <p className="text-xs opacity-75">
                          A complete offline property management tool.
                        </p>
                      </div>
                    </div>

                    <div className="rounded-xl bg-blue-500/10 px-3.5 py-2.5 text-[11.5px] leading-relaxed">
                      Each submission will be saved in TXT file and optionally generate JPG poster
                      based on your settings.
                    </div>

                    <button
                      type="button"
                      onClick={() => setActiveTab('collect')}
                      className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#0B41AD] text-sm font-bold text-white"
                    >
                      <span>Back to Home</span>
                    </button>
                  </div>
                )}

                {activeTab === 'files' && (
                  <div className="flex h-full flex-col">
                    <div className={`grid grid-cols-4 border-b text-[11.5px] font-semibold ${cardSurface}`}>
                      {(
                        [
                          ['records', 'Records'],
                          ['bulk', 'Bulk'],
                          ['posters', 'Posters'],
                          ['zip', 'ZIP'],
                        ] as [FilesSubTab, string][]
                      ).map(([key, label]) => (
                        <button
                          key={key}
                          type="button"
                          onClick={() => setFilesSubTab(key)}
                          className={`border-b-2 py-2.5 text-center ${
                            filesSubTab === key
                              ? 'border-blue-600 text-blue-600'
                              : 'border-transparent opacity-65'
                          }`}
                        >
                          {label}
                        </button>
                      ))}
                    </div>

                    <div className="flex-1 space-y-3 overflow-y-auto p-3.5">
                      {filesSubTab === 'records' && (
                        <>
                          {allRecords.length === 0 ? (
                            <p className="py-10 text-center text-xs opacity-70">
                              No saved property records yet.
                            </p>
                          ) : (
                            allRecords.map((rec) => {
                              const hasPoster = Boolean(
                                state.posters[`${rec.category}|${rec.recordId}`]
                              );
                              return (
                                <div
                                  key={rec.recordId}
                                  className={`rounded-xl border p-3 ${cardSurface}`}
                                >
                                  <div className="flex items-center justify-between text-xs">
                                    <span
                                      className={`font-mono font-bold ${
                                        rec.category === 'HOUSE_FOR_RENT'
                                          ? 'text-blue-600'
                                          : 'text-emerald-600'
                                      }`}
                                    >
                                      {rec.recordId}
                                    </span>
                                    <span className="opacity-70">{rec.date}</span>
                                  </div>
                                  <p className="mt-1 text-xs font-semibold">{rec.mobile}</p>
                                  <p className="mt-1 line-clamp-2 whitespace-pre-wrap text-xs opacity-75">
                                    {rec.details}
                                  </p>
                                  <div className="mt-2 flex items-center justify-between text-[11px]">
                                    <span className={hasPoster ? 'text-emerald-600' : 'text-amber-600'}>
                                      {hasPoster ? 'Poster Ready' : 'Poster Missing'}
                                    </span>
                                    {!hasPoster ? (
                                      <button
                                        type="button"
                                        onClick={() => {
                                          const dataUrl = renderPosterDataUrl(rec);
                                          const entry: PosterEntry = {
                                            recordId: rec.recordId,
                                            fileName: buildPosterFileName(rec.category, rec.recordId),
                                            category: rec.category,
                                            date: rec.date,
                                            sourceTxtFile: rec.sourceFileName,
                                            dataUrl,
                                            createdAt: Date.now(),
                                          };
                                          setState((prev) => ({
                                            ...prev,
                                            posters: {
                                              ...prev.posters,
                                              [`${rec.category}|${rec.recordId}`]: entry,
                                            },
                                          }));
                                          showTopToast(
                                            'Poster Generated',
                                            entry.fileName,
                                            rec.category === 'HOUSE_FOR_RENT'
                                          );
                                        }}
                                        className="font-semibold text-blue-600 hover:underline"
                                      >
                                        Generate Poster
                                      </button>
                                    ) : (
                                      <button
                                        type="button"
                                        onClick={() =>
                                          setPreviewPoster(
                                            state.posters[`${rec.category}|${rec.recordId}`]
                                          )
                                        }
                                        className="font-semibold text-blue-600 hover:underline"
                                      >
                                        View Poster
                                      </button>
                                    )}
                                  </div>
                                </div>
                              );
                            })
                          )}
                        </>
                      )}

                      {filesSubTab === 'bulk' && (
                        <div className="space-y-3">
                          <input
                            ref={txtFileInputRef}
                            type="file"
                            accept=".txt,text/plain"
                            multiple
                            onChange={handleImportTxtFilesSelected}
                            className="hidden"
                          />
                          <div className={`space-y-2.5 rounded-2xl border p-4 ${cardSurface}`}>
                            <h3 className="text-sm font-bold">Import Saved TXT Files</h3>
                            <p className="text-xs opacity-75">
                              Works independently of Auto JPG. Existing valid posters are skipped by
                              RECORD_ID without modifying source TXT files.
                            </p>
                            <button
                              type="button"
                              onClick={() => txtFileInputRef.current?.click()}
                              className="flex h-10 w-full items-center justify-center gap-1.5 rounded-xl bg-[#0B8A38] text-xs font-bold text-white"
                            >
                              <FileText className="h-4 w-4" />
                              <span>Import TXT Files</span>
                            </button>
                          </div>

                          {importSummary && (
                            <div className="space-y-2 rounded-2xl border border-blue-500/40 bg-blue-500/10 p-3.5 text-xs">
                              <p className="font-bold">
                                Imported {importSummary.fileCount} TXT file(s)
                              </p>
                              <p>Total Records: {importSummary.totalScanned}</p>
                              <p>Already Generated: {importSummary.alreadyGenerated}</p>
                              <p>
                                Pending Generation:{' '}
                                {importSummary.pendingRecords.length +
                                  Object.keys(conflictChoiceMap).length}
                              </p>
                              <p>Invalid Records: {importSummary.invalidCount}</p>
                              <p>Duplicate RECORD_IDs Skipped: {importSummary.duplicateSkipped}</p>
                              {importSummary.conflicts.length > 0 && (
                                <button
                                  type="button"
                                  onClick={() => setShowConflictModal(true)}
                                  className="h-8 w-full rounded-lg border border-amber-500 bg-amber-500/15 text-xs font-bold text-amber-700"
                                >
                                  Review Conflicts ({importSummary.conflicts.length})
                                </button>
                              )}
                              <button
                                type="button"
                                disabled={
                                  importSummary.pendingRecords.length +
                                    Object.keys(conflictChoiceMap).length ===
                                  0
                                }
                                onClick={handleGenerateMissingFromImport}
                                className="h-9 w-full rounded-xl bg-blue-600 text-xs font-bold text-white disabled:opacity-50"
                              >
                                Generate Missing Posters (
                                {importSummary.pendingRecords.length +
                                  Object.keys(conflictChoiceMap).length}
                                )
                              </button>
                            </div>
                          )}

                          <div className={`space-y-3 rounded-2xl border p-4 ${cardSurface}`}>
                            <div className="flex items-center gap-2">
                              <Layers className="h-5 w-5 text-blue-600" />
                              <h3 className="text-sm font-bold">Bulk Poster Generator</h3>
                            </div>
                            <button
                              type="button"
                              onClick={handleBulkGenerate}
                              className="h-10 w-full rounded-xl bg-blue-600 text-xs font-bold text-white"
                            >
                              Start Generation ({allRecords.length} records)
                            </button>
                            {bulkProgress.total > 0 && (
                              <div className="space-y-0.5 rounded-xl bg-blue-500/10 p-3 text-xs">
                                <p>Total records scanned: {bulkProgress.total}</p>
                                <p>Existing posters skipped: {bulkProgress.skipped}</p>
                                <p>New posters generated: {bulkProgress.succeeded}</p>
                                <p>Invalid records: {bulkProgress.invalid}</p>
                                <p>Duplicate records skipped: {bulkProgress.duplicateSkipped}</p>
                              </div>
                            )}
                            <div className="grid grid-cols-2 gap-2">
                              <button
                                type="button"
                                onClick={() => setFilesSubTab('posters')}
                                className="h-9 rounded-xl bg-emerald-600 px-2 text-[11px] font-bold text-white"
                              >
                                View Generated Posters ({postersList.length})
                              </button>
                              <button
                                type="button"
                                onClick={() => setFilesSubTab('zip')}
                                className="h-9 rounded-xl border border-blue-600 px-2 text-[11px] font-bold text-blue-600"
                              >
                                Export as ZIP
                              </button>
                            </div>
                            <button
                              type="button"
                              onClick={() => txtFileInputRef.current?.click()}
                              className="h-8 w-full rounded-xl border border-slate-300 text-[11px] font-semibold"
                            >
                              Import More TXT Files
                            </button>
                          </div>
                        </div>
                      )}

                      {filesSubTab === 'posters' && (
                        <>
                          {postersList.length === 0 ? (
                            <p className="py-10 text-center text-xs opacity-70">
                              No generated JPG posters yet.
                            </p>
                          ) : (
                            postersList.map((poster) => (
                              <div
                                key={`${poster.category}|${poster.recordId}`}
                                className={`overflow-hidden rounded-2xl border ${cardSurface}`}
                              >
                                <img
                                  src={poster.dataUrl}
                                  alt={poster.fileName}
                                  className="aspect-[4/3] w-full bg-slate-950 object-contain"
                                />
                                <div className="p-3">
                                  <div className="flex items-center justify-between text-xs font-bold">
                                    <span className="font-mono">{poster.recordId}</span>
                                    <span
                                      className={
                                        poster.category === 'HOUSE_FOR_RENT'
                                          ? 'text-blue-600'
                                          : 'text-emerald-600'
                                      }
                                    >
                                      {poster.fileName}
                                    </span>
                                  </div>
                                  <div className="mt-2 flex gap-2">
                                    <button
                                      type="button"
                                      onClick={() => setPreviewPoster(poster)}
                                      className="flex flex-1 items-center justify-center gap-1 rounded-lg border border-slate-300 py-1.5 text-xs font-semibold"
                                    >
                                      <Eye className="h-3.5 w-3.5" />
                                      <span>View</span>
                                    </button>
                                    <a
                                      href={poster.dataUrl}
                                      download={poster.fileName}
                                      className="flex flex-1 items-center justify-center gap-1 rounded-lg bg-blue-600 py-1.5 text-xs font-semibold text-white"
                                    >
                                      <Download className="h-3.5 w-3.5" />
                                      <span>Save JPG</span>
                                    </a>
                                  </div>
                                </div>
                              </div>
                            ))
                          )}
                        </>
                      )}

                      {filesSubTab === 'zip' && (
                        <div className="space-y-3">
                          <div className={`space-y-2.5 rounded-2xl border p-3.5 ${cardSurface}`}>
                            <div className="flex items-center gap-2">
                              <Archive className="h-4 w-4 text-blue-600" />
                              <h3 className="text-sm font-bold">ZIP Exports</h3>
                            </div>
                            <select
                              value={zipFilterCat}
                              onChange={(e) =>
                                setZipFilterCat(e.target.value as 'ALL' | CategoryCode)
                              }
                              className={`h-9 w-full rounded-lg border px-2 text-xs ${inputSurface}`}
                            >
                              <option value="ALL">All Categories</option>
                              <option value="HOUSE_FOR_RENT">House for Rent</option>
                              <option value="LAND_FOR_SALE">Land for Sale</option>
                            </select>
                            <select
                              value={zipDateFilter}
                              onChange={(e) => setZipDateFilter(e.target.value)}
                              className={`h-9 w-full rounded-lg border px-2 text-xs ${inputSurface}`}
                            >
                              <option value="ALL">All Dates</option>
                              <option value={activeLocalDate}>{activeLocalDate}</option>
                            </select>
                            <label className="flex items-center gap-2 text-xs">
                              <input
                                type="checkbox"
                                checked={includeTxtInZip}
                                onChange={(e) => setIncludeTxtInZip(e.target.checked)}
                              />
                              <span>Include TXT records in archive</span>
                            </label>
                            <button
                              type="button"
                              onClick={handleCreateZipExport}
                              className="h-10 w-full rounded-xl bg-blue-600 text-xs font-bold text-white"
                            >
                              Create ZIP Archive
                            </button>
                          </div>

                          {state.zips.map((z) => (
                            <div key={z.fileName} className={`rounded-xl border p-3 ${cardSurface}`}>
                              <p className="truncate font-mono text-xs font-bold">{z.fileName}</p>
                              <p className="mt-0.5 text-[11px] opacity-75">
                                {z.imageCount} JPGs{z.txtCount > 0 ? ` + ${z.txtCount} TXTs` : ''} •{' '}
                                {z.createdAt}
                              </p>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>

              {/* Persistent 3-Destination Bottom Navigation */}
              <nav
                className={`grid shrink-0 grid-cols-3 border-t py-1.5 text-[11px] font-semibold ${
                  isDark
                    ? 'border-slate-800 bg-[#0B1120] text-slate-400'
                    : 'border-slate-200 bg-white text-slate-600'
                }`}
              >
                <button
                  type="button"
                  onClick={() => setActiveTab('collect')}
                  className={`flex flex-col items-center gap-0.5 py-1 ${
                    activeTab === 'collect' ? 'text-blue-600' : ''
                  }`}
                >
                  <Home className="h-5 w-5" />
                  <span>Collect Data</span>
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab('settings')}
                  className={`flex flex-col items-center gap-0.5 py-1 ${
                    activeTab === 'settings' ? 'text-blue-600' : ''
                  }`}
                >
                  <Settings className="h-5 w-5" />
                  <span>Settings</span>
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab('files')}
                  className={`flex flex-col items-center gap-0.5 py-1 ${
                    activeTab === 'files' ? 'text-blue-600' : ''
                  }`}
                >
                  <FolderOpen className="h-5 w-5" />
                  <span>Generated Files</span>
                </button>
              </nav>
            </div>
          </div>

          {/* RIGHT: OFFLINE STORAGE & ANDROID ARCHITECTURE INSPECTOR */}
          <div className="space-y-6 lg:col-span-7">
            <div className="rounded-2xl border border-[#D4E0F3] bg-white p-6 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-4">
                <div>
                  <h2 className="text-lg font-bold text-[#0B1F44]">
                    Property Hub Offline Storage Inspector
                  </h2>
                  <p className="mt-0.5 text-sm text-slate-600">
                    Simulates the exact Android SAF folder tree, daily TXT records, 1440×1080 4:3
                    Canvas posters, and ZIP exports.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setState(DEFAULT_STATE);
                    setSelectedTxtFile(null);
                  }}
                  className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                  <span>Reset Simulator</span>
                </button>
              </div>

              <div className="mt-4 flex flex-wrap items-center justify-between gap-4 rounded-xl bg-[#F6F9FE] p-4">
                <div className="flex items-center gap-3">
                  <Calendar className="h-5 w-5 text-[#0B41AD]" />
                  <div>
                    <label
                      htmlFor="sim-local-date"
                      className="block text-xs font-semibold text-slate-600"
                    >
                      Simulated Device Local Date
                    </label>
                    <input
                      id="sim-local-date"
                      type="date"
                      value={activeLocalDate}
                      onChange={(e) => setActiveLocalDate(e.target.value)}
                      className="mt-1 h-9 rounded-lg border border-[#C4D3E8] bg-white px-2.5 font-mono text-sm"
                    />
                  </div>
                </div>
                <div className="font-mono text-xs text-slate-700">
                  Root: {state.authorizedFolder ?? 'Documents'}/Property Hub/
                </div>
              </div>

              <div className="mt-5 grid grid-cols-1 gap-5 md:grid-cols-12">
                <div className="space-y-3 md:col-span-5">
                  <div className="rounded-xl border border-slate-200 p-3">
                    <p className="flex items-center gap-1.5 text-xs font-bold text-[#0B41AD]">
                      <FolderOpen className="h-4 w-4" />
                      <span>House for Rent/</span>
                    </p>
                    <div className="mt-2 space-y-1">
                      {Object.keys(state.houseFiles).length === 0 ? (
                        <p className="text-xs text-slate-400">No daily TXT files yet</p>
                      ) : (
                        Object.keys(state.houseFiles)
                          .sort()
                          .reverse()
                          .map((fn) => (
                            <button
                              key={fn}
                              type="button"
                              onClick={() =>
                                setSelectedTxtFile({ category: 'HOUSE_FOR_RENT', fileName: fn })
                              }
                              className="flex w-full items-center gap-1.5 rounded px-2 py-1 text-left font-mono text-xs hover:bg-blue-50"
                            >
                              <FileText className="h-3.5 w-3.5 text-blue-600" />
                              <span>{fn}</span>
                            </button>
                          ))
                      )}
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200 p-3">
                    <p className="flex items-center gap-1.5 text-xs font-bold text-[#0B8A38]">
                      <FolderOpen className="h-4 w-4" />
                      <span>Land for Sale/</span>
                    </p>
                    <div className="mt-2 space-y-1">
                      {Object.keys(state.landFiles).length === 0 ? (
                        <p className="text-xs text-slate-400">No daily TXT files yet</p>
                      ) : (
                        Object.keys(state.landFiles)
                          .sort()
                          .reverse()
                          .map((fn) => (
                            <button
                              key={fn}
                              type="button"
                              onClick={() =>
                                setSelectedTxtFile({ category: 'LAND_FOR_SALE', fileName: fn })
                              }
                              className="flex w-full items-center gap-1.5 rounded px-2 py-1 text-left font-mono text-xs hover:bg-emerald-50"
                            >
                              <FileText className="h-3.5 w-3.5 text-emerald-600" />
                              <span>{fn}</span>
                            </button>
                          ))
                      )}
                    </div>
                  </div>
                </div>

                <div className="rounded-xl bg-[#0F172A] p-4 text-slate-100 md:col-span-7">
                  <p className="border-b border-slate-800 pb-2 font-mono text-xs text-slate-400">
                    {selectedTxtFile
                      ? `Property Hub/${
                          selectedTxtFile.category === 'HOUSE_FOR_RENT'
                            ? 'House for Rent'
                            : 'Land for Sale'
                        }/${selectedTxtFile.fileName}`
                      : 'Select a daily .txt file to view exact formatted contents'}
                  </p>
                  <pre className="mt-3 max-h-[300px] overflow-auto whitespace-pre-wrap font-mono text-xs leading-relaxed text-slate-200">
                    {activeTxtContent ??
                      'Submit a property in the mobile preview to inspect the structured RECORD_ID / DATE / TIME / CATEGORY / MOBILE / DETAILS block.'}
                  </pre>
                </div>
              </div>
            </div>

            <div className="rounded-2xl border border-[#D4E0F3] bg-white p-6 shadow-sm">
              <div className="flex items-start gap-3">
                <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-[#0B41AD]" />
                <div>
                  <h2 className="text-base font-bold text-[#0B1F44]">
                    Native Android APK Ready (.github/workflows/build.yml)
                  </h2>
                  <p className="mt-1 text-sm leading-relaxed text-slate-600">
                    Complete native Java & XML Android source files (`MainActivity`,
                    `CollectDataFragment`, `SettingsFragment`, `GeneratedFilesFragment`,
                    `StorageManager`, `PosterRenderer`, `BulkPosterManager`, and
                    `ZipExportManager`) and unit tests are included in `/app`.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Full-resolution 4:3 Poster Modal */}
      {previewPoster && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-2xl rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-[#0B1F44]">
                  {previewPoster.recordId} • {previewPoster.fileName}
                </h3>
                <p className="text-xs text-slate-500">
                  1440×1080 (4:3 Landscape) • Offline Canvas Render
                </p>
              </div>
              <button
                type="button"
                onClick={() => setPreviewPoster(null)}
                className="rounded-full p-1.5 text-slate-500 hover:bg-slate-100"
                aria-label="Close Preview"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <img
              src={previewPoster.dataUrl}
              alt={previewPoster.fileName}
              className="mt-4 aspect-[4/3] w-full rounded-xl border border-slate-200 bg-slate-950 object-contain"
            />
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setPreviewPoster(null)}
                className="h-9 rounded-lg border border-slate-200 px-4 text-xs font-semibold text-slate-700"
              >
                Close
              </button>
              <a
                href={previewPoster.dataUrl}
                download={previewPoster.fileName}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-[#0B41AD] px-4 text-xs font-semibold text-white"
              >
                <Download className="h-3.5 w-3.5" />
                <span>Download JPG</span>
              </a>
            </div>
          </div>
        </div>
      )}

      {/* Conflict Review Modal */}
      {showConflictModal && importSummary && importSummary.conflicts.length > 0 && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="max-h-[85vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-base font-bold text-[#0B1F44]">
                  Review Conflicting RECORD_IDs ({importSummary.conflicts.length})
                </h3>
                <p className="text-xs text-slate-500">
                  Original TXT files are never modified. Choose which version to use for poster
                  generation or skip.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setShowConflictModal(false)}
                className="rounded-full p-1.5 text-slate-500 hover:bg-slate-100"
                aria-label="Close"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-4 space-y-4">
              {importSummary.conflicts.map((conflict) => {
                const chosen = conflictChoiceMap[conflict.key];
                return (
                  <div key={conflict.key} className="rounded-xl border border-amber-300 bg-amber-50 p-3">
                    <p className="font-mono text-xs font-bold text-amber-900">
                      {conflict.first.recordId}
                    </p>
                    <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2">
                      {[conflict.first, conflict.second].map((ver, idx) => {
                        const isPicked = chosen === ver;
                        return (
                          <div
                            key={`${conflict.key}-${idx}`}
                            className={`rounded-lg border bg-white p-2.5 text-xs ${
                              isPicked ? 'border-blue-600 ring-1 ring-blue-600' : 'border-slate-200'
                            }`}
                          >
                            <p className="font-bold text-slate-700">
                              Version {idx === 0 ? 'A' : 'B'} ({ver.sourceFileName})
                            </p>
                            <p className="mt-1 font-mono text-[11px]">{ver.mobile}</p>
                            <p className="mt-1 whitespace-pre-wrap text-[11px] text-slate-600">
                              {ver.details}
                            </p>
                            <button
                              type="button"
                              onClick={() =>
                                setConflictChoiceMap((prev) => ({ ...prev, [conflict.key]: ver }))
                              }
                              className={`mt-2 h-7 w-full rounded text-[11px] font-bold ${
                                isPicked
                                  ? 'bg-blue-600 text-white'
                                  : 'border border-slate-300 text-slate-700'
                              }`}
                            >
                              {isPicked ? 'Selected' : `Use Version ${idx === 0 ? 'A' : 'B'}`}
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="mt-4 flex justify-end">
              <button
                type="button"
                onClick={() => setShowConflictModal(false)}
                className="h-9 rounded-lg bg-[#0B41AD] px-4 text-xs font-semibold text-white"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
