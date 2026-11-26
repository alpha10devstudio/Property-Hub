/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Calendar,
  CheckCircle2,
  FileText,
  Folder,
  FolderOpen,
  Home,
  MapPin,
  Phone,
  Send,
  Settings,
  Trash2,
  AlertTriangle,
  X,
} from 'lucide-react';

type CategoryKey = 'house' | 'land';

interface VirtualStorageState {
  authorizedFolder: string | null;
  houseFiles: Record<string, string>;
  landFiles: Record<string, string>;
}

const STORAGE_KEY = 'property_hub_saf_simulator_v1';

function getDeviceLocalDateString(date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function getDeviceLocalTimeString(date = new Date()): string {
  let hours = date.getHours();
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const ampm = hours >= 12 ? 'PM' : 'AM';
  hours = hours % 12;
  if (hours === 0) hours = 12;
  return `${String(hours).padStart(2, '0')}:${minutes} ${ampm}`;
}

function extractHighestRecordNumber(content: string): number {
  if (!content.trim()) return 0;
  const regex = /^PROPERTY RECORD #(\d+)\s*$/gm;
  let max = 0;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(content)) !== null) {
    const num = parseInt(match[1], 10);
    if (!Number.isNaN(num) && num > max) {
      max = num;
    }
  }
  return max;
}

function appendPropertyRecord(
  existingContent: string,
  recordNumber: number,
  dateStr: string,
  timeStr: string,
  categoryLabel: 'House for Rent' | 'Land for Sale',
  mobile: string,
  details: string
): string {
  const numLabel = String(recordNumber).padStart(3, '0');
  const block = [
    `PROPERTY RECORD #${numLabel}`,
    `Date: ${dateStr}`,
    `Time: ${timeStr}`,
    `Category: ${categoryLabel}`,
    `Mobile: ${mobile.trim()}`,
    `Details:`,
    details.replace(/\r\n/g, '\n').trim(),
    '',
  ].join('\n');

  if (!existingContent.trim()) {
    return `====================================\n${block}`;
  }

  let prefix = existingContent;
  if (!prefix.endsWith('\n\n')) {
    prefix += prefix.endsWith('\n') ? '\n' : '\n\n';
  }
  return `${prefix}${block}`;
}

function validateMobile(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return 'Please enter a mobile number';
  let digits = 0;
  for (const ch of trimmed) {
    if (ch >= '0' && ch <= '9') {
      digits += 1;
    } else if (!['+', '-', ' ', '(', ')'].includes(ch)) {
      return 'Enter a valid mobile number';
    }
  }
  if (digits < 6) return 'Enter at least 6 digits';
  return null;
}

const DEFAULT_STATE: VirtualStorageState = {
  authorizedFolder: 'Documents',
  houseFiles: {},
  landFiles: {},
};

export default function App() {
  const [storage, setStorage] = useState<VirtualStorageState>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        return JSON.parse(raw) as VirtualStorageState;
      }
    } catch {
      // Ignore parse errors
    }
    return DEFAULT_STATE;
  });

  // Local date selector for verifying midnight rollover (defaults to real device local date)
  const [activeLocalDate, setActiveLocalDate] = useState<string>(() =>
    getDeviceLocalDateString()
  );

  // House for Rent form state (independent)
  const [houseMobile, setHouseMobile] = useState('');
  const [houseDetails, setHouseDetails] = useState('');
  const [houseErrors, setHouseErrors] = useState<{ mobile?: string; details?: string }>({});

  // Land for Sale form state (independent)
  const [landMobile, setLandMobile] = useState('');
  const [landDetails, setLandDetails] = useState('');
  const [landErrors, setLandErrors] = useState<{ mobile?: string; details?: string }>({});

  // Tap debounce tracking
  const lastTapRef = useRef<{ house: number; land: number }>({ house: 0, land: 0 });

  // Modals & inspector state
  const [showFolderModal, setShowFolderModal] = useState(false);
  const [feedbackModal, setFeedbackModal] = useState<{
    title: string;
    message: string;
    isError?: boolean;
  } | null>(null);
  const [selectedFile, setSelectedFile] = useState<{
    category: CategoryKey;
    fileName: string;
  } | null>(null);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(storage));
    } catch {
      // Ignore quota errors
    }
  }, [storage]);

  const allFiles = useMemo(() => {
    const houseList = Object.keys(storage.houseFiles)
      .sort()
      .reverse()
      .map((fileName) => ({ category: 'house' as const, fileName }));
    const landList = Object.keys(storage.landFiles)
      .sort()
      .reverse()
      .map((fileName) => ({ category: 'land' as const, fileName }));
    return { houseList, landList };
  }, [storage.houseFiles, storage.landFiles]);

  const activeFileContent = useMemo(() => {
    if (!selectedFile) return null;
    if (selectedFile.category === 'house') {
      return storage.houseFiles[selectedFile.fileName] ?? null;
    }
    return storage.landFiles[selectedFile.fileName] ?? null;
  }, [selectedFile, storage.houseFiles, storage.landFiles]);

  function handleHouseSubmit(e: React.FormEvent) {
    e.preventDefault();
    const nowMs = Date.now();
    if (nowMs - lastTapRef.current.house < 650) return;

    const mobileErr = validateMobile(houseMobile);
    const detailsErr = !houseDetails.trim() ? 'Please enter property details' : undefined;
    if (mobileErr || detailsErr) {
      setHouseErrors({ mobile: mobileErr ?? undefined, details: detailsErr });
      return;
    }
    setHouseErrors({});

    if (!storage.authorizedFolder) {
      setFeedbackModal({
        title: 'Folder Access Required',
        message:
          'Storage folder access is missing or revoked. Please select a parent folder first.',
        isError: true,
      });
      return;
    }

    lastTapRef.current.house = nowMs;
    const dateStr = activeLocalDate || getDeviceLocalDateString();
    const timeStr = getDeviceLocalTimeString();
    const fileName = `${dateStr}.txt`;
    const existing = storage.houseFiles[fileName] ?? '';
    const nextNum = extractHighestRecordNumber(existing) + 1;
    const updated = appendPropertyRecord(
      existing,
      nextNum,
      dateStr,
      timeStr,
      'House for Rent',
      houseMobile,
      houseDetails
    );

    setStorage((prev) => ({
      ...prev,
      houseFiles: {
        ...prev.houseFiles,
        [fileName]: updated,
      },
    }));
    setSelectedFile({ category: 'house', fileName });
    setHouseMobile('');
    setHouseDetails('');
    setFeedbackModal({
      title: 'Saved Successfully',
      message: `House for Rent record #${String(nextNum).padStart(
        3,
        '0'
      )} saved to Property Hub/House for Rent/${fileName}`,
    });
  }

  function handleLandSubmit(e: React.FormEvent) {
    e.preventDefault();
    const nowMs = Date.now();
    if (nowMs - lastTapRef.current.land < 650) return;

    const mobileErr = validateMobile(landMobile);
    const detailsErr = !landDetails.trim() ? 'Please enter property details' : undefined;
    if (mobileErr || detailsErr) {
      setLandErrors({ mobile: mobileErr ?? undefined, details: detailsErr });
      return;
    }
    setLandErrors({});

    if (!storage.authorizedFolder) {
      setFeedbackModal({
        title: 'Folder Access Required',
        message:
          'Storage folder access is missing or revoked. Please select a parent folder first.',
        isError: true,
      });
      return;
    }

    lastTapRef.current.land = nowMs;
    const dateStr = activeLocalDate || getDeviceLocalDateString();
    const timeStr = getDeviceLocalTimeString();
    const fileName = `${dateStr}.txt`;
    const existing = storage.landFiles[fileName] ?? '';
    const nextNum = extractHighestRecordNumber(existing) + 1;
    const updated = appendPropertyRecord(
      existing,
      nextNum,
      dateStr,
      timeStr,
      'Land for Sale',
      landMobile,
      landDetails
    );

    setStorage((prev) => ({
      ...prev,
      landFiles: {
        ...prev.landFiles,
        [fileName]: updated,
      },
    }));
    setSelectedFile({ category: 'land', fileName });
    setLandMobile('');
    setLandDetails('');
    setFeedbackModal({
      title: 'Saved Successfully',
      message: `Land for Sale record #${String(nextNum).padStart(
        3,
        '0'
      )} saved to Property Hub/Land for Sale/${fileName}`,
    });
  }

  function handleAdvanceDay() {
    const [y, m, d] = activeLocalDate.split('-').map(Number);
    const next = new Date(y, m - 1, d + 1);
    setActiveLocalDate(getDeviceLocalDateString(next));
  }

  function handleResetFiles() {
    setStorage((prev) => ({
      ...prev,
      houseFiles: {},
      landFiles: {},
    }));
    setSelectedFile(null);
  }

  return (
    <div className="min-h-screen bg-[#EEF3FB] text-[#0B1F44]">
      <div className="mx-auto max-w-6xl px-4 py-6 lg:px-8 lg:py-8">
        <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
          {/* Left Column: Faithful Android Mobile UI Preview */}
          <div className="lg:col-span-5">
            <div className="mx-auto max-w-[420px] overflow-hidden rounded-[28px] bg-[#EDF2FA] shadow-xl ring-1 ring-[#D4E0F3]">
              {/* Top Royal Navy Header */}
              <header className="relative rounded-b-[26px] bg-gradient-to-r from-[#062D80] to-[#0D47B8] px-4 py-4 text-white shadow-sm">
                <div className="flex items-center justify-between gap-2">
                  {/* House Brand Mark */}
                  <div className="flex h-11 w-11 shrink-0 items-center justify-center">
                    <svg
                      viewBox="0 0 48 48"
                      className="h-10 w-10 text-white"
                      fill="currentColor"
                      aria-hidden="true"
                    >
                      <path d="M24 8L5 23l3.5 3.5L24 14.5l15.5 12L43 23z" />
                      <path d="M33 12h4v7.5l-4-3z" />
                      <path d="M18.5 25H23v4.5h-4.5zm6.5 0h4.5v4.5H25zm-6.5 6.5H23V36h-4.5zm6.5 0h4.5V36H25z" />
                    </svg>
                  </div>

                  {/* Title & Subtitle */}
                  <div className="text-center">
                    <h1 className="text-[24px] font-bold leading-tight tracking-tight text-white">
                      Property Hub
                    </h1>
                    <div className="mt-1 flex items-center justify-center gap-2 text-[12px] text-[#D7E4FF]">
                      <span className="h-px w-5 bg-[#D7E4FF]/50" />
                      <span>Post Your Property Easily</span>
                      <span className="h-px w-5 bg-[#D7E4FF]/50" />
                    </div>
                  </div>

                  {/* Settings Trigger */}
                  <button
                    type="button"
                    onClick={() => setShowFolderModal(true)}
                    aria-label="Storage Folder Settings"
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-white transition-colors hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-white"
                  >
                    <Settings className="h-6 w-6" />
                  </button>
                </div>
              </header>

              {/* Form Stack */}
              <div className="space-y-4 p-3.5 pb-5">
                {!storage.authorizedFolder && (
                  <div className="flex items-center justify-between gap-3 rounded-[14px] border border-[#F5C27B] bg-[#FFF4E5] px-3.5 py-2.5 text-[13px] text-[#7A4600]">
                    <div className="flex items-center gap-2">
                      <AlertTriangle className="h-4 w-4 shrink-0" />
                      <span>Folder access is needed to save records offline.</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setShowFolderModal(true)}
                      className="shrink-0 font-semibold text-[#0B41AD] hover:underline"
                    >
                      Select Folder
                    </button>
                  </div>
                )}

                {/* SECTION A: HOUSE FOR RENT */}
                <section className="overflow-hidden rounded-[20px] border border-[#D8E2F2] bg-white shadow-sm">
                  {/* Banner */}
                  <div className="relative flex h-[86px] items-center overflow-hidden bg-gradient-to-r from-[#06328E] to-[#1565C0] px-4">
                    {/* Right architectural house illustration */}
                    <svg
                      viewBox="0 0 180 92"
                      className="pointer-events-none absolute right-0 top-0 h-full w-[175px]"
                      preserveAspectRatio="xMidYMid slice"
                      aria-hidden="true"
                    >
                      <path fill="#CFE7FF" d="M42 0h138v92H0z" />
                      <path fill="#E9F4FF" d="M62 0h118v55H35z" />
                      <path fill="#3A8F3E" d="M14 76h166v16H0z" />
                      <path fill="#F5F7FA" d="M72 22l50-10 34 10v60H72z" />
                      <path fill="#526071" d="M138 24l20 5v53h-20z" />
                      <path fill="#FFF" d="M68 24l54-14 38 11-3 5-35-10-51 13z" />
                      <path fill="#FFB74D" d="M82 29h22v18H82zm28-2h24v20h-24z" />
                      <path fill="#FFA726" d="M82 58h22v22H82zm28 0h24v22h-24z" />
                      <path
                        fill="#1565C0"
                        fillOpacity="0.78"
                        d="M0 0h56L20 92H0z"
                      />
                    </svg>

                    <div className="relative z-10 flex items-center">
                      <Home className="h-8 w-8 text-white" strokeWidth={2} />
                      <div className="mx-3 h-11 w-px bg-white/50" />
                      <div>
                        <h2 className="text-[19px] font-bold leading-tight text-white">
                          House for Rent
                        </h2>
                        <p className="text-[12.5px] text-[#D7E4FF]">
                          Find the right tenant
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Form Fields */}
                  <form onSubmit={handleHouseSubmit} className="space-y-3 p-4">
                    <div className="flex items-start gap-3">
                      <div className="mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#DCEBFF] text-[#0D47A1]">
                        <Phone className="h-[18px] w-[18px]" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <label
                          htmlFor="house-mobile"
                          className="mb-1.5 block text-[14px] font-bold text-[#0B1F44]"
                        >
                          Mobile Number
                        </label>
                        <input
                          id="house-mobile"
                          type="tel"
                          value={houseMobile}
                          onChange={(e) => {
                            setHouseMobile(e.target.value);
                            if (houseErrors.mobile) {
                              setHouseErrors((prev) => ({ ...prev, mobile: undefined }));
                            }
                          }}
                          placeholder="Enter mobile number"
                          className="h-11 w-full rounded-[12px] border border-[#C4D3E8] bg-white px-3.5 text-[14.5px] text-[#0B1F44] placeholder-[#7A889E] focus:border-[#0B41AD] focus:outline-none"
                        />
                        {houseErrors.mobile && (
                          <p className="mt-1 text-[12px] text-red-600">
                            {houseErrors.mobile}
                          </p>
                        )}
                      </div>
                    </div>

                    <div className="flex items-start gap-3">
                      <div className="mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#DCEBFF] text-[#0D47A1]">
                        <FileText className="h-[18px] w-[18px]" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <label
                          htmlFor="house-details"
                          className="mb-1.5 block text-[14px] font-bold text-[#0B1F44]"
                        >
                          Details
                        </label>
                        <textarea
                          id="house-details"
                          rows={3}
                          value={houseDetails}
                          onChange={(e) => {
                            setHouseDetails(e.target.value);
                            if (houseErrors.details) {
                              setHouseErrors((prev) => ({ ...prev, details: undefined }));
                            }
                          }}
                          placeholder="Enter address, rent amount, house type, rooms, etc..."
                          className="w-full resize-none rounded-[12px] border border-[#C4D3E8] bg-white px-3.5 py-2.5 text-[14.5px] text-[#0B1F44] placeholder-[#7A889E] focus:border-[#0B41AD] focus:outline-none"
                        />
                        {houseErrors.details && (
                          <p className="mt-1 text-[12px] text-red-600">
                            {houseErrors.details}
                          </p>
                        )}
                      </div>
                    </div>

                    <button
                      type="submit"
                      className="mt-1 flex h-[50px] w-full items-center justify-center gap-2.5 rounded-[14px] bg-[#0B41AD] px-4 text-[15px] font-bold text-white shadow-sm transition-colors hover:bg-[#093691] active:scale-[0.99]"
                    >
                      <Send className="h-[18px] w-[18px]" />
                      <span>Submit House for Rent</span>
                    </button>
                  </form>
                </section>

                {/* SECTION B: LAND FOR SALE */}
                <section className="overflow-hidden rounded-[20px] border border-[#D3EAD9] bg-white shadow-sm">
                  {/* Banner */}
                  <div className="relative flex h-[86px] items-center overflow-hidden bg-gradient-to-r from-[#007E2F] to-[#14AE47] px-4">
                    {/* Right lush green meadow artwork */}
                    <svg
                      viewBox="0 0 180 92"
                      className="pointer-events-none absolute right-0 top-0 h-full w-[175px]"
                      preserveAspectRatio="xMidYMid slice"
                      aria-hidden="true"
                    >
                      <path fill="#2196F3" d="M34 0h146v54H10z" />
                      <path fill="#64B5F6" d="M24 20h156v34H8z" />
                      <path
                        fill="#1B5E20"
                        d="M24 54c4-16 16-18 22-10 5-12 20-12 25 0 7-10 21-9 26 1 7-12 22-12 27-1 7-10 22-8 27 1 7-11 23-10 29 1v12H20z"
                      />
                      <path fill="#66BB6A" d="M12 54h168v38H0z" />
                      <path
                        fill="#8BC34A"
                        d="M20 62c48-7 106-6 160 0v30H0z"
                      />
                      <path
                        fill="#14AE47"
                        fillOpacity="0.78"
                        d="M0 0h56L20 92H0z"
                      />
                    </svg>

                    <div className="relative z-10 flex items-center">
                      <MapPin className="h-8 w-8 text-white" strokeWidth={2} />
                      <div className="mx-3 h-11 w-px bg-white/50" />
                      <div>
                        <h2 className="text-[19px] font-bold leading-tight text-white">
                          Land for Sale
                        </h2>
                        <p className="text-[12.5px] text-[#E3FFE9]">
                          Sell Your Land Easily
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Form Fields */}
                  <form onSubmit={handleLandSubmit} className="space-y-3 p-4">
                    <div className="flex items-start gap-3">
                      <div className="mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#D6F5E0] text-[#0B8A38]">
                        <Phone className="h-[18px] w-[18px]" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <label
                          htmlFor="land-mobile"
                          className="mb-1.5 block text-[14px] font-bold text-[#0B1F44]"
                        >
                          Mobile Number
                        </label>
                        <input
                          id="land-mobile"
                          type="tel"
                          value={landMobile}
                          onChange={(e) => {
                            setLandMobile(e.target.value);
                            if (landErrors.mobile) {
                              setLandErrors((prev) => ({ ...prev, mobile: undefined }));
                            }
                          }}
                          placeholder="Enter mobile number"
                          className="h-11 w-full rounded-[12px] border border-[#C4D3E8] bg-white px-3.5 text-[14.5px] text-[#0B1F44] placeholder-[#7A889E] focus:border-[#0B8A38] focus:outline-none"
                        />
                        {landErrors.mobile && (
                          <p className="mt-1 text-[12px] text-red-600">
                            {landErrors.mobile}
                          </p>
                        )}
                      </div>
                    </div>

                    <div className="flex items-start gap-3">
                      <div className="mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#D6F5E0] text-[#0B8A38]">
                        <FileText className="h-[18px] w-[18px]" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <label
                          htmlFor="land-details"
                          className="mb-1.5 block text-[14px] font-bold text-[#0B1F44]"
                        >
                          Details
                        </label>
                        <textarea
                          id="land-details"
                          rows={3}
                          value={landDetails}
                          onChange={(e) => {
                            setLandDetails(e.target.value);
                            if (landErrors.details) {
                              setLandErrors((prev) => ({ ...prev, details: undefined }));
                            }
                          }}
                          placeholder="Enter location, land area, price, road access, etc..."
                          className="w-full resize-none rounded-[12px] border border-[#C4D3E8] bg-white px-3.5 py-2.5 text-[14.5px] text-[#0B1F44] placeholder-[#7A889E] focus:border-[#0B8A38] focus:outline-none"
                        />
                        {landErrors.details && (
                          <p className="mt-1 text-[12px] text-red-600">
                            {landErrors.details}
                          </p>
                        )}
                      </div>
                    </div>

                    <button
                      type="submit"
                      className="mt-1 flex h-[50px] w-full items-center justify-center gap-2.5 rounded-[14px] bg-[#0B8A38] px-4 text-[15px] font-bold text-white shadow-sm transition-colors hover:bg-[#09732E] active:scale-[0.99]"
                    >
                      <Send className="h-[18px] w-[18px]" />
                      <span>Submit Land for Sale</span>
                    </button>
                  </form>
                </section>
              </div>
            </div>
          </div>

          {/* Right Column: Live Offline Daily File Manager Inspector & Project Info */}
          <div className="space-y-6 lg:col-span-7">
            <div className="rounded-2xl border border-[#D4E0F3] bg-white p-6 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-4">
                <div>
                  <h2 className="text-lg font-bold text-[#0B1F44]">
                    Offline Daily File Manager Preview
                  </h2>
                  <p className="mt-0.5 text-sm text-slate-600">
                    Test independent submissions, same-day appending, and midnight date
                    rollover matching the Android SAF implementation.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={handleResetFiles}
                    className="inline-flex h-10 items-center gap-1.5 rounded-lg border border-slate-200 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    <span>Clear Preview Files</span>
                  </button>
                </div>
              </div>

              {/* Date & Folder Controls */}
              <div className="mt-4 flex flex-wrap items-center justify-between gap-4 rounded-xl bg-[#F6F9FE] p-4">
                <div className="flex items-center gap-3">
                  <Calendar className="h-5 w-5 text-[#0B41AD]" />
                  <div>
                    <label
                      htmlFor="sim-date"
                      className="block text-xs font-semibold text-slate-600"
                    >
                      Simulated Local Device Date
                    </label>
                    <div className="mt-1 flex items-center gap-2">
                      <input
                        id="sim-date"
                        type="date"
                        value={activeLocalDate}
                        onChange={(e) => setActiveLocalDate(e.target.value)}
                        className="h-9 rounded-lg border border-[#C4D3E8] bg-white px-2.5 text-sm font-mono tabular-nums text-[#0B1F44]"
                      />
                      <button
                        type="button"
                        onClick={handleAdvanceDay}
                        className="h-9 rounded-lg bg-[#0B41AD] px-3 text-xs font-semibold text-white hover:bg-[#093691]"
                      >
                        Next Day (+1)
                      </button>
                    </div>
                  </div>
                </div>

                <div className="text-sm text-slate-700">
                  <span className="font-semibold">SAF Root:</span>{' '}
                  <span className="font-mono text-xs">
                    {storage.authorizedFolder
                      ? `${storage.authorizedFolder}/Property Hub/`
                      : 'Not Authorized'}
                  </span>
                </div>
              </div>

              {/* Folder Tree & File Viewer */}
              <div className="mt-5 grid grid-cols-1 gap-5 md:grid-cols-12">
                <div className="space-y-4 md:col-span-5">
                  {/* House for Rent Folder */}
                  <div className="rounded-xl border border-slate-200 p-3.5">
                    <div className="flex items-center gap-2 text-sm font-bold text-[#0B41AD]">
                      <FolderOpen className="h-4 w-4" />
                      <span>Property Hub / House for Rent</span>
                    </div>
                    {allFiles.houseList.length === 0 ? (
                      <p className="mt-2 text-xs text-slate-500">
                        No daily files yet. Submit a House for Rent record.
                      </p>
                    ) : (
                      <ul className="mt-2 space-y-1">
                        {allFiles.houseList.map(({ fileName }) => {
                          const isSelected =
                            selectedFile?.category === 'house' &&
                            selectedFile.fileName === fileName;
                          return (
                            <li key={fileName}>
                              <button
                                type="button"
                                onClick={() =>
                                  setSelectedFile({ category: 'house', fileName })
                                }
                                className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left font-mono text-xs transition-colors ${
                                  isSelected
                                    ? 'bg-[#DCEBFF] font-semibold text-[#0B41AD]'
                                    : 'text-slate-700 hover:bg-slate-100'
                                }`}
                              >
                                <FileText className="h-3.5 w-3.5 shrink-0" />
                                <span className="truncate">{fileName}</span>
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </div>

                  {/* Land for Sale Folder */}
                  <div className="rounded-xl border border-slate-200 p-3.5">
                    <div className="flex items-center gap-2 text-sm font-bold text-[#0B8A38]">
                      <FolderOpen className="h-4 w-4" />
                      <span>Property Hub / Land for Sale</span>
                    </div>
                    {allFiles.landList.length === 0 ? (
                      <p className="mt-2 text-xs text-slate-500">
                        No daily files yet. Submit a Land for Sale record.
                      </p>
                    ) : (
                      <ul className="mt-2 space-y-1">
                        {allFiles.landList.map(({ fileName }) => {
                          const isSelected =
                            selectedFile?.category === 'land' &&
                            selectedFile.fileName === fileName;
                          return (
                            <li key={fileName}>
                              <button
                                type="button"
                                onClick={() =>
                                  setSelectedFile({ category: 'land', fileName })
                                }
                                className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left font-mono text-xs transition-colors ${
                                  isSelected
                                    ? 'bg-[#D6F5E0] font-semibold text-[#0B8A38]'
                                    : 'text-slate-700 hover:bg-slate-100'
                                }`}
                              >
                                <FileText className="h-3.5 w-3.5 shrink-0" />
                                <span className="truncate">{fileName}</span>
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </div>
                </div>

                {/* Selected Text File Content Viewer */}
                <div className="flex flex-col rounded-xl border border-slate-200 bg-[#0F172A] p-4 text-slate-100 md:col-span-7">
                  <div className="mb-3 flex items-center justify-between border-b border-slate-800 pb-2 text-xs text-slate-400">
                    <span className="font-mono">
                      {selectedFile
                        ? `Property Hub/${
                            selectedFile.category === 'house'
                              ? 'House for Rent'
                              : 'Land for Sale'
                          }/${selectedFile.fileName}`
                        : 'Select a daily .txt file to inspect'}
                    </span>
                  </div>
                  <pre className="min-h-[260px] flex-1 overflow-auto whitespace-pre-wrap font-mono text-xs leading-relaxed text-slate-200">
                    {activeFileContent ??
                      'Submit a House for Rent or Land for Sale property on the left to generate and append daily .txt records here.'}
                  </pre>
                </div>
              </div>
            </div>

            {/* Native Android Project Summary */}
            <div className="rounded-2xl border border-[#D4E0F3] bg-white p-6 shadow-sm">
              <h2 className="text-base font-bold text-[#0B1F44]">
                Native Android Project Ready for GitHub Actions
              </h2>
              <p className="mt-1 text-sm leading-relaxed text-slate-600">
                Complete Java and XML Android source files, SAF daily storage manager,
                unit tests, and Gradle configuration are included in this repository.
                Push to GitHub to automatically compile the debug APK via{' '}
                <code className="rounded bg-slate-100 px-1.5 py-0.5 text-xs">
                  .github/workflows/build.yml
                </code>
                .
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Folder Setup Modal */}
      {showFolderModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Folder className="h-6 w-6 text-[#0B41AD]" />
                <h3 className="text-lg font-bold text-[#0B1F44]">
                  Offline Storage Folder
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setShowFolderModal(false)}
                aria-label="Close"
                className="flex h-9 w-9 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <p className="mt-3 text-sm leading-relaxed text-slate-600">
              {storage.authorizedFolder
                ? `Active parent folder: ${storage.authorizedFolder}/Property Hub. Subfolders "House for Rent" and "Land for Sale" are ready.`
                : 'Folder access is currently revoked. Authorize a folder (such as Documents) to store daily text files.'}
            </p>

            <div className="mt-5 flex flex-wrap justify-end gap-2">
              {storage.authorizedFolder ? (
                <button
                  type="button"
                  onClick={() =>
                    setStorage((prev) => ({ ...prev, authorizedFolder: null }))
                  }
                  className="h-10 rounded-lg border border-red-200 px-3.5 text-sm font-semibold text-red-700 hover:bg-red-50"
                >
                  Simulate Revoke Access
                </button>
              ) : null}
              <button
                type="button"
                onClick={() => {
                  setStorage((prev) => ({ ...prev, authorizedFolder: 'Documents' }));
                  setShowFolderModal(false);
                }}
                className="h-10 rounded-lg bg-[#0B41AD] px-4 text-sm font-semibold text-white hover:bg-[#093691]"
              >
                Select Documents Folder
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Save / Error Confirmation Modal */}
      {feedbackModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
          <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
            <div className="flex items-center gap-2.5">
              {feedbackModal.isError ? (
                <AlertTriangle className="h-6 w-6 text-amber-600" />
              ) : (
                <CheckCircle2 className="h-6 w-6 text-[#0B8A38]" />
              )}
              <h3 className="text-lg font-bold text-[#0B1F44]">
                {feedbackModal.title}
              </h3>
            </div>
            <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-slate-700">
              {feedbackModal.message}
            </p>
            <div className="mt-5 flex justify-end">
              <button
                type="button"
                onClick={() => setFeedbackModal(null)}
                className="h-10 rounded-lg bg-[#0B41AD] px-5 text-sm font-semibold text-white hover:bg-[#093691]"
              >
                OK
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
