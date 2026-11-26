import {
  getDefaultTitle,
  getPosterVisibleFieldValues,
  PropertyFieldValue,
  PropertyRecord,
  StyledSegment,
} from './propertyCatalog';

interface StyledToken {
  text: string;
  bold: boolean;
  italic: boolean;
}

interface CellLayout {
  field: PropertyFieldValue;
  label: string;
  lines: StyledToken[][];
  labelSize: number;
  valueSize: number;
  lineH: number;
  padH: number;
  padV: number;
  labelH: number;
  labelGap: number;
  height: number;
}

interface RowLayout {
  cells: CellLayout[];
  height: number;
}

function buildFont(size: number, bold: boolean, italic: boolean): string {
  const style = `${italic ? 'italic ' : ''}${bold ? 'bold ' : '500 '}`;
  return `${style}${size}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
}

function wrapSegments(
  ctx: CanvasRenderingContext2D,
  segments: StyledSegment[],
  maxWidth: number,
  fontSize: number
): StyledToken[][] {
  const subLines: StyledSegment[][] = [[]];
  for (const seg of segments) {
    const parts = seg.text.split(/\r?\n/);
    parts.forEach((p, idx) => {
      if (p) subLines[subLines.length - 1].push({ text: p, bold: seg.bold, italic: seg.italic });
      if (idx < parts.length - 1) subLines.push([]);
    });
  }

  const lines: StyledToken[][] = [];
  for (const sub of subLines) {
    const tokens: StyledToken[] = [];
    for (const seg of sub) {
      for (const chunk of seg.text.split(/(\s+)/)) {
        if (chunk) tokens.push({ text: chunk, bold: seg.bold, italic: seg.italic });
      }
    }
    let current: StyledToken[] = [];
    let currentW = 0;
    for (const tok of tokens) {
      const isWs = /^\s+$/.test(tok.text);
      if (current.length === 0 && isWs) continue;
      ctx.font = buildFont(fontSize, tok.bold, tok.italic);
      const tokW = ctx.measureText(tok.text).width;
      if (currentW + tokW <= maxWidth || current.length === 0) {
        current.push(tok);
        currentW += tokW;
      } else {
        while (current.length > 0 && /^\s+$/.test(current[current.length - 1].text)) {
          current.pop();
        }
        if (current.length > 0) lines.push(current);
        if (isWs) {
          current = [];
          currentW = 0;
        } else {
          current = [tok];
          currentW = tokW;
        }
      }
    }
    while (current.length > 0 && /^\s+$/.test(current[current.length - 1].text)) {
      current.pop();
    }
    if (current.length > 0) lines.push(current);
  }
  return lines.length > 0 ? lines : [[{ text: '-', bold: false, italic: false }]];
}

function isCompactSingleLine(fv: PropertyFieldValue): boolean {
  const t = fv.plainText.trim();
  return t.length > 0 && !t.includes('\n') && !t.includes('\r') && t.length <= 26;
}

function measureCell(
  ctx: CanvasRenderingContext2D,
  fv: PropertyFieldValue,
  cardW: number,
  labelSize: number,
  valueSize: number,
  padH: number,
  padV: number,
  labelGap: number
): CellLayout {
  const innerW = Math.max(80, cardW - 2 * padH);
  const lines = wrapSegments(ctx, fv.segments, innerW, valueSize);
  const lineH = valueSize * 1.24;
  const labelH = labelSize * 1.2;
  const height = padV * 2 + labelH + labelGap + lines.length * lineH;
  return {
    field: fv,
    label: fv.label || 'তথ্য',
    lines,
    labelSize,
    valueSize,
    lineH,
    padH,
    padV,
    labelH,
    labelGap,
    height,
  };
}

function measureRows(
  ctx: CanvasRenderingContext2D,
  fields: PropertyFieldValue[],
  totalW: number,
  colGap: number,
  allowTwoCol: boolean,
  labelSize: number,
  valueSize: number,
  padH: number,
  padV: number,
  labelGap: number
): RowLayout[] {
  const rows: RowLayout[] = [];
  let i = 0;
  while (i < fields.length) {
    const cur = fields[i];
    const canPair =
      allowTwoCol &&
      fields.length >= 3 &&
      i + 1 < fields.length &&
      isCompactSingleLine(cur) &&
      isCompactSingleLine(fields[i + 1]);
    if (canPair) {
      const halfW = (totalW - colGap) / 2;
      const c1 = measureCell(ctx, cur, halfW, labelSize, valueSize, padH, padV, labelGap);
      const c2 = measureCell(ctx, fields[i + 1], halfW, labelSize, valueSize, padH, padV, labelGap);
      if (c1.lines.length <= 2 && c2.lines.length <= 2) {
        rows.push({ cells: [c1, c2], height: Math.max(c1.height, c2.height) });
        i += 2;
        continue;
      }
    }
    const full = measureCell(ctx, cur, totalW, labelSize, valueSize, padH, padV, labelGap);
    rows.push({ cells: [full], height: full.height });
    i += 1;
  }
  return rows;
}

function drawDiamond(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  r: number,
  outer: string,
  inner: string
) {
  ctx.fillStyle = outer;
  ctx.beginPath();
  ctx.moveTo(cx, cy - r);
  ctx.lineTo(cx + r, cy);
  ctx.lineTo(cx, cy + r);
  ctx.lineTo(cx - r, cy);
  ctx.closePath();
  ctx.fill();

  const ir = r * 0.45;
  ctx.fillStyle = inner;
  ctx.beginPath();
  ctx.moveTo(cx, cy - ir);
  ctx.lineTo(cx + ir, cy);
  ctx.lineTo(cx, cy + ir);
  ctx.lineTo(cx - ir, cy);
  ctx.closePath();
  ctx.fill();
}

function drawHandset(ctx: CanvasRenderingContext2D) {
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

function drawHouseIllustration(
  ctx: CanvasRenderingContext2D,
  box: { left: number; top: number; width: number; height: number },
  navy: string
) {
  const cx = box.left + box.width / 2;
  const cy = box.top + box.height / 2;
  const scale = Math.min(1.05, Math.min((box.width - 36) / 340, (box.height - 20) / 310));
  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(scale, scale);
  ctx.translate(-210, -613);

  ctx.fillStyle = navy;
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

  ctx.strokeStyle = navy;
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
}

function drawLandIllustration(
  ctx: CanvasRenderingContext2D,
  box: { left: number; top: number; width: number; height: number },
  forest: string,
  emerald: string
) {
  const cx = box.left + box.width / 2;
  const cy = box.top + box.height / 2 + 8;
  const scale = Math.min(1.05, Math.min((box.width - 36) / 320, (box.height - 20) / 280));
  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(scale, scale);

  ctx.fillStyle = forest;
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
  ctx.strokeStyle = emerald;
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.moveTo(0, 18);
  ctx.lineTo(0, 100);
  ctx.moveTo(-106, 58);
  ctx.lineTo(106, 58);
  ctx.stroke();
  ctx.restore();

  ctx.strokeStyle = forest;
  ctx.lineWidth = 5.5;
  ctx.beginPath();
  ctx.moveTo(-84, 18);
  ctx.lineTo(84, 18);
  ctx.lineTo(126, 100);
  ctx.lineTo(-126, 100);
  ctx.closePath();
  ctx.stroke();

  for (const [tx, ty, ts] of [
    [-104, 20, 0.92],
    [104, 20, 0.85],
  ]) {
    ctx.save();
    ctx.translate(tx, ty);
    ctx.scale(ts, ts);
    ctx.fillStyle = forest;
    ctx.fillRect(-4.5, -40, 9, 40);
    ctx.beginPath();
    ctx.arc(0, -64, 22, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = emerald;
    ctx.beginPath();
    ctx.arc(-11, -50, 14, 0, Math.PI * 2);
    ctx.arc(11, -50, 14, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  for (const [sx, sy] of [
    [-84, 18],
    [84, 18],
    [-126, 100],
    [126, 100],
  ]) {
    ctx.fillStyle = forest;
    ctx.beginPath();
    ctx.arc(sx, sy, 8.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(sx, sy, 4.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = emerald;
    ctx.beginPath();
    ctx.arc(sx, sy, 2.2, 0, Math.PI * 2);
    ctx.fill();
  }

  ctx.strokeStyle = emerald;
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.ellipse(0, 43, 38, 13, 0, 0, Math.PI * 2);
  ctx.stroke();

  ctx.fillStyle = forest;
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
  ctx.fillStyle = emerald;
  ctx.beginPath();
  ctx.arc(0, -64, 9, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
}

export function renderPortraitPosterDataUrl(record: PropertyRecord): string {
  const W = 1440;
  const H = 1920;
  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';

  const isHouse = record.category === 'HOUSE_FOR_RENT';
  const cardLeft = 56;
  const cardRight = W - 56;
  const cardW = cardRight - cardLeft;
  const headerTop = 38;
  const headerH = 152;
  const titleTop = 202;

  const titleText = (record.title || getDefaultTitle(record.category)).trim();
  const visibleFields = getPosterVisibleFieldValues(record);

  // Steady title sizing: shrink slightly only if user edits a very long title
  let titleFontSize = 80;
  while (titleFontSize > 48) {
    ctx.font = `bold ${titleFontSize}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
    if (ctx.measureText(titleText).width <= cardW - 140) break;
    titleFontSize -= 2;
  }
  const titleH = Math.max(160, Math.min(220, titleFontSize * 1.45 + 52));
  const titleBottom = titleTop + titleH;

  const contactH = 158;
  const contactBottom = H - 54;
  const contactTop = contactBottom - contactH;

  const bodyOuterTop = titleBottom + 26;
  const bodyOuterBottom = contactTop - 26;
  const bodyFramePadH = 30;
  const bodyFramePadV = 28;
  const contentLeft = cardLeft + bodyFramePadH;
  const contentRight = cardRight - bodyFramePadH;
  const contentW = contentRight - contentLeft;
  const maxContentH = bodyOuterBottom - bodyOuterTop - bodyFramePadV * 2;
  const colGap = 22;

  let chosenRows: RowLayout[] = [];
  let chosenShowcaseH = 216;
  let chosenShowcaseGap = 24;
  let chosenRowGap = 20;
  let chosenValueSize = 42;
  let overflowed = true;

  for (let valSize = 46; valSize >= 20; valSize -= 2) {
    const tNorm = (valSize - 20) / (46 - 20);
    const labelSize = Math.max(17, valSize * 0.64);
    const padH = 18 + tNorm * 14;
    const padV = 12 + tNorm * 12;
    const labelGap = 6 + tNorm * 5;
    const rowGap = 12 + tNorm * 12;
    const showcaseH = visibleFields.length > 5 ? 168 : 176 + tNorm * 46;
    const showcaseGap = 16 + tNorm * 12;

    const rows = measureRows(
      ctx,
      visibleFields,
      contentW,
      colGap,
      !isHouse,
      labelSize,
      valSize,
      padH,
      padV,
      labelGap
    );
    const rowsH =
      rows.reduce((sum, r) => sum + r.height, 0) + Math.max(0, rows.length - 1) * rowGap;
    const totalH = showcaseH + (rows.length > 0 ? showcaseGap + rowsH : 0);

    chosenRows = rows;
    chosenShowcaseH = showcaseH;
    chosenShowcaseGap = showcaseGap;
    chosenRowGap = rowGap;
    chosenValueSize = valSize;
    if (totalH <= maxContentH) {
      overflowed = false;
      break;
    }
  }

  const rowsTotalH =
    chosenRows.reduce((s, r) => s + r.height, 0) +
    Math.max(0, chosenRows.length - 1) * chosenRowGap;
  const totalStackH =
    chosenShowcaseH + (chosenRows.length > 0 ? chosenShowcaseGap + rowsTotalH : 0);
  const availH = bodyOuterBottom - bodyOuterTop;
  const frameH = overflowed
    ? availH
    : Math.max(360, Math.min(availH, totalStackH + bodyFramePadV * 2));
  const frameTop = bodyOuterTop + (availH - frameH) * 0.35;
  const frameBottom = frameTop + frameH;
  const contentStartY = frameTop + bodyFramePadV + Math.max(0, (frameH - bodyFramePadV * 2 - totalStackH) * 0.35);

  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, W, H);

  if (isHouse) {
    const NAVY = '#032668';
    const SOFT_BLUE = '#EFF5FF';
    const CARD_BORDER = '#B8CDE8';
    const MUTED_BLUE = '#33538A';

    // Outer rounded navy frame
    ctx.strokeStyle = NAVY;
    ctx.lineWidth = 7;
    ctx.beginPath();
    ctx.roundRect(22, 22, W - 44, H - 44, 48);
    ctx.stroke();

    // Top House Roof Silhouette
    ctx.save();
    ctx.translate(0, headerTop - 22);
    ctx.fillStyle = NAVY;
    ctx.beginPath();
    ctx.moveTo(96, 114);
    ctx.lineTo(430, 106);
    ctx.arc(430, 114, 8, -Math.PI / 2, Math.PI / 2);
    ctx.closePath();
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(W - 96, 114);
    ctx.lineTo(W - 430, 106);
    ctx.arc(W - 430, 114, 8, -Math.PI / 2, Math.PI / 2, true);
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

    const cx = W / 2;
    ctx.fillRect(cx - 22, 98, 18, 18);
    ctx.fillRect(cx + 4, 98, 18, 18);
    ctx.fillRect(cx - 22, 124, 18, 18);
    ctx.fillRect(cx + 4, 124, 18, 18);
    ctx.restore();

    // Title Banner
    ctx.fillStyle = NAVY;
    ctx.beginPath();
    ctx.roundRect(cardLeft, titleTop, cardW, titleH, 48);
    ctx.fill();
    ctx.font = `bold ${titleFontSize}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(titleText, W / 2, (titleTop + titleBottom) / 2);

    // Body Outer Frame
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(cardLeft, frameTop, cardW, frameH, 42);
    ctx.fill();
    ctx.strokeStyle = NAVY;
    ctx.lineWidth = 6;
    ctx.stroke();

    // Showcase band inside top of body frame
    const showcaseBox = { left: contentLeft, top: contentStartY, width: contentW, height: chosenShowcaseH };
    ctx.fillStyle = SOFT_BLUE;
    ctx.beginPath();
    ctx.roundRect(showcaseBox.left, showcaseBox.top, showcaseBox.width, showcaseBox.height, 26);
    ctx.fill();
    drawHouseIllustration(ctx, showcaseBox, NAVY);

    // Dynamic non-empty fields
    let curY = contentStartY + chosenShowcaseH + chosenShowcaseGap;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    for (const row of chosenRows) {
      const cell = row.cells[0];
      ctx.fillStyle = SOFT_BLUE;
      ctx.beginPath();
      ctx.roundRect(contentLeft, curY, contentW, row.height, 24);
      ctx.fill();
      ctx.strokeStyle = CARD_BORDER;
      ctx.lineWidth = 2.5;
      ctx.stroke();

      // Navy accent bar on left
      ctx.fillStyle = NAVY;
      ctx.beginPath();
      ctx.roundRect(contentLeft, curY + 12, 8, row.height - 24, 4);
      ctx.fill();

      const innerX = contentLeft + cell.padH + 8;
      let textY = curY + cell.padV;
      ctx.fillStyle = MUTED_BLUE;
      ctx.font = `bold ${cell.labelSize}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
      ctx.fillText(cell.label, innerX, textY);
      textY += cell.labelH + cell.labelGap;

      ctx.fillStyle = NAVY;
      for (const lineTokens of cell.lines) {
        let runX = innerX;
        for (const tok of lineTokens) {
          ctx.font = buildFont(cell.valueSize, tok.bold, tok.italic);
          ctx.fillText(tok.text, runX, textY);
          runX += ctx.measureText(tok.text).width;
        }
        textY += cell.lineH;
      }
      curY += row.height + chosenRowGap;
    }

    // Contact Banner
    ctx.fillStyle = NAVY;
    ctx.beginPath();
    ctx.roundRect(cardLeft, contactTop, cardW, contactH, 48);
    ctx.fill();

    const contactMidY = (contactTop + contactBottom) / 2;
    const phoneR = 50;
    const phoneCx = cardLeft + 104;
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(phoneCx, contactMidY, phoneR, 0, Math.PI * 2);
    ctx.fill();
    ctx.save();
    ctx.translate(phoneCx, contactMidY);
    ctx.scale(phoneR / 64, phoneR / 64);
    ctx.fillStyle = NAVY;
    drawHandset(ctx);
    ctx.restore();

    const cDivX = cardLeft + 196;
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 5;
    ctx.beginPath();
    ctx.moveTo(cDivX, contactTop + 28);
    ctx.lineTo(cDivX, contactBottom - 28);
    ctx.stroke();

    const contactStr = `যোগাযোগ: ${record.mobile.trim()}`;
    let contactFontSize = 68;
    while (contactFontSize > 40) {
      ctx.font = `bold ${contactFontSize}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
      if (ctx.measureText(contactStr).width <= cardRight - 36 - (cDivX + 26)) break;
      contactFontSize -= 2;
    }
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(contactStr, (cDivX + 26 + cardRight - 36) / 2, contactMidY);
  } else {
    const FOREST = '#0B5D2E';
    const EMERALD = '#16A34A';
    const SOFT_MINT = '#EEFBF3';
    const PINE_TEXT = '#063B1D';
    const CARD_BORDER = '#A7DFB9';

    // Double Surveyor Outer Frame
    ctx.strokeStyle = FOREST;
    ctx.lineWidth = 6.5;
    ctx.beginPath();
    ctx.roundRect(22, 22, W - 44, H - 44, 30);
    ctx.stroke();

    ctx.strokeStyle = EMERALD;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.roundRect(32, 32, W - 64, H - 64, 24);
    ctx.stroke();

    // Header Pin & Boundary Emblem
    const hCx = W / 2;
    const hBottom = headerTop + headerH - 6;
    const barY = hBottom - 28;
    ctx.strokeStyle = FOREST;
    ctx.lineWidth = 6;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(hCx - 520, barY);
    ctx.lineTo(hCx - 158, barY);
    ctx.moveTo(hCx + 158, barY);
    ctx.lineTo(hCx + 520, barY);
    ctx.stroke();

    drawDiamond(ctx, hCx - 158, barY, 10, FOREST, '#FFFFFF');
    drawDiamond(ctx, hCx + 158, barY, 10, FOREST, '#FFFFFF');

    const pinCy = headerTop + 42;
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

    // Title Card
    ctx.fillStyle = FOREST;
    ctx.beginPath();
    ctx.roundRect(cardLeft, titleTop, cardW, titleH, 24);
    ctx.fill();
    const stripW = Math.min(460, cardW * 0.36);
    const stripL = W / 2 - stripW / 2;
    ctx.fillStyle = EMERALD;
    ctx.beginPath();
    ctx.roundRect(stripL, titleTop + 8, stripW, 6, 3);
    ctx.roundRect(stripL, titleBottom - 14, stripW, 6, 3);
    ctx.fill();
    const titleMidY = (titleTop + titleBottom) / 2;
    drawDiamond(ctx, cardLeft + 68, titleMidY, 14, EMERALD, '#FFFFFF');
    drawDiamond(ctx, cardRight - 68, titleMidY, 14, EMERALD, '#FFFFFF');
    ctx.font = `bold ${titleFontSize}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(titleText, W / 2, titleMidY);

    // Body Outer Frame
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(cardLeft, frameTop, cardW, frameH, 26);
    ctx.fill();
    ctx.strokeStyle = FOREST;
    ctx.lineWidth = 5.5;
    ctx.stroke();

    // Showcase Plot Parcel Graphic
    const showcaseBox = { left: contentLeft, top: contentStartY, width: contentW, height: chosenShowcaseH };
    ctx.fillStyle = SOFT_MINT;
    ctx.beginPath();
    ctx.roundRect(showcaseBox.left, showcaseBox.top, showcaseBox.width, showcaseBox.height, 20);
    ctx.fill();
    ctx.strokeStyle = CARD_BORDER;
    ctx.lineWidth = 2;
    ctx.stroke();
    drawLandIllustration(ctx, showcaseBox, FOREST, EMERALD);

    // Dynamic 1-col / 2-col non-empty field cards
    let curY = contentStartY + chosenShowcaseH + chosenShowcaseGap;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    for (const row of chosenRows) {
      const colW = row.cells.length === 2 ? (contentW - colGap) / 2 : contentW;
      row.cells.forEach((cell, idx) => {
        const cx = contentLeft + idx * (colW + colGap);
        ctx.fillStyle = SOFT_MINT;
        ctx.beginPath();
        ctx.roundRect(cx, curY, colW, row.height, 20);
        ctx.fill();
        ctx.strokeStyle = CARD_BORDER;
        ctx.lineWidth = 2.5;
        ctx.stroke();

        ctx.fillStyle = EMERALD;
        ctx.beginPath();
        ctx.roundRect(cx + 14, curY + 8, Math.min(72, colW * 0.25), 5, 2.5);
        ctx.fill();

        const innerX = cx + cell.padH;
        let textY = curY + cell.padV + 4;
        ctx.fillStyle = FOREST;
        ctx.font = `bold ${cell.labelSize}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
        ctx.fillText(cell.label, innerX, textY);
        textY += cell.labelH + cell.labelGap;

        ctx.fillStyle = PINE_TEXT;
        for (const lineTokens of cell.lines) {
          let runX = innerX;
          for (const tok of lineTokens) {
            ctx.font = buildFont(cell.valueSize, tok.bold, tok.italic);
            ctx.fillText(tok.text, runX, textY);
            runX += ctx.measureText(tok.text).width;
          }
          textY += cell.lineH;
        }
      });
      curY += row.height + chosenRowGap;
    }

    // Contact Banner
    ctx.fillStyle = FOREST;
    ctx.beginPath();
    ctx.roundRect(cardLeft, contactTop, cardW, contactH, 24);
    ctx.fill();
    ctx.fillStyle = EMERALD;
    ctx.beginPath();
    ctx.roundRect(cardLeft + 250, contactTop + 8, cardW - 310, 5, 2.5);
    ctx.fill();

    const contactMidY = (contactTop + contactBottom) / 2;
    const badgeSize = 104;
    const badgeCx = cardLeft + 104;
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(badgeCx - badgeSize / 2, contactMidY - badgeSize / 2, badgeSize, badgeSize, 22);
    ctx.fill();
    ctx.save();
    ctx.translate(badgeCx, contactMidY);
    ctx.scale((badgeSize * 0.5) / 56, (badgeSize * 0.5) / 56);
    ctx.fillStyle = FOREST;
    drawHandset(ctx);
    ctx.restore();

    const cDivX = cardLeft + 196;
    ctx.strokeStyle = '#A7F3D0';
    ctx.lineWidth = 4.5;
    ctx.beginPath();
    ctx.moveTo(cDivX, contactTop + 26);
    ctx.lineTo(cDivX, contactBottom - 26);
    ctx.stroke();

    const contactStr = `যোগাযোগ: ${record.mobile.trim()}`;
    let contactFontSize = 68;
    while (contactFontSize > 40) {
      ctx.font = `bold ${contactFontSize}px "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
      if (ctx.measureText(contactStr).width <= cardRight - 36 - (cDivX + 26)) break;
      contactFontSize -= 2;
    }
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(contactStr, (cDivX + 26 + cardRight - 36) / 2, contactMidY);
  }

  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  return canvas.toDataURL('image/jpeg', 0.92);
}
