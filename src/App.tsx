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

function parseDetailsParagraphs(rawDetails: string): string[] {
  if (!rawDetails.trim()) return ['-'];
  const normalized = rawDetails.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const hasQuote = /["\u201C\u201D]/.test(normalized);
  if (!hasQuote) {
    const lines = normalized
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean);
    return lines.length > 0 ? lines : ['-'];
  }

  const paragraphs: string[] = [];
  let buf = '';
  let inQuotes = false;
  let expectedClose = '"';

  const flushUnquoted = () => {
    const t = buf.trim();
    buf = '';
    if (t) paragraphs.push(t);
  };

  for (let i = 0; i < normalized.length; i++) {
    const ch = normalized[i];
    if (!inQuotes) {
      if (ch === '"' || ch === '\u201C') {
        flushUnquoted();
        inQuotes = true;
        expectedClose = ch === '\u201C' ? '\u201D' : '"';
      } else if (ch === ',' || ch === '\n') {
        flushUnquoted();
      } else if (!/\s/.test(ch) || buf.length > 0) {
        buf += ch;
      }
    } else {
      const next = i + 1 < normalized.length ? normalized[i + 1] : '';
      if (ch === '\\' && (next === '"' || next === '\u201C' || next === '\u201D')) {
        buf += next;
        i++;
      } else if (
        expectedClose === '\u201D'
          ? ch === '\u201D' || ch === '"'
          : ch === '"' || ch === '\u201D'
      ) {
        const t = buf.trim();
        buf = '';
        if (t) paragraphs.push(t);
        inQuotes = false;
      } else {
        buf += ch;
      }
    }
  }
  const rem = buf.trim();
  if (rem) paragraphs.push(rem);
  return paragraphs.length > 0 ? paragraphs : ['-'];
}

interface StyledSegment {
  text: string;
  bold: boolean;
  italic: boolean;
}

interface StyledToken {
  text: string;
  bold: boolean;
  italic: boolean;
}

function parseInlineMarkdown(paragraph: string): StyledSegment[] {
  if (!paragraph) return [{ text: '', bold: false, italic: false }];
  const raw: StyledSegment[] = [];

  const isAlphaNum = (ch: string) => /[\p{L}\p{N}]/u.test(ch);
  const isWs = (ch: string) => /\s/.test(ch);

  const findClosingTriple = (src: string, from: number, end: number, marker: string) => {
    for (let j = from; j <= end - 3; j++) {
      if (src[j] === '\\') {
        j++;
        continue;
      }
      if (src[j] === marker && src[j + 1] === marker && src[j + 2] === marker && !isWs(src[j - 1])) {
        return j;
      }
    }
    return -1;
  };

  const findClosingDouble = (src: string, from: number, end: number, marker: string) => {
    for (let j = from; j <= end - 2; j++) {
      if (src[j] === '\\') {
        j++;
        continue;
      }
      if (src[j] === marker && src[j + 1] === marker && !isWs(src[j - 1])) {
        return j;
      }
    }
    return -1;
  };

  const findClosingSingle = (src: string, from: number, end: number, marker: string) => {
    for (let j = from; j < end; j++) {
      if (src[j] === '\\') {
        j++;
        continue;
      }
      if (j + 1 < end && src[j] === marker && src[j + 1] === marker) {
        j++;
        continue;
      }
      if (src[j] === marker && !isWs(src[j - 1])) {
        if (marker === '*' || j + 1 >= end || !isAlphaNum(src[j + 1])) {
          return j;
        }
      }
    }
    return -1;
  };

  const walk = (src: string, start: number, end: number, bold: boolean, italic: boolean) => {
    let plain = '';
    const flush = () => {
      if (plain) {
        raw.push({ text: plain, bold, italic });
        plain = '';
      }
    };

    let i = start;
    while (i < end) {
      const ch = src[i];
      if (ch === '\\' && i + 1 < end && (src[i + 1] === '*' || src[i + 1] === '_' || src[i + 1] === '\\')) {
        plain += src[i + 1];
        i += 2;
        continue;
      }
      if (ch === '*' || ch === '_') {
        if (i + 2 < end && src[i + 1] === ch && src[i + 2] === ch && i + 3 < end && !isWs(src[i + 3])) {
          const closeIdx = findClosingTriple(src, i + 3, end, ch);
          if (closeIdx > i + 3) {
            flush();
            walk(src, i + 3, closeIdx, true, true);
            i = closeIdx + 3;
            continue;
          }
        }
        if (i + 1 < end && src[i + 1] === ch && i + 2 < end && !isWs(src[i + 2])) {
          const closeIdx = findClosingDouble(src, i + 2, end, ch);
          if (closeIdx > i + 2) {
            flush();
            walk(src, i + 2, closeIdx, true, italic);
            i = closeIdx + 2;
            continue;
          }
        }
        if (i + 1 < end && !isWs(src[i + 1])) {
          const leftOk = ch === '*' || i <= start || !isAlphaNum(src[i - 1]);
          if (leftOk) {
            const closeIdx = findClosingSingle(src, i + 1, end, ch);
            if (closeIdx > i + 1) {
              flush();
              walk(src, i + 1, closeIdx, bold, true);
              i = closeIdx + 1;
              continue;
            }
          }
        }
      }
      plain += ch;
      i++;
    }
    flush();
  };

  walk(paragraph, 0, paragraph.length, false, false);
  const merged: StyledSegment[] = [];
  for (const seg of raw) {
    if (!seg.text) continue;
    const last = merged[merged.length - 1];
    if (last && last.bold === seg.bold && last.italic === seg.italic) {
      last.text += seg.text;
    } else {
      merged.push({ ...seg });
    }
  }
  return merged.length > 0 ? merged : [{ text: paragraph, bold: false, italic: false }];
}

function buildCanvasFont(fontSize: number, bold: boolean, italic: boolean): string {
  const stylePart = `${italic ? 'italic ' : ''}${bold ? 'bold ' : '500 '}`;
  return `${stylePart}${fontSize}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
}

function wrapStyledParagraphs(
  ctx: CanvasRenderingContext2D,
  styledParagraphs: StyledSegment[][],
  maxWidth: number,
  fontSize: number
): StyledToken[][][] {
  return styledParagraphs.map((segments) => {
    // Split into explicit sub-lines by '\n'
    const subLines: StyledSegment[][] = [[]];
    for (const seg of segments) {
      const parts = seg.text.split('\n');
      parts.forEach((part, idx) => {
        if (part) {
          subLines[subLines.length - 1].push({
            text: part,
            bold: seg.bold,
            italic: seg.italic,
          });
        }
        if (idx < parts.length - 1) {
          subLines.push([]);
        }
      });
    }

    const wrappedLines: StyledToken[][] = [];
    for (const subSegs of subLines) {
      // Tokenize keeping trailing/leading spaces attached cleanly for word-level wrapping
      const tokens: StyledToken[] = [];
      for (const seg of subSegs) {
        const chunks = seg.text.split(/(\s+)/);
        for (const chunk of chunks) {
          if (!chunk) continue;
          tokens.push({ text: chunk, bold: seg.bold, italic: seg.italic });
        }
      }

      let currentLine: StyledToken[] = [];
      let currentWidth = 0;

      for (const tok of tokens) {
        const isSpaceOnly = /^\s+$/.test(tok.text);
        if (currentLine.length === 0 && isSpaceOnly) continue;

        ctx.font = buildCanvasFont(fontSize, tok.bold, tok.italic);
        const tokW = ctx.measureText(tok.text).width;

        if (currentWidth + tokW <= maxWidth || currentLine.length === 0) {
          currentLine.push(tok);
          currentWidth += tokW;
        } else {
          // Trim trailing whitespace on wrapped line
          while (currentLine.length > 0 && /^\s+$/.test(currentLine[currentLine.length - 1].text)) {
            currentLine.pop();
          }
          if (currentLine.length > 0) wrappedLines.push(currentLine);
          if (isSpaceOnly) {
            currentLine = [];
            currentWidth = 0;
          } else {
            currentLine = [tok];
            currentWidth = tokW;
          }
        }
      }
      while (currentLine.length > 0 && /^\s+$/.test(currentLine[currentLine.length - 1].text)) {
        currentLine.pop();
      }
      if (currentLine.length > 0) wrappedLines.push(currentLine);
    }

    return wrappedLines.length > 0 ? wrappedLines : [[{ text: '-', bold: false, italic: false }]];
  });
}

function computeDynamicGeometry(width: number, height: number, desiredDetailsH: number) {
  const OUTER_MARGIN = 16;
  const CARD_SIDE = 36;
  const CARD_GAP = 26;
  const HEADER_H = 118;
  const STACK_TOP = 158;
  const STACK_BOTTOM = height - 34;
  const STACK_BUDGET = STACK_BOTTOM - STACK_TOP;
  const CARDS_SUM_BUDGET = STACK_BUDGET - 2 * CARD_GAP;

  const MIN_TITLE_H = 156;
  const MAX_TITLE_H = 224;
  const MIN_CONTACT_H = 148;
  const MAX_CONTACT_H = 204;
  const MIN_DETAILS_H = 256;
  const MAX_DETAILS_H = CARDS_SUM_BUDGET - MIN_TITLE_H - MIN_CONTACT_H;

  const detailsH = Math.max(MIN_DETAILS_H, Math.min(MAX_DETAILS_H, desiredDetailsH));
  const remaining = CARDS_SUM_BUDGET - detailsH;
  const extra = Math.max(0, remaining - MIN_TITLE_H - MIN_CONTACT_H);

  const titleH = Math.min(MAX_TITLE_H, MIN_TITLE_H + extra * 0.52);
  const contactH = Math.min(MAX_CONTACT_H, MIN_CONTACT_H + (extra - (titleH - MIN_TITLE_H)));

  const totalStackH = titleH + CARD_GAP + detailsH + CARD_GAP + contactH;
  const slack = Math.max(0, STACK_BUDGET - totalStackH);

  const titleTop = STACK_TOP + slack * 0.42;
  const titleBottom = titleTop + titleH;
  const detailsTop = titleBottom + CARD_GAP;
  const detailsBottom = detailsTop + detailsH;
  const contactTop = detailsBottom + CARD_GAP;
  const contactBottom = contactTop + contactH;

  const headerBottom = Math.max(26 + HEADER_H, titleTop - 16);
  const headerTop = headerBottom - HEADER_H;

  const cardLeft = CARD_SIDE;
  const cardRight = width - CARD_SIDE;
  const dividerX = cardLeft + 336;
  const textLeft = dividerX + 34;
  const textRight = cardRight - 34;

  return {
    outerRect: { left: OUTER_MARGIN, top: OUTER_MARGIN, right: width - OUTER_MARGIN, bottom: height - OUTER_MARGIN },
    headerRect: { left: cardLeft, top: headerTop, right: cardRight, bottom: headerBottom, height: HEADER_H },
    titleRect: { left: cardLeft, top: titleTop, right: cardRight, bottom: titleBottom, width: cardRight - cardLeft, height: titleH },
    detailsCard: { left: cardLeft, top: detailsTop, right: cardRight, bottom: detailsBottom, width: cardRight - cardLeft, height: detailsH },
    contactRect: { left: cardLeft, top: contactTop, right: cardRight, bottom: contactBottom, width: cardRight - cardLeft, height: contactH },
    dividerX,
    textBox: { left: textLeft, top: detailsTop + 32, width: textRight - textLeft, height: detailsH - 64 },
    maxDetailsH: MAX_DETAILS_H,
  };
}

function drawDiamondMarker(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  r: number,
  outerColor: string,
  innerColor: string
) {
  ctx.fillStyle = outerColor;
  ctx.beginPath();
  ctx.moveTo(cx, cy - r);
  ctx.lineTo(cx + r, cy);
  ctx.lineTo(cx, cy + r);
  ctx.lineTo(cx - r, cy);
  ctx.closePath();
  ctx.fill();

  const ir = r * 0.45;
  ctx.fillStyle = innerColor;
  ctx.beginPath();
  ctx.moveTo(cx, cy - ir);
  ctx.lineTo(cx + ir, cy);
  ctx.lineTo(cx, cy + ir);
  ctx.lineTo(cx - ir, cy);
  ctx.closePath();
  ctx.fill();
}

function drawHandsetPath(ctx: CanvasRenderingContext2D) {
  ctx.beginPath();
  ctx.moveTo(-22, -34);
  ctx.quadraticCurveTo(-34, -32, -34, -18);
  ctx.bezierCurveTo(-34, 6, -6, 34, 18, 34);
  ctx.quadraticCurveTo(32, 34, 34, 22);
  ctx.lineTo(34, 12);
  ctx.quadraticCurveTo(34, 6, 26, 4);
  ctx.lineTo(14, 2);
  ctx.quadraticCurveTo(9, 1, 6, 5);
  ctx.lineTo(1, 11);
  ctx.quadraticCurveTo(-8, 6, -11, -1);
  ctx.lineTo(-5, -6);
  ctx.quadraticCurveTo(-1, -9, -2, -14);
  ctx.lineTo(-4, -26);
  ctx.quadraticCurveTo(-6, -34, -12, -34);
  ctx.closePath();
  ctx.fill();
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
  const FIXED_TITLE_SIZE = 94;
  const FIXED_CONTACT_SIZE = 82;
  const COMPACT_CARD_MAX_H = 348;
  const VERT_PAD = 32;

  const styledParagraphs = parseDetailsParagraphs(record.details).map(parseInlineMarkdown);
  const baseGeom = computeDynamicGeometry(width, height, COMPACT_CARD_MAX_H);
  const textWidth = baseGeom.textBox.width;
  const compactMaxTextH = COMPACT_CARD_MAX_H - 2 * VERT_PAD;
  const expandedMaxTextH = baseGeom.maxDetailsH - 2 * VERT_PAD;

  const measureFit = (startSize: number, minSize: number, maxH: number) => {
    let size = startSize;
    while (size >= minSize) {
      const wrapped = wrapStyledParagraphs(ctx, styledParagraphs, textWidth, size);
      const lineH = size * 1.18;
      const paraGap = Math.max(10, size * 0.42);
      const totalH =
        wrapped.reduce((acc, g) => acc + g.length * lineH, 0) +
        Math.max(0, wrapped.length - 1) * paraGap;
      if (totalH <= maxH) {
        return { wrapped, fontSize: size, lineH, paraGap, totalH, overflowed: false };
      }
      size -= 2;
    }
    const minWrapped = wrapStyledParagraphs(ctx, styledParagraphs, textWidth, minSize);
    const minLineH = minSize * 1.12;
    const minGap = Math.max(8, minSize * 0.30);
    const minTotalH =
      minWrapped.reduce((acc, g) => acc + g.length * minLineH, 0) +
      Math.max(0, minWrapped.length - 1) * minGap;
    return {
      wrapped: minWrapped,
      fontSize: minSize,
      lineH: minLineH,
      paraGap: minGap,
      totalH: minTotalH,
      overflowed: minTotalH > maxH,
    };
  };

  let chosenFit = measureFit(66, 50, compactMaxTextH);
  let desiredCardH = Math.max(256, chosenFit.totalH + 2 * VERT_PAD);
  if (chosenFit.overflowed || chosenFit.totalH > compactMaxTextH) {
    chosenFit = measureFit(50, 18, expandedMaxTextH);
    desiredCardH = chosenFit.overflowed
      ? baseGeom.maxDetailsH
      : Math.max(COMPACT_CARD_MAX_H, Math.min(baseGeom.maxDetailsH, chosenFit.totalH + 2 * VERT_PAD));
  }

  const layout = computeDynamicGeometry(width, height, desiredCardH);

  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, width, height);

  if (isHouse) {
    const NAVY = '#032668';

    // Outer border
    ctx.strokeStyle = NAVY;
    ctx.lineWidth = 7;
    ctx.beginPath();
    ctx.roundRect(16, 16, width - 32, height - 32, 48);
    ctx.stroke();

    // Dynamic top roof silhouette + wings
    ctx.save();
    ctx.translate(0, layout.headerRect.top - 32);
    ctx.fillStyle = NAVY;
    ctx.beginPath();
    ctx.moveTo(96, 114);
    ctx.lineTo(430, 106);
    ctx.arc(430, 114, 8, -Math.PI / 2, Math.PI / 2);
    ctx.closePath();
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(width - 96, 114);
    ctx.lineTo(width - 430, 106);
    ctx.arc(width - 430, 114, 8, -Math.PI / 2, Math.PI / 2, true);
    ctx.closePath();
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(438, 150);
    ctx.lineTo(618, 48);
    ctx.lineTo(632, 56);
    ctx.lineTo(466, 150);
    ctx.closePath();
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(482, 150);
    ctx.lineTo(720, 32);
    ctx.lineTo(824, 84);
    ctx.lineTo(824, 54);
    ctx.lineTo(858, 54);
    ctx.lineTo(858, 101);
    ctx.lineTo(960, 150);
    ctx.lineTo(890, 150);
    ctx.lineTo(720, 64);
    ctx.lineTo(548, 150);
    ctx.closePath();
    ctx.fill();

    const cx = width / 2;
    ctx.fillRect(cx - 22, 98, 18, 18);
    ctx.fillRect(cx + 4, 98, 18, 18);
    ctx.fillRect(cx - 22, 124, 18, 18);
    ctx.fillRect(cx + 4, 124, 18, 18);
    ctx.restore();

    // Fixed Title Card
    ctx.fillStyle = NAVY;
    ctx.beginPath();
    ctx.roundRect(layout.titleRect.left, layout.titleRect.top, layout.titleRect.width, layout.titleRect.height, 50);
    ctx.fill();

    ctx.font = `bold ${FIXED_TITLE_SIZE}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('ফ্ল্যাট / বাসা ভাড়া দেওয়া হবে', width / 2, (layout.titleRect.top + layout.titleRect.bottom) / 2);

    // Dynamic Details Card
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(layout.detailsCard.left, layout.detailsCard.top, layout.detailsCard.width, layout.detailsCard.height, 44);
    ctx.fill();
    ctx.strokeStyle = NAVY;
    ctx.lineWidth = 6;
    ctx.stroke();

    // Proportionally scaled 2-story house + tree illustration
    const paneW = layout.dividerX - layout.detailsCard.left;
    const hScale = Math.min(1.08, Math.min((paneW - 36) / 294, (layout.detailsCard.height - 44) / 322));
    ctx.save();
    ctx.translate(layout.detailsCard.left + paneW / 2, (layout.detailsCard.top + layout.detailsCard.bottom) / 2);
    ctx.scale(hScale, hScale);
    ctx.translate(-210, -613);

    ctx.fillStyle = NAVY;
    ctx.beginPath();
    ctx.moveTo(64, 772);
    ctx.quadraticCurveTo(208, 746, 354, 772);
    ctx.quadraticCurveTo(208, 760, 64, 772);
    ctx.closePath();
    ctx.fill();

    ctx.fillRect(94, 710, 12, 54);
    ctx.beginPath();
    ctx.arc(100, 678, 30, 0, Math.PI * 2);
    ctx.arc(84, 696, 20, 0, Math.PI * 2);
    ctx.arc(116, 696, 20, 0, Math.PI * 2);
    ctx.fill();

    ctx.strokeStyle = NAVY;
    ctx.lineWidth = 6;
    ctx.strokeRect(140, 522, 72, 236);
    ctx.beginPath();
    ctx.moveTo(212, 506);
    ctx.lineTo(212, 758);
    ctx.moveTo(334, 506);
    ctx.lineTo(334, 758);
    ctx.moveTo(130, 610);
    ctx.lineTo(344, 610);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(192, 514);
    ctx.lineTo(274, 454);
    ctx.lineTo(312, 478);
    ctx.lineTo(312, 458);
    ctx.lineTo(330, 458);
    ctx.lineTo(330, 488);
    ctx.lineTo(356, 514);
    ctx.lineTo(340, 526);
    ctx.lineTo(274, 472);
    ctx.lineTo(206, 526);
    ctx.closePath();
    ctx.fill();

    ctx.fillRect(251, 510, 20, 20);
    ctx.fillRect(277, 510, 20, 20);
    ctx.fillRect(251, 536, 20, 20);
    ctx.fillRect(277, 536, 20, 20);
    ctx.lineWidth = 4;
    ctx.strokeRect(154, 540, 48, 54);
    ctx.strokeRect(154, 630, 44, 48);
    ctx.fillRect(254, 630, 62, 128);
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(276, 696, 3.2, 0, Math.PI * 2);
    ctx.arc(294, 696, 3.2, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    // Vertical divider
    const divInset = Math.min(38, layout.detailsCard.height * 0.12);
    ctx.strokeStyle = NAVY;
    ctx.lineWidth = 5;
    ctx.beginPath();
    ctx.moveTo(layout.dividerX, layout.detailsCard.top + divInset);
    ctx.lineTo(layout.dividerX, layout.detailsCard.bottom - divInset);
    ctx.stroke();

    // Dynamic Details Styled Text
    ctx.fillStyle = NAVY;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    let drawY = layout.textBox.top + (!chosenFit.overflowed ? Math.max(0, (layout.textBox.height - chosenFit.totalH) / 2) : 0);
    for (let gi = 0; gi < chosenFit.wrapped.length; gi++) {
      for (const lineTokens of chosenFit.wrapped[gi]) {
        if (drawY + chosenFit.lineH > layout.textBox.top + layout.textBox.height + 10) break;
        let runX = layout.textBox.left;
        for (const tok of lineTokens) {
          ctx.font = buildCanvasFont(chosenFit.fontSize, tok.bold, tok.italic);
          ctx.fillText(tok.text, runX, drawY);
          runX += ctx.measureText(tok.text).width;
        }
        drawY += chosenFit.lineH;
      }
      if (gi < chosenFit.wrapped.length - 1) drawY += chosenFit.paraGap;
    }

    // Bottom Navy Contact Banner
    ctx.fillStyle = NAVY;
    ctx.beginPath();
    ctx.roundRect(layout.contactRect.left, layout.contactRect.top, layout.contactRect.width, layout.contactRect.height, 50);
    ctx.fill();

    const contactMidY = (layout.contactRect.top + layout.contactRect.bottom) / 2;
    const phoneR = Math.min(62, layout.contactRect.height * 0.32);
    const phoneCx = layout.contactRect.left + 108;
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(phoneCx, contactMidY, phoneR, 0, Math.PI * 2);
    ctx.fill();

    ctx.save();
    ctx.translate(phoneCx, contactMidY);
    ctx.scale(phoneR / 64, phoneR / 64);
    ctx.fillStyle = NAVY;
    drawHandsetPath(ctx);
    ctx.restore();

    const cDivX = layout.contactRect.left + 206;
    const cDivInset = Math.min(30, layout.contactRect.height * 0.16);
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 5;
    ctx.beginPath();
    ctx.moveTo(cDivX, layout.contactRect.top + cDivInset);
    ctx.lineTo(cDivX, layout.contactRect.bottom - cDivInset);
    ctx.stroke();

    const mobAreaLeft = cDivX + 30;
    const mobAreaW = layout.contactRect.right - 36 - mobAreaLeft;
    ctx.font = `bold ${FIXED_CONTACT_SIZE}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(`যোগাযোগ: ${record.mobile}`, mobAreaLeft + mobAreaW / 2, contactMidY);
    ctx.textAlign = 'left';
    ctx.textBaseline = 'alphabetic';
  } else {
    // LAND FOR SALE: Distinct Green + White Surveyor & Plot Design using shared Dynamic Layout
    const FOREST = '#0B5D2E';
    const EMERALD = '#16A34A';
    const SOFT_MINT = '#EEFBF3';
    const PINE_TEXT = '#063B1D';

    // Double surveyor outer frame
    ctx.strokeStyle = FOREST;
    ctx.lineWidth = 6.5;
    ctx.beginPath();
    ctx.roundRect(16, 16, width - 32, height - 32, 30);
    ctx.stroke();

    ctx.strokeStyle = EMERALD;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.roundRect(26, 26, width - 52, height - 52, 24);
    ctx.stroke();

    // Top Location Pin + Horizon Boundary Stakes Emblem
    const hCx = width / 2;
    const hBottom = layout.headerRect.bottom - 6;
    const barY = hBottom - 28;
    ctx.strokeStyle = FOREST;
    ctx.lineWidth = 6;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(hCx - 540, barY);
    ctx.lineTo(hCx - 158, barY);
    ctx.moveTo(hCx + 158, barY);
    ctx.lineTo(hCx + 540, barY);
    ctx.stroke();

    ctx.strokeStyle = EMERALD;
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(hCx - 496, barY + 12);
    ctx.lineTo(hCx - 176, barY + 12);
    ctx.moveTo(hCx + 176, barY + 12);
    ctx.lineTo(hCx + 496, barY + 12);
    ctx.stroke();

    drawDiamondMarker(ctx, hCx - 158, barY, 10, FOREST, '#FFFFFF');
    drawDiamondMarker(ctx, hCx + 158, barY, 10, FOREST, '#FFFFFF');
    drawDiamondMarker(ctx, hCx - 540, barY, 7, EMERALD, '#FFFFFF');
    drawDiamondMarker(ctx, hCx + 540, barY, 7, EMERALD, '#FFFFFF');

    ctx.strokeStyle = FOREST;
    ctx.lineWidth = 5;
    ctx.beginPath();
    ctx.moveTo(hCx - 122, hBottom);
    ctx.quadraticCurveTo(hCx, hBottom - 34, hCx + 122, hBottom);
    ctx.stroke();

    ctx.strokeStyle = EMERALD;
    ctx.lineWidth = 3.5;
    ctx.beginPath();
    ctx.moveTo(hCx - 82, hBottom + 4);
    ctx.quadraticCurveTo(hCx, hBottom - 16, hCx + 82, hBottom + 4);
    ctx.stroke();

    const pinCy = layout.headerRect.top + 38;
    const pinR = 29;
    ctx.fillStyle = FOREST;
    ctx.beginPath();
    ctx.arc(hCx, pinCy, pinR, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.moveTo(hCx - pinR * 0.86, pinCy + pinR * 0.45);
    ctx.lineTo(hCx, hBottom - 14);
    ctx.lineTo(hCx + pinR * 0.86, pinCy + pinR * 0.45);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(hCx, pinCy, 14, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = EMERALD;
    ctx.beginPath();
    ctx.arc(hCx, pinCy, 7.5, 0, Math.PI * 2);
    ctx.fill();

    // Architectural Forest Green Title Card ("জমি বিক্রি করা হবে") — FIXED Font Size
    ctx.fillStyle = FOREST;
    ctx.beginPath();
    ctx.roundRect(layout.titleRect.left, layout.titleRect.top, layout.titleRect.width, layout.titleRect.height, 22);
    ctx.fill();

    const stripW = Math.min(460, layout.titleRect.width * 0.36);
    const stripL = width / 2 - stripW / 2;
    ctx.fillStyle = EMERALD;
    ctx.beginPath();
    ctx.roundRect(stripL, layout.titleRect.top + 8, stripW, 6, 3);
    ctx.roundRect(stripL, layout.titleRect.bottom - 14, stripW, 6, 3);
    ctx.fill();

    const titleMidY = (layout.titleRect.top + layout.titleRect.bottom) / 2;
    drawDiamondMarker(ctx, layout.titleRect.left + 72, titleMidY, 15, EMERALD, '#FFFFFF');
    drawDiamondMarker(ctx, layout.titleRect.right - 72, titleMidY, 15, EMERALD, '#FFFFFF');

    ctx.font = `bold ${FIXED_TITLE_SIZE}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('জমি বিক্রি করা হবে', width / 2, titleMidY);

    // Dynamic Details Card with Soft Mint Left Zone & Survey Plot Graphics
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(layout.detailsCard.left, layout.detailsCard.top, layout.detailsCard.width, layout.detailsCard.height, 24);
    ctx.fill();

    ctx.save();
    ctx.beginPath();
    ctx.roundRect(layout.detailsCard.left, layout.detailsCard.top, layout.detailsCard.width, layout.detailsCard.height, 24);
    ctx.clip();
    ctx.fillStyle = SOFT_MINT;
    ctx.fillRect(layout.detailsCard.left, layout.detailsCard.top, layout.dividerX - layout.detailsCard.left, layout.detailsCard.height);
    ctx.restore();

    ctx.strokeStyle = FOREST;
    ctx.lineWidth = 5.5;
    ctx.beginPath();
    ctx.roundRect(layout.detailsCard.left, layout.detailsCard.top, layout.detailsCard.width, layout.detailsCard.height, 24);
    ctx.stroke();

    // Right L-corner surveyor ticks
    ctx.strokeStyle = EMERALD;
    ctx.lineWidth = 4;
    ctx.beginPath();
    ctx.moveTo(layout.detailsCard.right - 40, layout.detailsCard.top + 18);
    ctx.lineTo(layout.detailsCard.right - 18, layout.detailsCard.top + 18);
    ctx.lineTo(layout.detailsCard.right - 18, layout.detailsCard.top + 40);
    ctx.moveTo(layout.detailsCard.right - 40, layout.detailsCard.bottom - 18);
    ctx.lineTo(layout.detailsCard.right - 18, layout.detailsCard.bottom - 18);
    ctx.lineTo(layout.detailsCard.right - 18, layout.detailsCard.bottom - 40);
    ctx.stroke();

    // Proportionally scaled Surveyed Land Plot + Trees + Boundary Stakes + Pin Illustration
    const paneW = layout.dividerX - layout.detailsCard.left;
    const lScale = Math.min(1.08, Math.min((paneW - 34) / 292, (layout.detailsCard.height - 42) / 302));
    ctx.save();
    ctx.translate(layout.detailsCard.left + paneW / 2, (layout.detailsCard.top + layout.detailsCard.bottom) / 2);
    ctx.scale(lScale, lScale);

    ctx.fillStyle = FOREST;
    ctx.beginPath();
    ctx.moveTo(-136, 122);
    ctx.quadraticCurveTo(0, 96, 136, 122);
    ctx.quadraticCurveTo(0, 110, -136, 122);
    ctx.closePath();
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(-84, 18);
    ctx.lineTo(84, 18);
    ctx.lineTo(126, 100);
    ctx.lineTo(-126, 100);
    ctx.closePath();
    ctx.fillStyle = '#FFFFFF';
    ctx.fill();
    ctx.fillStyle = 'rgba(22, 163, 74, 0.15)';
    ctx.fill();

    ctx.save();
    ctx.setLineDash([9, 7]);
    ctx.strokeStyle = EMERALD;
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(0, 18);
    ctx.lineTo(0, 100);
    ctx.moveTo(-106, 58);
    ctx.lineTo(106, 58);
    ctx.stroke();
    ctx.restore();

    ctx.strokeStyle = FOREST;
    ctx.lineWidth = 5.5;
    ctx.beginPath();
    ctx.moveTo(-84, 18);
    ctx.lineTo(84, 18);
    ctx.lineTo(126, 100);
    ctx.lineTo(-126, 100);
    ctx.closePath();
    ctx.stroke();

    // Rear trees
    for (const [tx, ty, ts] of [
      [-104, 20, 0.92],
      [104, 20, 0.85],
    ]) {
      ctx.save();
      ctx.translate(tx, ty);
      ctx.scale(ts, ts);
      ctx.fillStyle = FOREST;
      ctx.fillRect(-4.5, -40, 9, 40);
      ctx.beginPath();
      ctx.arc(0, -64, 22, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = EMERALD;
      ctx.beginPath();
      ctx.arc(-11, -50, 14, 0, Math.PI * 2);
      ctx.arc(11, -50, 14, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    }

    // Boundary stakes
    for (const [sx, sy] of [
      [-84, 18],
      [84, 18],
      [-126, 100],
      [126, 100],
    ]) {
      ctx.fillStyle = FOREST;
      ctx.beginPath();
      ctx.arc(sx, sy, 8.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#FFFFFF';
      ctx.beginPath();
      ctx.arc(sx, sy, 4.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = EMERALD;
      ctx.beginPath();
      ctx.arc(sx, sy, 2.2, 0, Math.PI * 2);
      ctx.fill();
    }

    // Location Pin hovering over parcel center
    ctx.strokeStyle = EMERALD;
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.ellipse(0, 43, 38, 13, 0, 0, Math.PI * 2);
    ctx.stroke();

    ctx.fillStyle = FOREST;
    ctx.beginPath();
    ctx.arc(0, -64, 36, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.moveTo(-30.6, -47.8);
    ctx.lineTo(0, 36);
    ctx.lineTo(30.6, -47.8);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(0, -64, 17, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = EMERALD;
    ctx.beginPath();
    ctx.arc(0, -64, 9, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    // Meridian vertical divider with emerald diamonds
    const divInset = Math.min(34, layout.detailsCard.height * 0.12);
    ctx.strokeStyle = FOREST;
    ctx.lineWidth = 4.5;
    ctx.beginPath();
    ctx.moveTo(layout.dividerX, layout.detailsCard.top + divInset);
    ctx.lineTo(layout.dividerX, layout.detailsCard.bottom - divInset);
    ctx.stroke();
    drawDiamondMarker(ctx, layout.dividerX, layout.detailsCard.top + divInset, 7.5, EMERALD, '#FFFFFF');
    drawDiamondMarker(ctx, layout.dividerX, layout.detailsCard.bottom - divInset, 7.5, EMERALD, '#FFFFFF');

    // Dynamic Details Styled Text
    ctx.fillStyle = PINE_TEXT;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    let drawY = layout.textBox.top + (!chosenFit.overflowed ? Math.max(0, (layout.textBox.height - chosenFit.totalH) / 2) : 0);
    for (let gi = 0; gi < chosenFit.wrapped.length; gi++) {
      for (const lineTokens of chosenFit.wrapped[gi]) {
        if (drawY + chosenFit.lineH > layout.textBox.top + layout.textBox.height + 10) break;
        let runX = layout.textBox.left;
        for (const tok of lineTokens) {
          ctx.font = buildCanvasFont(chosenFit.fontSize, tok.bold, tok.italic);
          ctx.fillText(tok.text, runX, drawY);
          runX += ctx.measureText(tok.text).width;
        }
        drawY += chosenFit.lineH;
      }
      if (gi < chosenFit.wrapped.length - 1) drawY += chosenFit.paraGap;
    }

    // Bottom Framed Forest Green Contact Card + Rounded-Square Shield Phone Badge
    ctx.fillStyle = FOREST;
    ctx.beginPath();
    ctx.roundRect(layout.contactRect.left, layout.contactRect.top, layout.contactRect.width, layout.contactRect.height, 22);
    ctx.fill();

    ctx.fillStyle = EMERALD;
    ctx.beginPath();
    ctx.roundRect(layout.contactRect.left + 260, layout.contactRect.top + 8, layout.contactRect.width - 324, 5, 2.5);
    ctx.fill();

    const contactMidY = (layout.contactRect.top + layout.contactRect.bottom) / 2;
    const badgeSize = Math.min(116, layout.contactRect.height * 0.68);
    const badgeCx = layout.contactRect.left + 108;
    const badgeLeft = badgeCx - badgeSize / 2;
    const badgeTop = contactMidY - badgeSize / 2;

    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(badgeLeft, badgeTop, badgeSize, badgeSize, 24);
    ctx.fill();
    ctx.strokeStyle = '#86EFAC';
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.roundRect(badgeLeft + 5, badgeTop + 5, badgeSize - 10, badgeSize - 10, 19);
    ctx.stroke();

    ctx.save();
    ctx.translate(badgeCx, contactMidY);
    ctx.scale((badgeSize * 0.50) / 56, (badgeSize * 0.50) / 56);
    ctx.fillStyle = FOREST;
    drawHandsetPath(ctx);
    ctx.restore();

    const cDivX = layout.contactRect.left + 206;
    const cDivInset = Math.min(28, layout.contactRect.height * 0.16);
    ctx.strokeStyle = '#A7F3D0';
    ctx.lineWidth = 4.5;
    ctx.beginPath();
    ctx.moveTo(cDivX, layout.contactRect.top + cDivInset);
    ctx.lineTo(cDivX, layout.contactRect.bottom - cDivInset);
    ctx.stroke();

    const mobAreaLeft = cDivX + 30;
    const mobAreaW = layout.contactRect.right - 36 - mobAreaLeft;
    ctx.font = `bold ${FIXED_CONTACT_SIZE}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(`যোগাযোগ: ${record.mobile}`, mobAreaLeft + mobAreaW / 2, contactMidY);
    ctx.textAlign = 'left';
    ctx.textBaseline = 'alphabetic';
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
