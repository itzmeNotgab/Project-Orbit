import { zlibSync } from 'fflate';

export type PngInfo = { width: number; height: number };

const SIG = [137, 80, 78, 71, 13, 10, 26, 10];

export function readPngInfo(bytes: Uint8Array): PngInfo | null {
  if (bytes.length < 24) return null;
  for (let i = 0; i < 8; i++) if (bytes[i] !== SIG[i]) return null;
  const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (dv.getUint32(12) !== 0x49484452) return null; // 'IHDR'
  return { width: dv.getUint32(16), height: dv.getUint32(20) };
}

let crcTable: Uint32Array | null = null;
function crc32(buf: Uint8Array): number {
  if (!crcTable) {
    crcTable = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      crcTable[n] = c >>> 0;
    }
  }
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 255] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type: string, data: Uint8Array): Uint8Array {
  const out = new Uint8Array(12 + data.length);
  const dv = new DataView(out.buffer);
  dv.setUint32(0, data.length);
  for (let i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
  out.set(data, 8);
  dv.setUint32(8 + data.length, crc32(out.subarray(4, 8 + data.length)));
  return out;
}

/** Encodes straight RGBA pixels (width*height*4 bytes) as a PNG. */
export function encodePng(rgba: Uint8Array, width: number, height: number): Uint8Array {
  const raw = new Uint8Array((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    raw.set(rgba.subarray(y * width * 4, (y + 1) * width * 4), y * (width * 4 + 1) + 1);
  }
  const ihdr = new Uint8Array(13);
  const dv = new DataView(ihdr.buffer);
  dv.setUint32(0, width);
  dv.setUint32(4, height);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const parts = [new Uint8Array(SIG), chunk('IHDR', ihdr), chunk('IDAT', zlibSync(raw)), chunk('IEND', new Uint8Array(0))];
  const total = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(total);
  let o = 0;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}

/** 16x16 ORBIT planet-with-ring icon, generated in code (original artwork). */
export function makeOrbitIcon(size = 16): Uint8Array {
  const px = new Uint8Array(size * size * 4);
  const c = (size - 1) / 2;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4;
      const dx = x - c;
      const dy = y - c;
      const r = Math.sqrt(dx * dx + dy * dy);
      // rotated ring
      const rx = dx * Math.cos(0.45) + dy * Math.sin(0.45);
      const ry = -dx * Math.sin(0.45) + dy * Math.cos(0.45);
      const ring = Math.abs(Math.sqrt((rx * rx) / (size * 0.2) + (ry * ry) / (size * 0.022)) - 1) < 0.28 && Math.abs(rx) < size * 0.48;
      const planet = r < size * 0.27;
      if (ring && !(planet && ry < 0)) {
        px[i] = 200; px[i + 1] = 170; px[i + 2] = 255; px[i + 3] = 255;
      } else if (planet) {
        const t = Math.min(1, Math.max(0, (dx + dy) / size + 0.5));
        px[i] = Math.round(139 - 80 * t);
        px[i + 1] = Math.round(92 - 60 * t);
        px[i + 2] = Math.round(246 - 120 * t);
        px[i + 3] = 255;
      }
    }
  }
  return encodePng(px, size, size);
}
