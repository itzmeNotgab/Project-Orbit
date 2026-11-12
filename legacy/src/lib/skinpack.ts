import { strToU8, unzipSync, strFromU8, zipSync } from 'fflate';
import { dataUriToBytes, bytesToDataUri } from './base64';
import { readPngInfo } from './png';
import { uuidv4 } from './ids';

export type SkinInput = { name: string; dataUri: string; slim: boolean };

function id(name: string): string {
  const s = name.replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  return s.length ? s.slice(0, 24) : 'skin';
}

/** Builds a Bedrock skin pack (.mcpack) containing the given skins. */
export function buildSkinPack(packName: string, skins: SkinInput[]): Uint8Array {
  const serialize = 'Orbit_' + id(packName);
  const files: Record<string, Uint8Array> = {};
  const skinEntries: object[] = [];
  const lang: string[] = ['skinpack.' + serialize + '=' + packName];
  skins.forEach((s, i) => {
    const key = 'skin' + (i + 1);
    files[key + '.png'] = dataUriToBytes(s.dataUri);
    skinEntries.push({
      localization_name: key,
      geometry: s.slim ? 'geometry.humanoid.customSlim' : 'geometry.humanoid.custom',
      texture: key + '.png',
      type: 'free',
    });
    lang.push('skin.' + serialize + '.' + key + '=' + s.name);
  });
  files['skins.json'] = strToU8(JSON.stringify({ skins: skinEntries, serialize_name: serialize, localization_name: serialize }, null, 2));
  files['manifest.json'] = strToU8(
    JSON.stringify(
      {
        format_version: 1,
        header: { name: packName, uuid: uuidv4(), version: [1, 0, 0] },
        modules: [{ type: 'skin_pack', uuid: uuidv4(), version: [1, 0, 0] }],
      },
      null,
      2,
    ),
  );
  files['texts/en_US.lang'] = strToU8(lang.join('\n') + '\n');
  files['texts/languages.json'] = strToU8('["en_US"]');
  return zipSync(files);
}

/** Reads the skins out of an existing skin pack. */
export function readSkinPack(bytes: Uint8Array): { skins: SkinInput[]; skipped: number } {
  const files = unzipSync(bytes, { filter: (f) => /(^|\/)skins\.json$/i.test(f.name) || /\.png$/i.test(f.name) });
  const skinsPath = Object.keys(files).find((p) => /(^|\/)skins\.json$/i.test(p));
  if (!skinsPath) throw new Error('This pack has no skins.json, so it is not a skin pack.');
  const base = skinsPath.slice(0, skinsPath.length - 'skins.json'.length);
  const json = JSON.parse(strFromU8(files[skinsPath]).replace(/^\uFEFF/, ''));
  const out: SkinInput[] = [];
  let skipped = 0;
  for (const s of Array.isArray(json.skins) ? json.skins : []) {
    const png = files[base + String(s.texture)];
    const info = png ? readPngInfo(png) : null;
    const validShape = !!info && info.width >= 64 && (info.height === info.width || info.height * 2 === info.width);
    if (!png || !info || !validShape || png.length > 256 * 1024) {
      skipped++;
      continue;
    }
    out.push({
      name: String(s.localization_name ?? 'Skin'),
      dataUri: bytesToDataUri(png, 'image/png'),
      slim: /slim/i.test(String(s.geometry ?? '')),
    });
  }
  return { skins: out, skipped };
}
