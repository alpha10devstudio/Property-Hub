import {
  getDefaultTitle,
  getPosterVisibleFieldValues,
  PropertyFieldValue,
  PropertyRecord,
  StyledSegment,
} from './propertyCatalog';

export interface RenderedPosterOutput {
  dataUrl: string;
  width: number;
  height: number;
  overflowed: boolean;
}

interface RichRun {
  text: string;
  bold: boolean;
  italic: boolean;
  isLabel?: boolean;
}

let fontsLoadPromise: Promise<void> | null = null;

export function ensurePosterFontsLoaded(): Promise<void> {
  if (fontsLoadPromise) return fontsLoadPromise;
  if (typeof document === 'undefined' || !('fonts' in document)) {
    fontsLoadPromise = Promise.resolve();
    return fontsLoadPromise;
  }

  fontsLoadPromise = (async () => {
    try {
      const loaders: Promise<unknown>[] = [];
      if (typeof FontFace !== 'undefined') {
        const titleFace = new FontFace('PosterTitle', 'url(/fonts/title.ttf)');
        const paraFace = new FontFace('PosterParagraph', 'url(/fonts/paragraph.ttf)');
        loaders.push(
          titleFace.load().then((loaded) => {
            document.fonts.add(loaded);
          }),
          paraFace.load().then((loaded) => {
            document.fonts.add(loaded);
          })
        );
      }
      await Promise.allSettled(loaders);
      await document.fonts.ready;
    } catch {
      // Fall back gracefully if FontFace loading fails in restricted environments
    }
  })();

  return fontsLoadPromise;
}

function buildTitleFont(size: number): string {
  return `bold ${size}px "PosterTitle", "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
}

function buildBodyFont(size: number, bold: boolean, italic: boolean): string {
  const style = `${italic ? 'italic ' : ''}${bold ? 'bold ' : ''}`;
  return `${style}${size}px "PosterParagraph", "Noto Sans Bengali", "Plus Jakarta Sans", sans-serif`;
}

/**
 * Wraps rich text segments (supporting newlines, bold, italic, and long unspaced words)
 * inside maxWidth without clipping or horizontal overflow.
 */
function wrapRichRuns(
  ctx: CanvasRenderingContext2D,
  runs: RichRun[],
  maxWidth: number,
  fontSize: number,
  boldLabels = true
): RichRun[][] {
  const paragraphs: RichRun[][] = [[]];
  for (const run of runs) {
    const parts = run.text.split(/\r?\n/);
    parts.forEach((part, idx) => {
      if (part.length > 0) {
        paragraphs[paragraphs.length - 1].push({
          text: part,
          bold: run.bold || (Boolean(run.isLabel) && boldLabels),
          italic: run.italic,
          isLabel: run.isLabel,
        });
      }
      if (idx < parts.length - 1) {
        paragraphs.push([]);
      }
    });
  }

  const lines: RichRun[][] = [];
  for (const para of paragraphs) {
    if (para.length === 0) {
      continue;
    }

    const tokens: RichRun[] = [];
    for (const seg of para) {
      for (const chunk of seg.text.split(/(\s+)/)) {
        if (!chunk) continue;
        ctx.font = buildBodyFont(fontSize, seg.bold, seg.italic);
        if (ctx.measureText(chunk).width <= maxWidth || /^\s+$/.test(chunk)) {
          tokens.push({ ...seg, text: chunk });
        } else {
          // Break oversized single word character-by-character so it never clips horizontally
          let buf = '';
          for (const ch of Array.from(chunk)) {
            const candidate = buf + ch;
            if (buf && ctx.measureText(candidate).width > maxWidth) {
              tokens.push({ ...seg, text: buf });
              buf = ch;
            } else {
              buf = candidate;
            }
          }
          if (buf) tokens.push({ ...seg, text: buf });
        }
      }
    }

    let currentLine: RichRun[] = [];
    let currentWidth = 0;

    for (const tok of tokens) {
      const isWs = /^\s+$/.test(tok.text);
      if (currentLine.length === 0 && isWs) continue;
      ctx.font = buildBodyFont(fontSize, tok.bold, tok.italic);
      const tokWidth = ctx.measureText(tok.text).width;

      if (currentWidth + tokWidth <= maxWidth || currentLine.length === 0) {
        currentLine.push(tok);
        currentWidth += tokWidth;
      } else {
        while (currentLine.length > 0 && /^\s+$/.test(currentLine[currentLine.length - 1].text)) {
          currentLine.pop();
        }
        if (currentLine.length > 0) {
          lines.push(currentLine);
        }
        if (isWs) {
          currentLine = [];
          currentWidth = 0;
        } else {
          currentLine = [tok];
          currentWidth = tokWidth;
        }
      }
    }

    while (currentLine.length > 0 && /^\s+$/.test(currentLine[currentLine.length - 1].text)) {
      currentLine.pop();
    }
    if (currentLine.length > 0) {
      lines.push(currentLine);
    }
  }

  return lines;
}

function wrapPlainTitleLines(
  ctx: CanvasRenderingContext2D,
  text: string,
  maxWidth: number,
  fontSize: number
): string[] {
  ctx.font = buildTitleFont(fontSize);
  const words = text.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return [''];
  const lines: string[] = [];
  let current = '';
  for (const w of words) {
    const candidate = current ? `${current} ${w}` : w;
    if (ctx.measureText(candidate).width <= maxWidth || !current) {
      current = candidate;
    } else {
      lines.push(current);
      current = w;
    }
  }
  if (current) lines.push(current);
  return lines;
}

function buildFieldInlineRuns(fv: PropertyFieldValue): RichRun[] {
  const runs: RichRun[] = [];
  const label = fv.label.trim();
  if (label.length > 0) {
    runs.push({
      text: `${label}ঃ `,
      bold: false,
      italic: false,
      isLabel: true,
    });
  }
  const segs: StyledSegment[] =
    fv.segments && fv.segments.length > 0
      ? fv.segments
      : fv.plainText.trim()
        ? [{ text: fv.plainText.trim(), bold: false, italic: false }]
        : [];
  for (const seg of segs) {
    if (seg.text) {
      runs.push({
        text: seg.text,
        bold: seg.bold,
        italic: seg.italic,
      });
    }
  }
  return runs;
}

function drawRichLine(
  ctx: CanvasRenderingContext2D,
  line: RichRun[],
  startX: number,
  baselineY: number,
  fontSize: number,
  textColor: string,
  labelColor: string
) {
  let runX = startX;
  for (const tok of line) {
    ctx.font = buildBodyFont(fontSize, tok.bold, tok.italic);
    const color = tok.isLabel ? labelColor : textColor;
    const rawW = ctx.measureText(tok.text).width;

    ctx.save();
    ctx.translate(runX, baselineY);
    if (tok.italic) {
      ctx.transform(1, 0, -0.22, 1, 0, 0);
    }
    if (tok.bold) {
      ctx.lineWidth = Math.max(1.4, fontSize * 0.036);
      ctx.lineJoin = 'round';
      ctx.strokeStyle = color;
      ctx.strokeText(tok.text, 0, 0);
    }
    ctx.fillStyle = color;
    ctx.fillText(tok.text, 0, 0);
    ctx.restore();

    runX += rawW + (tok.bold ? Math.max(1.5, fontSize * 0.035) : 0);
  }
}

function measureRichLineWidth(
  ctx: CanvasRenderingContext2D,
  line: RichRun[],
  fontSize: number
): number {
  let width = 0;
  for (const tok of line) {
    ctx.font = buildBodyFont(fontSize, tok.bold, tok.italic);
    width += ctx.measureText(tok.text).width + (tok.bold ? Math.max(1.5, fontSize * 0.035) : 0);
  }
  return width;
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

/**
 * Reference Image 1 Top Header: Navy Roof Silhouette with 2x2 windows & tapered horizontal lines.
 */
function drawHouseTopRoofHeader(
  ctx: CanvasRenderingContext2D,
  W: number,
  topY: number,
  navy: string
) {
  ctx.save();
  ctx.translate(0, topY);
  ctx.fillStyle = navy;

  // Left tapered wing (sharp outer tip -> rounded inner cap)
  ctx.beginPath();
  ctx.moveTo(96, 98);
  ctx.lineTo(426, 91);
  ctx.arc(426, 98, 7, -Math.PI / 2, Math.PI / 2);
  ctx.closePath();
  ctx.fill();

  // Right tapered wing
  ctx.beginPath();
  ctx.moveTo(W - 96, 98);
  ctx.lineTo(W - 426, 91);
  ctx.arc(W - 426, 98, 7, -Math.PI / 2, Math.PI / 2, true);
  ctx.closePath();
  ctx.fill();

  // Secondary left roof accent beam
  ctx.beginPath();
  ctx.moveTo(436, 134);
  ctx.lineTo(618, 36);
  ctx.lineTo(632, 44);
  ctx.lineTo(466, 134);
  ctx.closePath();
  ctx.fill();

  // Main pitched roof + right chimney
  ctx.beginPath();
  ctx.moveTo(480, 134);
  ctx.lineTo(720, 16);
  ctx.lineTo(824, 68);
  ctx.lineTo(824, 38);
  ctx.lineTo(862, 38);
  ctx.lineTo(862, 87);
  ctx.lineTo(962, 134);
  ctx.lineTo(890, 134);
  ctx.lineTo(720, 48);
  ctx.lineTo(548, 134);
  ctx.closePath();
  ctx.fill();

  // Centered 2x2 window grid under gable apex
  const cx = W / 2;
  ctx.fillRect(cx - 21, 82, 18, 18);
  ctx.fillRect(cx + 3, 82, 18, 18);
  ctx.fillRect(cx - 21, 106, 18, 18);
  ctx.fillRect(cx + 3, 106, 18, 18);

  ctx.restore();
}

/**
 * Reference Image 1 Left Pane Illustration: Navy two-story house + leafy tree + curved ground base.
 * Maintains strict 1:1 natural aspect ratio regardless of vertical panel expansion.
 */
function drawHousePanelIllustration(
  ctx: CanvasRenderingContext2D,
  centerX: number,
  centerY: number,
  maxW: number,
  maxH: number,
  navy: string
) {
  const scale = Math.min(0.95, Math.min((maxW - 24) / 310, (maxH - 20) / 330));
  ctx.save();
  ctx.translate(centerX, centerY);
  ctx.scale(scale, scale);
  ctx.translate(-210, -613);

  ctx.fillStyle = navy;
  // Curved ground hill
  ctx.beginPath();
  ctx.moveTo(58, 772);
  ctx.quadraticCurveTo(208, 742, 362, 772);
  ctx.quadraticCurveTo(208, 756, 58, 772);
  ctx.closePath();
  ctx.fill();

  // Left tree trunk + canopy + white branch veins
  ctx.fillRect(92, 706, 12, 58);
  for (const [cx, cy, r] of [
    [98, 672, 31],
    [80, 692, 21],
    [116, 692, 21],
  ]) {
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.fill();
  }

  ctx.strokeStyle = '#FFFFFF';
  ctx.lineWidth = 3;
  ctx.lineCap = 'round';
  ctx.beginPath();
  ctx.moveTo(98, 706);
  ctx.lineTo(88, 690);
  ctx.moveTo(98, 698);
  ctx.lineTo(108, 684);
  ctx.stroke();

  // Left wing & right wing walls
  ctx.strokeStyle = navy;
  ctx.lineWidth = 6.5;
  ctx.lineJoin = 'round';
  ctx.strokeRect(140, 522, 72, 236);
  ctx.beginPath();
  ctx.moveTo(132, 522);
  ctx.lineTo(214, 522);
  ctx.moveTo(212, 504);
  ctx.lineTo(212, 758);
  ctx.moveTo(334, 504);
  ctx.lineTo(334, 758);
  ctx.moveTo(130, 610);
  ctx.lineTo(344, 610);
  ctx.stroke();

  // Pitched main roof + right chimney
  ctx.beginPath();
  ctx.moveTo(192, 514);
  ctx.lineTo(274, 452);
  ctx.lineTo(312, 476);
  ctx.lineTo(312, 456);
  ctx.lineTo(332, 456);
  ctx.lineTo(332, 488);
  ctx.lineTo(358, 514);
  ctx.lineTo(340, 526);
  ctx.lineTo(274, 470);
  ctx.lineTo(206, 526);
  ctx.closePath();
  ctx.fill();

  // Upper gable 2x2 windows
  ctx.fillRect(251, 508, 20, 20);
  ctx.fillRect(277, 508, 20, 20);
  ctx.fillRect(251, 534, 20, 20);
  ctx.fillRect(277, 534, 20, 20);

  // Upper left window with vertical mullion
  ctx.lineWidth = 4.5;
  ctx.strokeRect(154, 538, 46, 54);
  ctx.beginPath();
  ctx.moveTo(177, 538);
  ctx.lineTo(177, 592);
  ctx.stroke();

  // Ground left square window
  ctx.strokeRect(154, 628, 44, 48);

  // Solid navy front door with white handles
  ctx.fillRect(252, 628, 62, 130);
  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.arc(274, 694, 3.4, 0, Math.PI * 2);
  ctx.arc(292, 694, 3.4, 0, Math.PI * 2);
  ctx.fill();

  ctx.restore();
}

/**
 * Reference Image 2 Top Header: Green location pin over curved contour arcs,
 * flanked by 2 trees and left/right tapered horizontal bars.
 */
function drawLandTopEmblemHeader(
  ctx: CanvasRenderingContext2D,
  W: number,
  topY: number,
  green: string
) {
  const cx = W / 2;
  ctx.save();
  ctx.translate(0, topY);

  // Tapered left & right horizontal decorative bars (thick inner round cap -> sharp outer point)
  ctx.fillStyle = green;
  ctx.beginPath();
  ctx.moveTo(80, 74);
  ctx.lineTo(448, 66);
  ctx.arc(448, 74, 8, -Math.PI / 2, Math.PI / 2);
  ctx.closePath();
  ctx.fill();

  ctx.beginPath();
  ctx.moveTo(W - 80, 74);
  ctx.lineTo(W - 448, 66);
  ctx.arc(W - 448, 74, 8, -Math.PI / 2, Math.PI / 2, true);
  ctx.closePath();
  ctx.fill();

  // Sweeping concentric curved field contour lines under location pin
  ctx.strokeStyle = green;
  ctx.lineCap = 'round';

  const arcs: { w: number; h: number; lw: number }[] = [
    { w: 280, h: 52, lw: 4.5 },
    { w: 215, h: 42, lw: 5 },
    { w: 155, h: 32, lw: 5.5 },
    { w: 96, h: 20, lw: 5 },
  ];
  const baseLineY = 124;
  for (const a of arcs) {
    ctx.lineWidth = a.lw;
    ctx.beginPath();
    ctx.moveTo(cx - a.w, baseLineY);
    ctx.quadraticCurveTo(cx, baseLineY - a.h * 2, cx + a.w, baseLineY);
    ctx.stroke();
  }

  // Flanking trees (left and right) — draw each lobe separately to avoid winding cutouts
  for (const [tx, ty, s] of [
    [cx - 184, baseLineY - 11, 1.0],
    [cx + 184, baseLineY - 14, 0.92],
  ]) {
    ctx.save();
    ctx.translate(tx, ty);
    ctx.scale(s, s);
    ctx.fillStyle = green;
    ctx.fillRect(-4, -34, 8, 36);
    for (const [lx, ly, lr] of [
      [0, -56, 20],
      [-14, -44, 14],
      [14, -44, 14],
      [-6, -68, 13],
      [8, -66, 12],
    ]) {
      ctx.beginPath();
      ctx.arc(lx, ly, lr, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
  }

  // Centered Location Pin
  const pinCy = 36;
  const pinR = 32;
  // White halo behind pin tip so contour lines stay crisp
  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.arc(cx, pinCy, pinR + 4, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.moveTo(cx - (pinR + 4) * 0.85, pinCy + (pinR + 4) * 0.45);
  ctx.lineTo(cx, baseLineY - 2);
  ctx.lineTo(cx + (pinR + 4) * 0.85, pinCy + (pinR + 4) * 0.45);
  ctx.closePath();
  ctx.fill();

  ctx.fillStyle = green;
  ctx.beginPath();
  ctx.arc(cx, pinCy, pinR, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.moveTo(cx - pinR * 0.85, pinCy + pinR * 0.44);
  ctx.lineTo(cx, baseLineY - 6);
  ctx.lineTo(cx + pinR * 0.85, pinCy + pinR * 0.44);
  ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.arc(cx, pinCy, 15, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = green;
  ctx.beginPath();
  ctx.arc(cx, pinCy, 7.5, 0, Math.PI * 2);
  ctx.fill();

  ctx.restore();
}

/**
 * Reference Image 2 Left Pane Illustration: Lush green meadow/field with striped bright green grass,
 * white wooden perspective fences on left and right converging to horizon, distant green trees, and soft cloudy sky.
 */
function drawLandPastureScene(
  ctx: CanvasRenderingContext2D,
  box: { left: number; top: number; width: number; height: number },
  darkGreen: string
) {
  const { left, top, width, height } = box;
  const horizonY = top + height * 0.37;

  ctx.save();
  ctx.beginPath();
  ctx.roundRect(left, top, width, height, 10);
  ctx.clip();

  // 1. Soft pale blue-white cloudy sky
  const skyGrad = ctx.createLinearGradient(0, top, 0, horizonY);
  skyGrad.addColorStop(0, '#D7E8F7');
  skyGrad.addColorStop(1, '#F5F9FD');
  ctx.fillStyle = skyGrad;
  ctx.fillRect(left, top, width, horizonY - top + 6);

  // Soft white clouds
  ctx.fillStyle = 'rgba(255, 255, 255, 0.82)';
  for (const [cxOff, cyOff, r] of [
    [0.14, 0.22, 28],
    [0.2, 0.25, 22],
    [0.48, 0.16, 30],
    [0.56, 0.2, 24],
    [0.72, 0.18, 26],
    [0.86, 0.23, 24],
  ]) {
    ctx.beginPath();
    ctx.arc(left + width * cxOff, top + height * cyOff, r, 0, Math.PI * 2);
    ctx.fill();
  }

  // 2. Distant rolling treeline silhouettes & foreground horizon trees
  ctx.fillStyle = '#2F8F3B';
  ctx.beginPath();
  ctx.moveTo(left, horizonY + 4);
  for (let i = 0; i <= 14; i++) {
    const px = left + (width * i) / 14;
    const py = horizonY - 12 - (i % 3) * 8 - (i % 2) * 5;
    ctx.lineTo(px, py);
  }
  ctx.lineTo(left + width, horizonY + 4);
  ctx.closePath();
  ctx.fill();

  const drawSceneryTree = (tx: number, baseY: number, scale: number) => {
    const fillCircles = (color: string, circles: [number, number, number][]) => {
      ctx.fillStyle = color;
      for (const [cx, cy, r] of circles) {
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.fill();
      }
    };
    ctx.save();
    ctx.translate(tx, baseY);
    ctx.scale(scale, scale);
    // Trunk
    ctx.fillStyle = '#27591E';
    ctx.fillRect(-6, -34, 12, 36);
    // Deep shadow canopy
    fillCircles('#0D5E1D', [
      [0, -64, 34],
      [-26, -48, 24],
      [26, -48, 24],
    ]);
    // Sunlit green clusters
    fillCircles('#229932', [
      [-8, -72, 24],
      [16, -64, 20],
      [-22, -52, 17],
      [20, -48, 16],
    ]);
    fillCircles('#5FD148', [
      [-10, -78, 13],
      [10, -70, 10],
    ]);
    ctx.restore();
  };

  drawSceneryTree(left + width * 0.28, horizonY + 2, 1.02);
  drawSceneryTree(left + width * 0.08, horizonY + 3, 0.58);
  drawSceneryTree(left + width * 0.72, horizonY + 1, 0.42);
  drawSceneryTree(left + width * 0.91, horizonY + 2, 0.74);

  // Foreground low bushes along horizon
  ctx.fillStyle = '#187526';
  for (let i = 0; i < 16; i++) {
    const bx = left + (width * (i + 0.5)) / 16;
    ctx.beginPath();
    ctx.arc(bx, horizonY + 2, 12 + (i % 3) * 4, 0, Math.PI * 2);
    ctx.fill();
  }

  // 3. Vibrant green grass meadow with alternating mowed bands
  const grassGrad = ctx.createLinearGradient(0, horizonY, 0, top + height);
  grassGrad.addColorStop(0, '#5CD43C');
  grassGrad.addColorStop(0.55, '#46C12D');
  grassGrad.addColorStop(1, '#2B9D1E');
  ctx.fillStyle = grassGrad;
  ctx.fillRect(left, horizonY, width, top + height - horizonY);

  // Perspective mowed grass stripes
  const fieldH = top + height - horizonY;
  const stripeFractions = [0.06, 0.14, 0.24, 0.36, 0.5, 0.67, 0.86];
  stripeFractions.forEach((f, idx) => {
    const y1 = horizonY + fieldH * f;
    const y2 = horizonY + fieldH * (stripeFractions[idx + 1] ?? 1.0);
    ctx.fillStyle = idx % 2 === 0 ? 'rgba(130, 235, 84, 0.38)' : 'rgba(20, 128, 22, 0.20)';
    ctx.fillRect(left, y1, width, y2 - y1);
  });

  // Subtle vertical grass furrows converging to vanishing center
  const vanishX = left + width * 0.5;
  ctx.strokeStyle = 'rgba(25, 130, 22, 0.22)';
  ctx.lineWidth = 2;
  for (let k = -4; k <= 4; k++) {
    ctx.beginPath();
    ctx.moveTo(vanishX + k * 14, horizonY);
    ctx.lineTo(vanishX + k * 56, top + height);
    ctx.stroke();
  }

  // 4. White Wooden Perspective Fences (Left side & Right side converging to horizon)
  const drawPerspectiveFence = (isLeft: boolean) => {
    const startX = isLeft ? left - 8 : left + width + 8;
    const endX = isLeft ? left + width * 0.42 : left + width * 0.58;
    const startBottomY = top + height * 0.86;
    const endBottomY = horizonY + 6;
    const startH = height * 0.32;
    const endH = height * 0.06;

    // Subtle ground shadow under fence
    ctx.strokeStyle = 'rgba(8, 70, 18, 0.28)';
    ctx.lineWidth = 6;
    ctx.beginPath();
    ctx.moveTo(startX, startBottomY + 2);
    ctx.lineTo(endX, endBottomY + 1);
    ctx.stroke();

    // Two horizontal wooden rails
    for (const hRatio of [0.38, 0.78]) {
      ctx.strokeStyle = '#FFFFFF';
      ctx.lineWidth = 5;
      ctx.beginPath();
      ctx.moveTo(startX, startBottomY - startH * hRatio);
      ctx.lineTo(endX, endBottomY - endH * hRatio);
      ctx.stroke();

      ctx.strokeStyle = 'rgba(16, 95, 32, 0.25)';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(startX, startBottomY - startH * hRatio + 3);
      ctx.lineTo(endX, endBottomY - endH * hRatio + 1.5);
      ctx.stroke();
    }

    // Fence posts from far to near
    const postTs = [0.95, 0.84, 0.7, 0.53, 0.34, 0.14];
    for (const t of postTs) {
      const px = startX + (endX - startX) * t;
      const pyBottom = startBottomY + (endBottomY - startBottomY) * t;
      const postH = startH + (endH - startH) * t;
      const postW = Math.max(3, 10 * (1 - t * 0.7));
      ctx.fillStyle = '#FFFFFF';
      ctx.fillRect(px - postW / 2, pyBottom - postH, postW, postH);
      ctx.strokeStyle = 'rgba(16, 95, 32, 0.28)';
      ctx.lineWidth = 1;
      ctx.strokeRect(px - postW / 2, pyBottom - postH, postW, postH);
    }
  };

  drawPerspectiveFence(true);
  drawPerspectiveFence(false);

  // Subtle green crisp inner outline
  ctx.strokeStyle = darkGreen;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.roundRect(left + 1, top + 1, width - 2, height - 2, 9);
  ctx.stroke();

  ctx.restore();
}

export function renderDynamicPoster(record: PropertyRecord): RenderedPosterOutput {
  const W = 1440;
  const isHouse = record.category === 'HOUSE_FOR_RENT';

  // Offscreen measurement canvas first so we calculate exact dynamic height BEFORE drawing
  const measureCanvas = document.createElement('canvas');
  measureCanvas.width = W;
  measureCanvas.height = 100;
  const mctx = measureCanvas.getContext('2d');
  if (!mctx) {
    return { dataUrl: '', width: W, height: 1080, overflowed: false };
  }

  const cardLeft = 48;
  const cardRight = W - 48;
  const cardW = cardRight - cardLeft;
  const visibleFields = getPosterVisibleFieldValues(record);
  const titleText = (record.title || getDefaultTitle(record.category)).trim();

  // Measure Title Banner (wraps gracefully if custom title is long)
  const titleMaxW = cardW - 110;
  let titleFontSize = isHouse ? 86 : 88;
  let titleLines = wrapPlainTitleLines(mctx, titleText, titleMaxW, titleFontSize);
  while (titleLines.length > 2 && titleFontSize > 52) {
    titleFontSize -= 2;
    titleLines = wrapPlainTitleLines(mctx, titleText, titleMaxW, titleFontSize);
  }
  const titleLineH = titleFontSize * 1.18;
  const titleTextBlockH = titleLines.length * titleLineH;
  const titleH = Math.max(158, Math.round(titleTextBlockH + 60));

  const contactH = 152;
  const contactStr = isHouse
    ? `যোগাযোগ: ${record.mobile.trim()}`
    : `যোগাযোগঃ ${record.mobile.trim()}`;

  if (isHouse) {
    const NAVY = '#002868';
    const headerTop = 36;
    const titleTop = headerTop + 144;
    const titleBottom = titleTop + titleH;
    const panelTop = titleBottom + 24;

    const illustPaneW = 316;
    const dividerX = cardLeft + illustPaneW;
    const textLeft = dividerX + 34;
    const textRight = cardRight - 34;
    const textMaxW = textRight - textLeft;

    // Auto-size typography: expand panel height first; only gently shrink font for massive walls of text
    let fontSize = visibleFields.length <= 2 ? 48 : 44;
    if (visibleFields.reduce((acc, f) => acc + f.plainText.length, 0) > 500) {
      fontSize = 38;
    }
    const lineH = fontSize * 1.28;
    const fieldGap = 20;

    const measuredBlocks = visibleFields.map((fv) => {
      const runs = buildFieldInlineRuns(fv);
      const lines = wrapRichRuns(mctx, runs, textMaxW, fontSize, false);
      return { fv, lines, height: lines.length * lineH };
    });

    const textTotalH =
      measuredBlocks.reduce((sum, b) => sum + b.height, 0) +
      Math.max(0, measuredBlocks.length - 1) * fieldGap;

    const hasFields = measuredBlocks.length > 0;
    const minPanelH = hasFields ? 316 : 256;
    const padV = 34;
    const panelH = Math.max(minPanelH, Math.round(textTotalH + padV * 2));
    const panelBottom = panelTop + panelH;

    const contactTop = panelBottom + 24;
    const contactBottom = contactTop + contactH;
    const totalHeight = Math.round(contactBottom + 46);

    const canvas = document.createElement('canvas');
    canvas.width = W;
    canvas.height = totalHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) return { dataUrl: '', width: W, height: totalHeight, overflowed: false };

    // 1. Crisp White Canvas & Navy Outer Rounded Border
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, W, totalHeight);

    ctx.strokeStyle = NAVY;
    ctx.lineWidth = 7;
    ctx.beginPath();
    ctx.roundRect(22, 22, W - 44, totalHeight - 44, 46);
    ctx.stroke();

    // 2. Top Roof Silhouette Header
    drawHouseTopRoofHeader(ctx, W, headerTop, NAVY);

    // 3. Navy Title Banner
    ctx.fillStyle = NAVY;
    ctx.beginPath();
    ctx.roundRect(cardLeft, titleTop, cardW, titleH, 46);
    ctx.fill();

    ctx.font = buildTitleFont(titleFontSize);
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    const titleStartY = titleTop + titleH / 2 - ((titleLines.length - 1) * titleLineH) / 2;
    titleLines.forEach((line, idx) => {
      ctx.fillText(line, W / 2, titleStartY + idx * titleLineH);
    });

    // 4. Single Large Central Rounded Panel
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(cardLeft, panelTop, cardW, panelH, 38);
    ctx.fill();
    ctx.strokeStyle = NAVY;
    ctx.lineWidth = 5.5;
    ctx.stroke();

    if (hasFields) {
      // Left House Illustration Pane (centered vertically, aspect-preserved)
      drawHousePanelIllustration(
        ctx,
        cardLeft + illustPaneW / 2,
        panelTop + panelH / 2,
        illustPaneW - 24,
        Math.min(280, panelH - 40),
        NAVY
      );

      // Vertical Separator Line
      ctx.strokeStyle = NAVY;
      ctx.lineWidth = 4.5;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(dividerX, panelTop + 26);
      ctx.lineTo(dividerX, panelBottom - 26);
      ctx.stroke();

      // Right Text Pane (No internal cards — crisp continuous layout)
      ctx.textAlign = 'left';
      ctx.textBaseline = 'alphabetic';
      const innerAvailH = panelH - padV * 2;
      let cursorY = panelTop + padV + Math.max(0, (innerAvailH - textTotalH) / 2);

      for (const block of measuredBlocks) {
        for (const line of block.lines) {
          const baselineY = cursorY + fontSize * 0.88;
          drawRichLine(ctx, line, textLeft, baselineY, fontSize, NAVY, NAVY);
          cursorY += lineH;
        }
        cursorY += fieldGap;
      }
    } else {
      // All optional body fields empty -> centered compact house illustration cleanly inside panel
      drawHousePanelIllustration(
        ctx,
        W / 2,
        panelTop + panelH / 2,
        cardW - 80,
        panelH - 36,
        NAVY
      );
    }

    // 5. Bottom Navy Contact Banner
    ctx.fillStyle = NAVY;
    ctx.beginPath();
    ctx.roundRect(cardLeft, contactTop, cardW, contactH, 44);
    ctx.fill();

    const contactMidY = contactTop + contactH / 2;
    const phoneR = 52;
    const phoneCx = cardLeft + 104;
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(phoneCx, contactMidY, phoneR, 0, Math.PI * 2);
    ctx.fill();

    ctx.save();
    ctx.translate(phoneCx, contactMidY);
    ctx.scale(phoneR / 62, phoneR / 62);
    ctx.fillStyle = NAVY;
    drawHandset(ctx);
    ctx.restore();

    const cDivX = cardLeft + 194;
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 4.5;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(cDivX, contactTop + 26);
    ctx.lineTo(cDivX, contactBottom - 26);
    ctx.stroke();

    const contactRightMaxW = cardRight - 36 - (cDivX + 28);
    let contactFontSize = 78;
    while (contactFontSize > 42) {
      ctx.font = buildTitleFont(contactFontSize);
      if (ctx.measureText(contactStr).width <= contactRightMaxW) break;
      contactFontSize -= 2;
    }
    ctx.font = buildTitleFont(contactFontSize);
    ctx.fillStyle = '#FFFFFF';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(contactStr, (cDivX + 28 + cardRight - 36) / 2, contactMidY);

    return {
      dataUrl: canvas.toDataURL('image/jpeg', 0.92),
      width: W,
      height: totalHeight,
      overflowed: false,
    };
  }

  // =========================================================================
  // LAND FOR SALE — REFERENCE IMAGE 2 EXACT DESIGN + DYNAMIC HEIGHT
  // =========================================================================
  const GREEN = '#05661D';
  const DARK_GREEN_TEXT = '#045718';
  const headerTop = 34;
  const titleTop = headerTop + 128;
  const titleBottom = titleTop + titleH;
  const panelTop = titleBottom + 20;

  // Separate Dag, Khatian, and Additional Info per Reference Image 2 structure
  // Any future fields added to LAND_FIELDS automatically flow into mainPanelFields!
  const dagField = visibleFields.find((f) => f.fieldId === 'dag_number');
  const khatianField = visibleFields.find((f) => f.fieldId === 'khatian_number');
  const additionalField = visibleFields.find((f) => f.fieldId === 'additional_info');
  const mainPanelFields = visibleFields.filter(
    (f) =>
      f.fieldId !== 'dag_number' &&
      f.fieldId !== 'khatian_number' &&
      f.fieldId !== 'additional_info'
  );

  // Measure Main Central Panel
  const illustPad = 18;
  const illustW = 368;
  const illustH = 258;
  const dividerX = cardLeft + illustPad + illustW + 18; // ~452
  const textLeft = dividerX + 28;
  const textRight = cardRight - 28;
  const textMaxW = textRight - textLeft;

  const totalMainChars = mainPanelFields.reduce((s, f) => s + f.plainText.length, 0);
  let mainFontSize = mainPanelFields.length <= 4 && totalMainChars <= 180 ? 45 : 41;
  if (totalMainChars > 460) mainFontSize = 36;
  const mainLineH = mainFontSize * 1.24;
  const mainFieldGap = 12;

  const measuredMainBlocks = mainPanelFields.map((fv) => {
    const runs = buildFieldInlineRuns(fv);
    const lines = wrapRichRuns(mctx, runs, textMaxW, mainFontSize, false);
    return { fv, lines, height: lines.length * mainLineH };
  });

  const mainTextTotalH =
    measuredMainBlocks.reduce((s, b) => s + b.height, 0) +
    Math.max(0, measuredMainBlocks.length - 1) * mainFieldGap;

  const hasMainFields = measuredMainBlocks.length > 0;
  const panelPadV = 22;
  const panelH = Math.max(illustH + panelPadV * 2, Math.round(mainTextTotalH + panelPadV * 2));
  const panelBottom = panelTop + panelH;

  let cursorBottomY = panelBottom;

  // Measure Separate Dag & Khatian Pill Row (omitted completely if both empty)
  const hasDag = Boolean(dagField && dagField.plainText.trim());
  const hasKhatian = Boolean(khatianField && khatianField.plainText.trim());
  const hasDagKhatianRow = hasDag || hasKhatian;

  let dagRowTop = 0;
  let dagRowH = 0;
  let dagLines: RichRun[][] = [];
  let khatianLines: RichRun[][] = [];
  const dagFontSize = 44;
  const dagLineH = dagFontSize * 1.22;

  if (hasDagKhatianRow) {
    dagRowTop = cursorBottomY + 18;
    const singleSideMaxW = hasDag && hasKhatian ? cardW / 2 - 44 : cardW - 72;
    if (hasDag && dagField) {
      dagLines = wrapRichRuns(mctx, buildFieldInlineRuns(dagField), singleSideMaxW, dagFontSize, false);
    }
    if (hasKhatian && khatianField) {
      khatianLines = wrapRichRuns(
        mctx,
        buildFieldInlineRuns(khatianField),
        singleSideMaxW,
        dagFontSize,
        false
      );
    }
    const maxLines = Math.max(dagLines.length, khatianLines.length, 1);
    dagRowH = Math.max(76, Math.round(maxLines * dagLineH + 22));
    cursorBottomY = dagRowTop + dagRowH;
  }

  // Measure Additional Information Row below Dag/Khatian (omitted completely if empty)
  const hasAdditional = Boolean(additionalField && additionalField.plainText.trim());
  let addTop = 0;
  let addLines: RichRun[][] = [];
  let addFontSize = 40;
  const addMaxW = cardW - 44;
  if (hasAdditional && additionalField) {
    addTop = cursorBottomY + 16;
    if (additionalField.plainText.length > 260) {
      addFontSize = 36;
    }
    addLines = wrapRichRuns(
      mctx,
      buildFieldInlineRuns(additionalField),
      addMaxW,
      addFontSize,
      false
    );
    const addLineH = addFontSize * 1.24;
    cursorBottomY = addTop + Math.round(addLines.length * addLineH);
  }

  const contactTop = cursorBottomY + 18;
  const contactBottom = contactTop + contactH;
  const totalHeight = Math.round(contactBottom + 44);

  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = totalHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) return { dataUrl: '', width: W, height: totalHeight, overflowed: false };

  // 1. Crisp White Canvas & Green Rounded Outer Border
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, W, totalHeight);

  ctx.strokeStyle = GREEN;
  ctx.lineWidth = 7;
  ctx.beginPath();
  ctx.roundRect(22, 22, W - 44, totalHeight - 44, 42);
  ctx.stroke();

  // 2. Top Land/Location Header Emblem
  drawLandTopEmblemHeader(ctx, W, headerTop, GREEN);

  // 3. Dark Green Title Banner
  ctx.fillStyle = GREEN;
  ctx.beginPath();
  ctx.roundRect(cardLeft, titleTop, cardW, titleH, 44);
  ctx.fill();

  ctx.font = buildTitleFont(titleFontSize);
  ctx.fillStyle = '#FFFFFF';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const titleStartY = titleTop + titleH / 2 - ((titleLines.length - 1) * titleLineH) / 2;
  titleLines.forEach((line, idx) => {
    ctx.fillText(line, W / 2, titleStartY + idx * titleLineH);
  });

  // 4. Main Central Green-Bordered Panel
  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.roundRect(cardLeft, panelTop, cardW, panelH, 34);
  ctx.fill();
  ctx.strokeStyle = GREEN;
  ctx.lineWidth = 5;
  ctx.stroke();

  if (hasMainFields) {
    // Left Pasture Illustration (aspect-preserved, vertically centered)
    const illustTop = panelTop + (panelH - illustH) / 2;
    drawLandPastureScene(
      ctx,
      { left: cardLeft + illustPad, top: illustTop, width: illustW, height: illustH },
      GREEN
    );

    // Vertical Green Separator Line
    ctx.strokeStyle = GREEN;
    ctx.lineWidth = 4.5;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(dividerX, panelTop + 20);
    ctx.lineTo(dividerX, panelBottom - 20);
    ctx.stroke();

    // Right Pane Dynamic Fields (No internal cards)
    ctx.textAlign = 'left';
    ctx.textBaseline = 'alphabetic';
    const innerAvailH = panelH - panelPadV * 2;
    let curY = panelTop + panelPadV + Math.max(0, (innerAvailH - mainTextTotalH) / 2);

    for (const block of measuredMainBlocks) {
      for (const line of block.lines) {
        const baselineY = curY + mainFontSize * 0.88;
        drawRichLine(ctx, line, textLeft, baselineY, mainFontSize, DARK_GREEN_TEXT, DARK_GREEN_TEXT);
        curY += mainLineH;
      }
      curY += mainFieldGap;
    }
  } else {
    // Edge case: only Dag/Khatian or Additional info filled, or everything optional empty
    const illustTop = panelTop + (panelH - illustH) / 2;
    drawLandPastureScene(
      ctx,
      { left: W / 2 - illustW / 2, top: illustTop, width: illustW, height: illustH },
      GREEN
    );
  }

  // 5. Separate Rounded Green-Bordered Dag & Khatian Row
  if (hasDagKhatianRow) {
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(cardLeft, dagRowTop, cardW, dagRowH, 28);
    ctx.fill();
    ctx.strokeStyle = GREEN;
    ctx.lineWidth = 4.5;
    ctx.stroke();

    ctx.textAlign = 'left';
    ctx.textBaseline = 'alphabetic';

    if (hasDag && hasKhatian) {
      // Vertical '|' divider in middle
      ctx.strokeStyle = GREEN;
      ctx.lineWidth = 4;
      ctx.beginPath();
      ctx.moveTo(W / 2, dagRowTop + 14);
      ctx.lineTo(W / 2, dagRowTop + dagRowH - 14);
      ctx.stroke();

      const drawCenteredSlot = (slotLines: RichRun[][], slotLeft: number, slotW: number) => {
        const blockH = slotLines.length * dagLineH;
        let y = dagRowTop + (dagRowH - blockH) / 2;
        for (const line of slotLines) {
          const lineW = measureRichLineWidth(ctx, line, dagFontSize);
          const x = slotLeft + Math.max(12, (slotW - lineW) / 2);
          drawRichLine(
            ctx,
            line,
            x,
            y + dagFontSize * 0.85,
            dagFontSize,
            DARK_GREEN_TEXT,
            DARK_GREEN_TEXT
          );
          y += dagLineH;
        }
      };

      drawCenteredSlot(dagLines, cardLeft + 16, W / 2 - cardLeft - 24);
      drawCenteredSlot(khatianLines, W / 2 + 8, cardRight - W / 2 - 24);
    } else {
      const onlyLines = hasDag ? dagLines : khatianLines;
      const blockH = onlyLines.length * dagLineH;
      let y = dagRowTop + (dagRowH - blockH) / 2;
      for (const line of onlyLines) {
        const lineW = measureRichLineWidth(ctx, line, dagFontSize);
        const x = cardLeft + Math.max(24, (cardW - lineW) / 2);
        drawRichLine(
          ctx,
          line,
          x,
          y + dagFontSize * 0.85,
          dagFontSize,
          DARK_GREEN_TEXT,
          DARK_GREEN_TEXT
        );
        y += dagLineH;
      }
    }
  }

  // 6. Additional Info Section (centered when short, wrapped cleanly when long)
  if (hasAdditional && addLines.length > 0) {
    const addLineH = addFontSize * 1.24;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'alphabetic';
    let y = addTop;
    for (const line of addLines) {
      const lineW = measureRichLineWidth(ctx, line, addFontSize);
      const x =
        addLines.length <= 2
          ? W / 2 - lineW / 2
          : cardLeft + 22;
      drawRichLine(
        ctx,
        line,
        x,
        y + addFontSize * 0.88,
        addFontSize,
        DARK_GREEN_TEXT,
        DARK_GREEN_TEXT
      );
      y += addLineH;
    }
  }

  // 7. Bottom Dark Green Contact Banner
  ctx.fillStyle = GREEN;
  ctx.beginPath();
  ctx.roundRect(cardLeft, contactTop, cardW, contactH, 44);
  ctx.fill();

  const contactMidY = contactTop + contactH / 2;
  const phoneR = 52;
  const phoneCx = cardLeft + 104;
  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.arc(phoneCx, contactMidY, phoneR, 0, Math.PI * 2);
  ctx.fill();

  ctx.save();
  ctx.translate(phoneCx, contactMidY);
  ctx.scale(phoneR / 62, phoneR / 62);
  ctx.fillStyle = GREEN;
  drawHandset(ctx);
  ctx.restore();

  const cDivX = cardLeft + 194;
  ctx.strokeStyle = '#FFFFFF';
  ctx.lineWidth = 4.5;
  ctx.lineCap = 'round';
  ctx.beginPath();
  ctx.moveTo(cDivX, contactTop + 26);
  ctx.lineTo(cDivX, contactBottom - 26);
  ctx.stroke();

  const contactRightMaxW = cardRight - 36 - (cDivX + 28);
  let contactFontSize = 78;
  while (contactFontSize > 42) {
    ctx.font = buildTitleFont(contactFontSize);
    if (ctx.measureText(contactStr).width <= contactRightMaxW) break;
    contactFontSize -= 2;
  }
  ctx.font = buildTitleFont(contactFontSize);
  ctx.fillStyle = '#FFFFFF';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(contactStr, (cDivX + 28 + cardRight - 36) / 2, contactMidY);

  return {
    dataUrl: canvas.toDataURL('image/jpeg', 0.92),
    width: W,
    height: totalHeight,
    overflowed: false,
  };
}

export function renderPortraitPosterDataUrl(record: PropertyRecord): string {
  return renderDynamicPoster(record).dataUrl;
}
