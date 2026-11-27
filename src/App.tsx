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
  Loader2,
  MapPin,
  Palette,
  RefreshCw,
  Send,
  Settings,
  Share2,
  Trash2,
  X,
} from 'lucide-react';
import { ensurePosterFontsLoaded, renderDynamicPoster } from './posterEngine';
import {
  appendRecord,
  buildDatedRecordId,
  buildHumanReadableSummary,
  buildPosterFileName,
  CategoryCode,
  getDefaultTitle,
  getFieldsForCategory,
  isValidBangladeshiMobile,
  parseTxtContent,
  PosterEntry,
  PropertyFieldValue,
  PropertyRecord,
  removeRecordFromTxt,
  ZipArchiveEntry,
} from './propertyCatalog';
import { RichFieldInput } from './RichFieldInput';

type ActiveScreen = 'collect' | 'settings' | 'files';
type FilesSubTab = 'records' | 'bulk' | 'posters' | 'zip';
type ThemeMode = 'blue' | 'light' | 'dark';

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

const STORAGE_KEY = 'property_hub_offline_v3';

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

function createInitialFormMap(category: CategoryCode): Record<string, PropertyFieldValue> {
  const map: Record<string, PropertyFieldValue> = {};
  for (const cfg of getFieldsForCategory(category)) {
    const def = cfg.defaultValue;
    map[cfg.fieldId] = {
      fieldId: cfg.fieldId,
      label: cfg.label,
      plainText: def,
      segments: def ? [{ text: def, bold: false, italic: false }] : [],
    };
  }
  return map;
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

  const [activeScreen, setActiveScreen] = useState<ActiveScreen>('collect');
  const [activeCategory, setActiveCategory] = useState<CategoryCode>('HOUSE_FOR_RENT');
  const [filesSubTab, setFilesSubTab] = useState<FilesSubTab>('records');
  const [activeLocalDate, setActiveLocalDate] = useState<string>(() => getLocalDateStr());

  const [houseForm, setHouseForm] = useState<Record<string, PropertyFieldValue>>(() =>
    createInitialFormMap('HOUSE_FOR_RENT')
  );
  const [landForm, setLandForm] = useState<Record<string, PropertyFieldValue>>(() =>
    createInitialFormMap('LAND_FOR_SALE')
  );
  const [houseErrors, setHouseErrors] = useState<Record<string, string>>({});
  const [landErrors, setLandErrors] = useState<Record<string, string>>({});
  const [houseResetToken, setHouseResetToken] = useState(0);
  const [landResetToken, setLandResetToken] = useState(0);
  const [submittingCategory, setSubmittingCategory] = useState<CategoryCode | null>(null);

  const swipeStartXRef = useRef<number | null>(null);

  const [selectedTxtFile, setSelectedTxtFile] = useState<{
    category: CategoryCode;
    fileName: string;
  } | null>(null);
  const [previewPoster, setPreviewPoster] = useState<PosterEntry | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{
    category: CategoryCode;
    recordId: string;
    fileName?: string;
    sourceTxtFile: string;
  } | null>(null);

  const [includeTxtInZip, setIncludeTxtInZip] = useState(false);
  const [zipFilterCat, setZipFilterCat] = useState<'ALL' | CategoryCode>('ALL');
  const [zipDateFilter, setZipDateFilter] = useState<string>('ALL');

  const [bulkProgress, setBulkProgress] = useState({
    total: 0,
    skipped: 0,
    succeeded: 0,
    invalid: 0,
    duplicateSkipped: 0,
  });

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

  useEffect(() => {
    void ensurePosterFontsLoaded();
  }, []);

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
    }, 2600);
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

  const availableDates = useMemo(() => {
    const set = new Set<string>();
    if (activeLocalDate) set.add(activeLocalDate);
    for (const p of postersList) {
      if (p.date) set.add(p.date);
    }
    for (const fn of Object.keys(state.houseFiles)) {
      set.add(fn.replace(/\.txt$/i, ''));
    }
    for (const fn of Object.keys(state.landFiles)) {
      set.add(fn.replace(/\.txt$/i, ''));
    }
    return Array.from(set).sort().reverse();
  }, [activeLocalDate, postersList, state.houseFiles, state.landFiles]);

  function handleSubmit(category: CategoryCode, e: React.FormEvent) {
    e.preventDefault();
    if (submittingCategory !== null) return;

    const isHouse = category === 'HOUSE_FOR_RENT';
    const configs = getFieldsForCategory(category);
    const currentForm = isHouse ? houseForm : landForm;
    const errors: Record<string, string> = {};

    for (const cfg of configs) {
      const text = (currentForm[cfg.fieldId]?.plainText ?? '').trim();
      if (cfg.required && !text) {
        errors[cfg.fieldId] = `${cfg.label} অবশ্যই লিখুন`;
      } else if (cfg.fieldId === 'mobile' && !isValidBangladeshiMobile(text)) {
        errors[cfg.fieldId] = 'সঠিক ১১ ডিজিটের মোবাইল নম্বর লিখুন (01XXXXXXXXX)';
      }
    }

    if (Object.keys(errors).length > 0) {
      if (isHouse) setHouseErrors(errors);
      else setLandErrors(errors);
      return;
    }

    if (isHouse) setHouseErrors({});
    else setLandErrors({});
    setSubmittingCategory(category);

    const fieldValues = configs.map((cfg) => currentForm[cfg.fieldId]);
    const titleVal = (currentForm.title?.plainText ?? '').trim() || getDefaultTitle(category);
    const mobileVal = (currentForm.mobile?.plainText ?? '').trim();

    const dateStr = activeLocalDate || getLocalDateStr();
    const timeStr = getLocalTimeStr();
    const nextSeq = (isHouse ? state.houseSeq : state.landSeq) + 1;
    const recordId = buildDatedRecordId(category, dateStr, nextSeq);
    const fileName = `${dateStr}.txt`;

    const newRecord: PropertyRecord = {
      recordId,
      date: dateStr,
      time: timeStr,
      category,
      title: titleVal,
      mobile: mobileVal,
      details: buildHumanReadableSummary(category, fieldValues),
      fieldValues,
      sourceFileName: fileName,
    };

    const existingTxt = isHouse ? state.houseFiles[fileName] ?? '' : state.landFiles[fileName] ?? '';
    const updatedTxt = appendRecord(existingTxt, newRecord);

    // Clear form right after TXT save succeeds (matches Android TXT-first pipeline)
    if (isHouse) {
      setHouseForm(createInitialFormMap('HOUSE_FOR_RENT'));
      setHouseResetToken((n) => n + 1);
    } else {
      setLandForm(createInitialFormMap('LAND_FOR_SALE'));
      setLandResetToken((n) => n + 1);
    }

    window.setTimeout(async () => {
      await ensurePosterFontsLoaded();
      let generatedPoster: PosterEntry | null = null;
      if (state.autoJpgOnSubmit) {
        const posterName = buildPosterFileName(category, recordId);
        const rendered = renderDynamicPoster(newRecord);
        generatedPoster = {
          recordId,
          fileName: posterName,
          category,
          date: dateStr,
          time: timeStr,
          sourceTxtFile: fileName,
          dataUrl: rendered.dataUrl,
          width: rendered.width,
          height: rendered.height,
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
      setSubmittingCategory(null);

      showTopToast(
        generatedPoster
          ? 'Property saved and poster generated successfully!'
          : `${recordId} Saved to TXT`,
        generatedPoster
          ? `${recordId} • ${generatedPoster.fileName}`
          : `Property Hub/${isHouse ? 'House for Rent' : 'Land for Sale'}/${fileName}`,
        isHouse
      );
    }, 180);
  }

  function handleConfirmDelete() {
    if (!deleteTarget) return;
    const { category, recordId } = deleteTarget;
    const isHouse = category === 'HOUSE_FOR_RENT';

    setState((prev) => {
      const sourceFiles = isHouse ? { ...prev.houseFiles } : { ...prev.landFiles };
      for (const [fn, content] of Object.entries(sourceFiles)) {
        const res = removeRecordFromTxt(content, recordId);
        if (res.removed) {
          if (res.updated.trim()) {
            sourceFiles[fn] = res.updated;
          } else {
            delete sourceFiles[fn];
          }
          break;
        }
      }
      const nextPosters = { ...prev.posters };
      delete nextPosters[`${category}|${recordId}`];
      return {
        ...prev,
        houseFiles: isHouse ? sourceFiles : prev.houseFiles,
        landFiles: !isHouse ? sourceFiles : prev.landFiles,
        posters: nextPosters,
      };
    });

    if (previewPoster && previewPoster.recordId === recordId) {
      setPreviewPoster(null);
    }
    setDeleteTarget(null);
    showTopToast(`Deleted ${recordId}`, 'Poster and TXT record removed cleanly', isHouse);
  }

  async function handleRegeneratePoster(poster: PosterEntry) {
    const match = allRecords.find(
      (r) => r.category === poster.category && r.recordId === poster.recordId
    );
    if (!match) {
      showTopToast('Source TXT Not Found', `No TXT record found for ${poster.recordId}`, false);
      return;
    }
    await ensurePosterFontsLoaded();
    const rendered = renderDynamicPoster(match);
    const updated: PosterEntry = {
      ...poster,
      dataUrl: rendered.dataUrl,
      width: rendered.width,
      height: rendered.height,
      time: match.time,
      createdAt: Date.now(),
    };
    setState((prev) => ({
      ...prev,
      posters: { ...prev.posters, [`${poster.category}|${poster.recordId}`]: updated },
    }));
    showTopToast('Poster Regenerated', poster.fileName, poster.category === 'HOUSE_FOR_RENT');
  }

  async function handleSharePoster(poster: PosterEntry) {
    try {
      const res = await fetch(poster.dataUrl);
      const blob = await res.blob();
      const file = new File([blob], poster.fileName, { type: 'image/jpeg' });
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({
          files: [file],
          title: poster.fileName,
          text: `${poster.recordId} Property Poster`,
        });
        return;
      }
    } catch {
      // Fallback to direct download if navigator.share is cancelled or unavailable
    }
    const a = document.createElement('a');
    a.href = poster.dataUrl;
    a.download = poster.fileName;
    a.click();
    showTopToast('Poster Downloaded', `${poster.fileName} (${poster.width ?? 1440}×${poster.height ?? 1000})`, poster.category === 'HOUSE_FOR_RENT');
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

  async function handleGenerateMissingFromImport() {
    if (!importSummary) return;
    await ensurePosterFontsLoaded();
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
      const rendered = renderDynamicPoster(rec);
      newPosters[posterKey] = {
        recordId: rec.recordId,
        fileName: buildPosterFileName(rec.category, rec.recordId),
        category: rec.category,
        date: rec.date,
        time: rec.time,
        sourceTxtFile: rec.sourceFileName,
        dataUrl: rendered.dataUrl,
        width: rendered.width,
        height: rendered.height,
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
    });
    setImportSummary((prev) =>
      prev ? { ...prev, alreadyGenerated: skipped, pendingRecords: [] } : null
    );
    showTopToast('Missing Posters Generated', `${succeeded} created, ${skipped} skipped`, true);
  }

  async function handleBulkGenerate() {
    if (allRecords.length === 0) {
      showTopToast('No Records Found', 'Collect property records first.', true);
      return;
    }

    await ensurePosterFontsLoaded();
    let skipped = 0;
    let succeeded = 0;
    const newPosters = { ...state.posters };

    for (const rec of allRecords) {
      const key = `${rec.category}|${rec.recordId}`;
      if (newPosters[key]) {
        skipped += 1;
        continue;
      }
      const rendered = renderDynamicPoster(rec);
      newPosters[key] = {
        recordId: rec.recordId,
        fileName: buildPosterFileName(rec.category, rec.recordId),
        category: rec.category,
        date: rec.date,
        time: rec.time,
        sourceTxtFile: rec.sourceFileName,
        dataUrl: rendered.dataUrl,
        width: rendered.width,
        height: rendered.height,
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

  const currentConfigs = getFieldsForCategory(activeCategory);
  const currentForm = activeCategory === 'HOUSE_FOR_RENT' ? houseForm : landForm;
  const currentErrors = activeCategory === 'HOUSE_FOR_RENT' ? houseErrors : landErrors;
  const currentResetToken = activeCategory === 'HOUSE_FOR_RENT' ? houseResetToken : landResetToken;

  return (
    <div className="min-h-screen bg-[#EEF3FB] text-[#0B1F44]">
      <div className="mx-auto max-w-7xl px-4 py-6 lg:px-8">
        <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
          {/* LEFT: MOBILE PREVIEW (NO BOTTOM NAV — TOP HEADER FILES & SETTINGS + SWIPEABLE TABS) */}
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

              {/* Top Header with Brand Logo + Generated Files + Settings */}
              <header className="shrink-0 rounded-b-[24px] bg-gradient-to-r from-[#062D80] to-[#0D47B8] px-4 py-3 text-white shadow-sm">
                <div className="flex items-center justify-between gap-2">
                  <button
                    type="button"
                    onClick={() => setActiveScreen('collect')}
                    className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-white/10"
                    aria-label="Property Hub Home"
                  >
                    <Home className="h-7 w-7 text-white" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setActiveScreen('collect')}
                    className="flex-1 text-center"
                  >
                    <h1 className="text-[21px] font-bold leading-tight text-white">
                      Property Hub
                    </h1>
                    <p className="mt-0.5 text-[11px] tracking-wide text-[#D7E4FF]">
                      Collect • Generate • Share
                    </p>
                  </button>
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() =>
                        setActiveScreen(activeScreen === 'files' ? 'collect' : 'files')
                      }
                      aria-label="Generated Files"
                      className={`flex h-9 w-9 items-center justify-center rounded-full text-white transition-colors ${
                        activeScreen === 'files' ? 'bg-white/25' : 'hover:bg-white/15'
                      }`}
                    >
                      <FolderOpen className="h-5 w-5" />
                    </button>
                    <button
                      type="button"
                      onClick={() =>
                        setActiveScreen(activeScreen === 'settings' ? 'collect' : 'settings')
                      }
                      aria-label="Settings"
                      className={`flex h-9 w-9 items-center justify-center rounded-full text-white transition-colors ${
                        activeScreen === 'settings' ? 'bg-white/25' : 'hover:bg-white/15'
                      }`}
                    >
                      <Settings className="h-5 w-5" />
                    </button>
                  </div>
                </div>
              </header>

              {/* Main Screen Container */}
              <div className="min-h-0 flex-1 overflow-hidden">
                {activeScreen === 'collect' && (
                  <div className="flex h-full flex-col">
                    {/* Clickable + Swipeable 2-Category Tab Switcher */}
                    <div className={`grid shrink-0 grid-cols-2 border-b ${cardSurface}`}>
                      <button
                        type="button"
                        onClick={() => setActiveCategory('HOUSE_FOR_RENT')}
                        className={`border-b-[3px] py-2.5 text-[13px] font-bold transition-colors ${
                          activeCategory === 'HOUSE_FOR_RENT'
                            ? 'border-[#0B41AD] text-[#0B41AD]'
                            : 'border-transparent opacity-60'
                        }`}
                      >
                        House for Rent
                      </button>
                      <button
                        type="button"
                        onClick={() => setActiveCategory('LAND_FOR_SALE')}
                        className={`border-b-[3px] py-2.5 text-[13px] font-bold transition-colors ${
                          activeCategory === 'LAND_FOR_SALE'
                            ? 'border-[#0B8A38] text-[#0B8A38]'
                            : 'border-transparent opacity-60'
                        }`}
                      >
                        Land for Sale
                      </button>
                    </div>

                    {/* Swipeable Form Page Area */}
                    <div
                      className="min-h-0 flex-1 overflow-y-auto p-3.5"
                      onTouchStart={(e) => {
                        swipeStartXRef.current = e.touches[0]?.clientX ?? null;
                      }}
                      onTouchEnd={(e) => {
                        if (swipeStartXRef.current === null) return;
                        const endX = e.changedTouches[0]?.clientX ?? swipeStartXRef.current;
                        const dx = endX - swipeStartXRef.current;
                        swipeStartXRef.current = null;
                        if (dx < -60) setActiveCategory('LAND_FOR_SALE');
                        else if (dx > 60) setActiveCategory('HOUSE_FOR_RENT');
                      }}
                    >
                      <section className={`overflow-hidden rounded-[20px] border shadow-sm ${cardSurface}`}>
                        <div
                          className={`flex h-[74px] items-center px-4 text-white ${
                            activeCategory === 'HOUSE_FOR_RENT'
                              ? 'bg-gradient-to-r from-[#06328E] to-[#1565C0]'
                              : 'bg-gradient-to-r from-[#007E2F] to-[#14AE47]'
                          }`}
                        >
                          {activeCategory === 'HOUSE_FOR_RENT' ? (
                            <Home className="h-7 w-7" />
                          ) : (
                            <MapPin className="h-7 w-7" />
                          )}
                          <div className="mx-3 h-9 w-px bg-white/40" />
                          <div>
                            <h2 className="text-[16px] font-bold tracking-wide">
                              {activeCategory === 'HOUSE_FOR_RENT'
                                ? 'HOUSE FOR RENT'
                                : 'LAND FOR SALE'}
                            </h2>
                            <p
                              className={`text-[12px] ${
                                activeCategory === 'HOUSE_FOR_RENT'
                                  ? 'text-[#D7E4FF]'
                                  : 'text-[#E3FFE9]'
                              }`}
                            >
                              {activeCategory === 'HOUSE_FOR_RENT'
                                ? 'Find the right tenant'
                                : 'Sell Your Land Easily'}
                            </p>
                          </div>
                        </div>

                        <form
                          onSubmit={(e) => handleSubmit(activeCategory, e)}
                          className="space-y-3 p-4"
                        >
                          {currentConfigs.map((cfg) => (
                            <RichFieldInput
                              key={`${activeCategory}-${cfg.fieldId}`}
                              fieldId={cfg.fieldId}
                              label={cfg.label}
                              required={cfg.required}
                              multiline={cfg.inputType === 'MULTILINE'}
                              formattable={cfg.formattable}
                              inputType={cfg.inputType}
                              placeholder={cfg.placeholder}
                              accent={activeCategory === 'HOUSE_FOR_RENT' ? 'blue' : 'green'}
                              value={currentForm[cfg.fieldId]}
                              resetToken={currentResetToken}
                              error={currentErrors[cfg.fieldId]}
                              inputSurfaceClass={inputSurface}
                              onChange={(next) => {
                                if (activeCategory === 'HOUSE_FOR_RENT') {
                                  setHouseForm((prev) => ({ ...prev, [cfg.fieldId]: next }));
                                } else {
                                  setLandForm((prev) => ({ ...prev, [cfg.fieldId]: next }));
                                }
                              }}
                            />
                          ))}

                          <button
                            type="submit"
                            disabled={submittingCategory !== null}
                            className={`flex h-11 w-full items-center justify-center gap-2 rounded-xl text-[14px] font-bold text-white transition-opacity disabled:opacity-70 ${
                              activeCategory === 'HOUSE_FOR_RENT'
                                ? 'bg-[#0B41AD] hover:bg-[#093691]'
                                : 'bg-[#0B8A38] hover:bg-[#09732E]'
                            }`}
                          >
                            {submittingCategory === activeCategory ? (
                              <>
                                <Loader2 className="h-4 w-4 animate-spin" />
                                <span>
                                  {state.autoJpgOnSubmit
                                    ? 'Saving & Generating Poster…'
                                    : 'Saving…'}
                                </span>
                              </>
                            ) : (
                              <>
                                <Send className="h-4 w-4" />
                                <span>
                                  {activeCategory === 'HOUSE_FOR_RENT'
                                    ? 'Submit House for Rent'
                                    : 'Submit Land for Sale'}
                                </span>
                              </>
                            )}
                          </button>
                        </form>
                      </section>
                    </div>
                  </div>
                )}

                {activeScreen === 'settings' && (
                  <div className="h-full space-y-3 overflow-y-auto p-4">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => setActiveScreen('collect')}
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
                          Automatically generate a dynamic-height JPG poster after every submission.
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

                    <button
                      type="button"
                      onClick={() => setActiveScreen('collect')}
                      className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#0B41AD] text-sm font-bold text-white"
                    >
                      <span>Back to Home</span>
                    </button>
                  </div>
                )}

                {activeScreen === 'files' && (
                  <div className="flex h-full flex-col">
                    <div className={`flex shrink-0 items-center gap-2 border-b px-3 py-2 ${cardSurface}`}>
                      <button
                        type="button"
                        onClick={() => setActiveScreen('collect')}
                        className="rounded-lg p-1 hover:bg-black/5"
                        aria-label="Back to Home"
                      >
                        <ArrowLeft className="h-4 w-4" />
                      </button>
                      <h2 className="text-[15px] font-bold">Generated Files</h2>
                    </div>

                    <div className={`grid shrink-0 grid-cols-4 border-b text-[11.5px] font-semibold ${cardSurface}`}>
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

                    <div className="min-h-0 flex-1 space-y-3 overflow-y-auto p-3.5">
                      {filesSubTab === 'records' && (
                        <>
                          {allRecords.length === 0 ? (
                            <p className="py-10 text-center text-xs opacity-70">
                              No saved property records yet.
                            </p>
                          ) : (
                            allRecords.map((rec) => {
                              const poster = state.posters[`${rec.category}|${rec.recordId}`];
                              const hasPoster = Boolean(poster);
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
                                    <span className="opacity-70">
                                      {rec.date}
                                      {rec.time ? ` · ${rec.time}` : ''}
                                    </span>
                                  </div>
                                  <p className="mt-1 text-xs font-bold">{rec.title}</p>
                                  <p className="mt-0.5 text-xs font-semibold opacity-85">
                                    {rec.mobile}
                                  </p>
                                  <p className="mt-1 line-clamp-2 whitespace-pre-wrap text-xs opacity-75">
                                    {rec.details}
                                  </p>
                                  <div className="mt-2 flex items-center justify-between text-[11px]">
                                    <span className={hasPoster ? 'text-emerald-600' : 'text-amber-600'}>
                                      {hasPoster ? 'Poster Ready' : 'Poster Missing'}
                                    </span>
                                    <div className="flex items-center gap-3">
                                        {!hasPoster ? (
                                        <button
                                          type="button"
                                          onClick={async () => {
                                            await ensurePosterFontsLoaded();
                                            const rendered = renderDynamicPoster(rec);
                                            const entry: PosterEntry = {
                                              recordId: rec.recordId,
                                              fileName: buildPosterFileName(rec.category, rec.recordId),
                                              category: rec.category,
                                              date: rec.date,
                                              time: rec.time,
                                              sourceTxtFile: rec.sourceFileName,
                                              dataUrl: rendered.dataUrl,
                                              width: rendered.width,
                                              height: rendered.height,
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
                                          onClick={() => setPreviewPoster(poster)}
                                          className="font-semibold text-blue-600 hover:underline"
                                        >
                                          View Poster
                                        </button>
                                      )}
                                      <button
                                        type="button"
                                        onClick={() =>
                                          setDeleteTarget({
                                            category: rec.category,
                                            recordId: rec.recordId,
                                            sourceTxtFile: rec.sourceFileName,
                                          })
                                        }
                                        className="font-semibold text-red-600 hover:underline"
                                      >
                                        Delete
                                      </button>
                                    </div>
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
                              Works independently of Auto JPG. Existing valid posters are skipped
                              by RECORD_ID without modifying source TXT files.
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
                                View Posters ({postersList.length})
                              </button>
                              <button
                                type="button"
                                onClick={() => setFilesSubTab('zip')}
                                className="h-9 rounded-xl border border-blue-600 px-2 text-[11px] font-bold text-blue-600"
                              >
                                Export as ZIP
                              </button>
                            </div>
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
                                <div className="bg-slate-100 p-2">
                                  <img
                                    src={poster.dataUrl}
                                    alt={poster.fileName}
                                    onClick={() => setPreviewPoster(poster)}
                                    className="mx-auto max-h-72 w-full cursor-pointer rounded object-contain"
                                  />
                                </div>
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
                                      {poster.category === 'HOUSE_FOR_RENT'
                                        ? 'House for Rent'
                                        : 'Land for Sale'}
                                    </span>
                                  </div>
                                  <p className="mt-0.5 text-[11px] opacity-70">
                                    Date: {poster.date}
                                    {poster.time ? ` · ${poster.time}` : ''} ·{' '}
                                    {poster.width ?? 1440}×{poster.height ?? 'auto'}px
                                  </p>
                                  <div className="mt-2.5 grid grid-cols-2 gap-2">
                                    <button
                                      type="button"
                                      onClick={() => setPreviewPoster(poster)}
                                      className="flex items-center justify-center gap-1 rounded-lg border border-slate-300 py-1.5 text-xs font-semibold"
                                    >
                                      <Eye className="h-3.5 w-3.5" />
                                      <span>View</span>
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => handleSharePoster(poster)}
                                      className="flex items-center justify-center gap-1 rounded-lg bg-[#0B41AD] py-1.5 text-xs font-semibold text-white"
                                    >
                                      <Share2 className="h-3.5 w-3.5" />
                                      <span>Share</span>
                                    </button>
                                    <a
                                      href={poster.dataUrl}
                                      download={poster.fileName}
                                      className="flex items-center justify-center gap-1 rounded-lg border border-slate-300 py-1.5 text-xs font-semibold"
                                    >
                                      <Download className="h-3.5 w-3.5" />
                                      <span>Save JPG</span>
                                    </a>
                                    <button
                                      type="button"
                                      onClick={() => handleRegeneratePoster(poster)}
                                      className="flex items-center justify-center gap-1 rounded-lg border border-slate-300 py-1.5 text-xs font-semibold"
                                    >
                                      <RefreshCw className="h-3.5 w-3.5" />
                                      <span>Regenerate</span>
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() =>
                                        setDeleteTarget({
                                          category: poster.category,
                                          recordId: poster.recordId,
                                          fileName: poster.fileName,
                                          sourceTxtFile: poster.sourceTxtFile,
                                        })
                                      }
                                      className="col-span-2 flex items-center justify-center gap-1 rounded-lg border border-red-300 py-1.5 text-xs font-semibold text-red-600 hover:bg-red-50"
                                    >
                                      <Trash2 className="h-3.5 w-3.5" />
                                      <span>Delete</span>
                                    </button>
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
                              {availableDates.map((d) => (
                                <option key={d} value={d}>
                                  {d}
                                </option>
                              ))}
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
                    Simulates the exact Android SAF folder tree, daily TXT records with rich field
                    runs, dynamic auto-height Canvas posters (1440px wide), and ZIP exports.
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
                      'Submit a property in the mobile preview to inspect the structured RECORD_ID / TITLE / MOBILE / FIELDS_JSON / DETAILS block.'}
                  </pre>
                </div>
              </div>
            </div>

            <div className="rounded-2xl border border-[#D4E0F3] bg-white p-6 shadow-sm">
              <div className="flex items-start gap-3">
                <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-[#0B41AD]" />
                <div>
                  <h2 className="text-base font-bold text-[#0B1F44]">
                    Native Android App Ready (/app)
                  </h2>
                  <p className="mt-1 text-sm leading-relaxed text-slate-600">
                    Complete native Java & XML Android source files (`MainActivity`,
                    `CollectDataFragment`, `CategoryFormPageFragment`, `RichTextSpannableCodec`,
                    `SettingsFragment`, `GeneratedFilesFragment`, `StorageManager`,
                    `DynamicPosterLayoutEngine`, `HousePosterTemplate`, `LandPosterTemplate`, and
                    unit tests) are updated in `/app`.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Full-resolution Dynamic Poster Modal */}
      {previewPoster && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-[#0B1F44]">
                  {previewPoster.recordId} • {previewPoster.fileName}
                </h3>
                <p className="text-xs text-slate-500">
                  {previewPoster.width ?? 1440}×{previewPoster.height ?? 'auto'} Dynamic Poster •
                  Offline Canvas Render
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
            <div className="mt-4 max-h-[68vh] overflow-y-auto rounded-xl border border-slate-200 bg-slate-100 p-2">
              <img
                src={previewPoster.dataUrl}
                alt={previewPoster.fileName}
                className="h-auto w-full rounded-lg object-contain"
              />
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setPreviewPoster(null)}
                className="h-9 rounded-lg border border-slate-200 px-4 text-xs font-semibold text-slate-700"
              >
                Close
              </button>
              <button
                type="button"
                onClick={() => handleSharePoster(previewPoster)}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 px-4 text-xs font-semibold text-slate-700"
              >
                <Share2 className="h-3.5 w-3.5" />
                <span>Share</span>
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

      {/* Delete Confirmation Modal */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-2xl">
            <h3 className="text-base font-bold text-[#0B1F44]">Delete Poster & Record?</h3>
            <p className="mt-2 text-xs leading-relaxed text-slate-600">
              This will permanently delete{' '}
              {deleteTarget.fileName ? `${deleteTarget.fileName} and ` : ''}the linked record{' '}
              <span className="font-mono font-bold">{deleteTarget.recordId}</span> from{' '}
              <span className="font-mono">{deleteTarget.sourceTxtFile}</span>.
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setDeleteTarget(null)}
                className="h-9 rounded-lg border border-slate-200 px-4 text-xs font-semibold text-slate-700"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmDelete}
                className="h-9 rounded-lg bg-red-600 px-4 text-xs font-semibold text-white hover:bg-red-700"
              >
                Delete
              </button>
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
