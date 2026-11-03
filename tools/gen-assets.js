

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'creat-fonts');
const BLOCK_OUT = path.join(OUT, 'textures', 'block');

const GLYPH = [
  '.......##.......',
  '......#..#......',
  '........#.......',
  '.##############.',
  '.#............#.',
  '.#............#.',
  '......####......',
  '.....#..##......',
  '....#....#......',
  '...#######......',
  '.......#........',
  '.......#........',
  '..#....#....#...',
  '...#######......',
  '................',
  '................',
];

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
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) put(c, x, y, color[0], color[1], color[2], color[3]);
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


const block = makeCanvas(16, 16);
fillRect(block, 0, 0, 16, 16, [0x2e, 0x31, 0x38, 255]);
for (let i = 0; i < 16; i++) {
  put(block, i, 0, 0x14, 0x16, 0x1a, 255);
  put(block, i, 15, 0x14, 0x16, 0x1a, 255);
  put(block, 0, i, 0x14, 0x16, 0x1a, 255);
  put(block, 15, i, 0x14, 0x16, 0x1a, 255);
  put(block, i, 1, 0x4a, 0x4f, 0x58, 255);
  put(block, i, 14, 0x4a, 0x4f, 0x58, 255);
  put(block, 1, i, 0x4a, 0x4f, 0x58, 255);
  put(block, 14, i, 0x4a, 0x4f, 0x58, 255);
}
for (let y = 0; y < 16; y++) {
  for (let x = 0; x < 16; x++) {
    if (GLYPH[y][x] === '#') {
      put(block, x, y, (x + y) % 5 === 0 ? 0xc7 : 0xff, (x + y) % 5 === 0 ? 0x9a : 0xd1, (x + y) % 5 === 0 ? 0x3e : 0x66, 255);
    }
  }
}

fs.mkdirSync(BLOCK_OUT, { recursive: true });
writePng(path.join(BLOCK_OUT, 'font_block.png'), 16, 16, block.data);

console.log('wrote block texture to ' + BLOCK_OUT);
