


const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'creat-fonts');
const SIZE = 128;
const F = 4;

const PIXEL_FONT = {
  G: ['01110', '10001', '10000', '10111', '10001', '10001', '01110'],
  L: ['10000', '10000', '10000', '10000', '10000', '10000', '11111'],
  Y: ['10001', '10001', '01010', '00100', '00100', '00100', '00100'],
  P: ['11110', '10001', '10001', '11110', '10000', '10000', '10000'],
  H: ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
  F: ['11111', '10000', '10000', '11110', '10000', '10000', '10000'],
  O: ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
  R: ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
  E: ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
};

const GLYPH_ADVANCE = 6;
const CREAM_TOP = [0xff, 0xf4, 0xde];
const CREAM_BOTTOM = [0xd9, 0xc6, 0xa3];
const GOLD_TOP = [0xf5, 0xc9, 0x68];
const GOLD_BOTTOM = [0xc6, 0x8a, 0x2c];

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([len, body, crc]);
}

function writePng(file, width, height, pixels) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    pixels.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
  fs.writeFileSync(file, png);
}

function makeCanvas(w, h) {
  return { w, h, data: Buffer.alloc(w * h * 4) };
}

function put(c, x, y, r, g, b, a) {
  if (x < 0 || y < 0 || x >= c.w || y >= c.h) return;
  const i = (y * c.w + x) * 4;
  const sa = a / 255;
  const da = c.data[i + 3] / 255;
  const oa = sa + da * (1 - sa);
  if (oa === 0) return;
  c.data[i] = Math.round((r * sa + c.data[i] * da * (1 - sa)) / oa);
  c.data[i + 1] = Math.round((g * sa + c.data[i + 1] * da * (1 - sa)) / oa);
  c.data[i + 2] = Math.round((b * sa + c.data[i + 2] * da * (1 - sa)) / oa);
  c.data[i + 3] = Math.round(oa * 255);
}

function fillRect(c, x0, y0, x1, y1, color) {
  const left = Math.round(x0);
  const top = Math.round(y0);
  const right = Math.round(x1);
  const bottom = Math.round(y1);
  for (let y = top; y < bottom; y++) {
    for (let x = left; x < right; x++) put(c, x, y, color[0], color[1], color[2], color[3]);
  }
}

function roundedMask(x, y, w, h, r) {
  if (x < 0 || y < 0 || x >= w || y >= h) return false;
  const cx = x < r ? r : x >= w - r ? w - r - 1 : x;
  const cy = y < r ? r : y >= h - r ? h - r - 1 : y;
  const dx = x - cx;
  const dy = y - cy;
  return dx * dx + dy * dy <= r * r;
}

function downsample(src, factor) {
  const w = src.w / factor;
  const h = src.h / factor;
  const dst = makeCanvas(w, h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      let r = 0, g = 0, b = 0, a = 0;
      for (let sy = 0; sy < factor; sy++) {
        for (let sx = 0; sx < factor; sx++) {
          const i = ((y * factor + sy) * src.w + x * factor + sx) * 4;
          const pa = src.data[i + 3] / 255;
          r += src.data[i] * pa;
          g += src.data[i + 1] * pa;
          b += src.data[i + 2] * pa;
          a += pa;
        }
      }
      const n = factor * factor;
      const o = (y * w + x) * 4;
      dst.data[o + 3] = Math.round((a / n) * 255);
      if (a > 0) {
        dst.data[o] = Math.round(r / a);
        dst.data[o + 1] = Math.round(g / a);
        dst.data[o + 2] = Math.round(b / a);
      }
    }
  }
  return dst;
}

function mix(a, b, t) {
  return [
    Math.round(a[0] + (b[0] - a[0]) * t),
    Math.round(a[1] + (b[1] - a[1]) * t),
    Math.round(a[2] + (b[2] - a[2]) * t),
  ];
}

function wordWidth(word, unit) {
  return (word.length * 5 + (word.length - 1)) * unit;
}


function drawWord(c, word, unit, x, y, topColor, bottomColor, shadowAlpha) {
  const cell = unit * F;
  const gap = cell;
  const letters = [...word];

  const draw = (offsetX, offsetY, alphaScale, useColor) => {
    letters.forEach((letter, index) => {
      const rows = PIXEL_FONT[letter];
      for (let row = 0; row < 7; row++) {
        for (let col = 0; col < 5; col++) {
          if (rows[row][col] !== '1') continue;
          const px = (x + index * (5 * unit + unit) + col * unit) * F + offsetX * F;
          const py = (y + row * unit) * F + offsetY * F;
          if (useColor) {
            const color = mix(topColor, bottomColor, row / 6);
            fillRect(c, px, py, px + cell, py + cell, [color[0], color[1], color[2], Math.round(255 * alphaScale)]);
          } else {
            fillRect(c, px, py, px + cell, py + cell, [10, 6, 3, Math.round(shadowAlpha * 255)]);
          }
        }
      }
    });
  };

  draw(1.2, 2.0, 1, false);
  draw(0, 0, 1, true);
}

function buildIconPng() {
  const canvas = makeCanvas(SIZE * F, SIZE * F);


  for (let y = 0; y < canvas.h; y++) {
    for (let x = 0; x < canvas.w; x++) {
      const px = x / F;
      const py = y / F;
      if (roundedMask(px - 2, py - 3, SIZE, SIZE, 24)
          && !roundedMask(px, py, SIZE, SIZE, 24)) {
        put(canvas, x, y, 0, 0, 0, 42);
      }
    }
  }


  for (let y = 0; y < canvas.h; y++) {
    for (let x = 0; x < canvas.w; x++) {
      const px = x / F;
      const py = y / F;
      if (!roundedMask(px, py, SIZE, SIZE, 24)) continue;
      const t = Math.max(0, Math.min(1, (py - 8) / 112));
      if (!roundedMask(px - 2, py - 2, SIZE, SIZE, 24)
          || !roundedMask(px + 2, py + 2, SIZE, SIZE, 24)) {
        put(canvas, x, y, 0x5a, 0x41, 0x28, 255);
      } else {
        put(
          canvas, x, y,
          Math.round(0x3a + (0x17 - 0x3a) * t),
          Math.round(0x2a + (0x10 - 0x2a) * t),
          Math.round(0x1c + (0x09 - 0x1c) * t),
          255
        );
      }
    }
  }


  fillRect(canvas, 14 * F, 10 * F, 114 * F, 11 * F, [0xf1, 0xe7, 0xd0, 26]);

  const corner = [0x8a, 0x64, 0x20, 110];
  fillRect(canvas, 15 * F, 15 * F, 18 * F, 18 * F, corner);
  fillRect(canvas, 110 * F, 15 * F, 113 * F, 18 * F, corner);
  fillRect(canvas, 15 * F, 110 * F, 18 * F, 113 * F, corner);
  fillRect(canvas, 110 * F, 110 * F, 113 * F, 113 * F, corner);


  const micro = 4;
  const microGap = 3;
  const microTotal = 5 * micro + 4 * microGap;
  let mx = (SIZE - microTotal) / 2;
  for (let i = 0; i < 5; i++) {
    const shade = i % 2 === 0 ? [0xe0, 0xa9, 0x3b, 150] : [0x8a, 0x64, 0x20, 130];
    fillRect(canvas, mx * F, 14 * F, (mx + micro) * F, 18 * F, shade);
    mx += micro + microGap;
  }

  drawWord(canvas, 'GLYPH', 3.5, (SIZE - wordWidth('GLYPH', 3.5)) / 2, 29, CREAM_TOP, CREAM_BOTTOM, 0.62);
  drawWord(canvas, 'FORGE', 2.5, (SIZE - wordWidth('FORGE', 2.5)) / 2, 63, GOLD_TOP, GOLD_BOTTOM, 0.62);


  const block = 7;
  const blockGap = 5;
  const blockTotal = 5 * block + 4 * blockGap;
  let bx = (SIZE - blockTotal) / 2;
  for (let i = 0; i < 5; i++) {
    const color = i === 2 ? [0xff, 0xf4, 0xde, 255] : i % 2 === 0 ? [0xe0, 0xa9, 0x3b, 255] : [0xb8, 0x83, 0x30, 255];
    fillRect(canvas, bx * F, 91 * F, (bx + block) * F, 98 * F, [0, 0, 0, 70]);
    fillRect(canvas, bx * F, (91 - 1) * F, (bx + block) * F, 98 * F, color);
    bx += block + blockGap;
  }

  return downsample(canvas, F);
}

function svgWord(word, unit, x, y, topColor, bottomColor) {
  const letters = [...word];
  const rects = [];
  letters.forEach((letter, index) => {
    const rows = PIXEL_FONT[letter];
    for (let row = 0; row < 7; row++) {
      for (let col = 0; col < 5; col++) {
        if (rows[row][col] !== '1') continue;
        const px = x + index * 6 * unit + col * unit;
        const py = y + row * unit;
        const color = mix(topColor, bottomColor, row / 6);
        const hex = '#' + color.map((v) => v.toString(16).padStart(2, '0')).join('');
        rects.push(`    <rect x="${px.toFixed(2)}" y="${py.toFixed(2)}" width="${unit}" height="${unit}" fill="${hex}"/>`);
      }
    }
  });
  return rects.join('\n');
}

function buildSvg() {
  const micro = 4;
  const microGap = 3;
  const microTotal = 5 * micro + 4 * microGap;
  let mx = (SIZE - microTotal) / 2;
  const microRects = [];
  for (let i = 0; i < 5; i++) {
    microRects.push(`  <rect x="${mx}" y="14" width="${micro}" height="4" fill="${i % 2 === 0 ? '#e0a93b' : '#8a6420'}" opacity="${i % 2 === 0 ? 0.58 : 0.5}"/>`);
    mx += micro + microGap;
  }

  const block = 7;
  const blockGap = 5;
  const blockTotal = 5 * block + 4 * blockGap;
  let bx = (SIZE - blockTotal) / 2;
  const blockRects = [];
  for (let i = 0; i < 5; i++) {
    const color = i === 2 ? '#fff4de' : i % 2 === 0 ? '#e0a93b' : '#b88330';
    blockRects.push(`  <rect x="${bx}" y="90" width="${block}" height="${block}" fill="${color}"/>`);
    bx += block + blockGap;
  }

  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128" role="img" aria-labelledby="title desc">
  <title id="title">GLYPH FORGE</title>
  <desc id="desc">Glyph Forge: the English wordmark for 造字, built from miniature blocks.</desc>
  <defs>
    <linearGradient id="panel" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#3a2a1c"/>
      <stop offset="1" stop-color="#171009"/>
    </linearGradient>
  </defs>
  <rect x="6" y="7" width="116" height="116" rx="26" fill="#000000" opacity="0.28"/>
  <rect x="8" y="8" width="112" height="112" rx="24" fill="url(#panel)" stroke="#5a4128" stroke-width="2"/>
  <rect x="14" y="10" width="100" height="1" fill="#f1e7d0" opacity="0.12"/>
  <rect x="15" y="15" width="3" height="3" fill="#8a6420" opacity="0.45"/>
  <rect x="110" y="15" width="3" height="3" fill="#8a6420" opacity="0.45"/>
  <rect x="15" y="110" width="3" height="3" fill="#8a6420" opacity="0.45"/>
  <rect x="110" y="110" width="3" height="3" fill="#8a6420" opacity="0.45"/>
${microRects.join('\n')}
  <g shape-rendering="crispEdges">
${svgWord('GLYPH', 3.5, (SIZE - wordWidth('GLYPH', 3.5)) / 2, 29, CREAM_TOP, CREAM_BOTTOM)}
${svgWord('FORGE', 2.5, (SIZE - wordWidth('FORGE', 2.5)) / 2, 63, GOLD_TOP, GOLD_BOTTOM)}
${blockRects.join('\n')}
  </g>
</svg>
`;
}

fs.mkdirSync(OUT, { recursive: true });
writePng(path.join(OUT, 'icon.png'), SIZE, SIZE, buildIconPng().data);
fs.writeFileSync(path.join(OUT, 'icon.svg'), buildSvg(), 'utf8');
console.log('wrote icon.svg and icon.png to ' + OUT);
