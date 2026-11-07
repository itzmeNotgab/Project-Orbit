const CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
const LOOKUP: Record<string, number> = {};
for (let i = 0; i < CHARS.length; i++) LOOKUP[CHARS[i]] = i;

export function bytesToBase64(bytes: Uint8Array): string {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i];
    const b = i + 1 < bytes.length ? bytes[i + 1] : 0;
    const c = i + 2 < bytes.length ? bytes[i + 2] : 0;
    const n = (a << 16) | (b << 8) | c;
    out += CHARS[(n >> 18) & 63] + CHARS[(n >> 12) & 63];
    out += i + 1 < bytes.length ? CHARS[(n >> 6) & 63] : '=';
    out += i + 2 < bytes.length ? CHARS[n & 63] : '=';
  }
  return out;
}

export function base64ToBytes(b64: string): Uint8Array {
  const clean = b64.replace(/[^A-Za-z0-9+/]/g, '');
  const len = Math.floor((clean.length * 3) / 4);
  const out = new Uint8Array(len);
  let o = 0;
  for (let i = 0; i < clean.length; i += 4) {
    const n =
      (LOOKUP[clean[i]] << 18) |
      (LOOKUP[clean[i + 1]] << 12) |
      ((LOOKUP[clean[i + 2]] ?? 0) << 6) |
      (LOOKUP[clean[i + 3]] ?? 0);
    if (o < len) out[o++] = (n >> 16) & 255;
    if (o < len) out[o++] = (n >> 8) & 255;
    if (o < len) out[o++] = n & 255;
  }
  return out;
}

export function dataUriToBytes(uri: string): Uint8Array {
  const i = uri.indexOf('base64,');
  if (i < 0) throw new Error('Not a base64 data URI');
  return base64ToBytes(uri.slice(i + 7));
}

export function bytesToDataUri(bytes: Uint8Array, mime: string): string {
  return 'data:' + mime + ';base64,' + bytesToBase64(bytes);
}
