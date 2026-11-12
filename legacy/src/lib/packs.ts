import { unzipSync, strFromU8 } from 'fflate';
import { bytesToDataUri } from './base64';

export type PackKind = 'addon' | 'behavior' | 'resource' | 'skin';

export type PackInfo = {
  name: string;
  description: string;
  uuid: string;
  version: string;
  minEngine: string | null;
  moduleTypes: string[];
  kind: 'behavior' | 'resource' | 'skin';
  icon: string | null;
};

export type InspectResult =
  | { ok: true; kind: PackKind; packs: PackInfo[]; warnings: string[] }
  | { ok: false; error: string };

export const MAX_PACK_BYTES = 60 * 1024 * 1024;
export const MAX_WORLD_BYTES = 100 * 1024 * 1024;

function parseJsonLoose(text: string): any {
  const t = text.replace(/^\uFEFF/, '');
  try {
    return JSON.parse(t);
  } catch {
    const stripped = t
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/(^|[^:"'])\/\/.*$/gm, '$1')
      .replace(/,(\s*[}\]])/g, '$1');
    return JSON.parse(stripped);
  }
}

function versionString(v: unknown): string {
  if (Array.isArray(v)) return v.join('.');
  if (typeof v === 'string') return v;
  return '0';
}

function dirOf(path: string): string {
  const i = path.lastIndexOf('/');
  return i < 0 ? '' : path.slice(0, i + 1);
}

function collectPacks(files: Record<string, Uint8Array>, warnings: string[]): PackInfo[] {
  const packs: PackInfo[] = [];
  for (const path of Object.keys(files)) {
    if (!/(^|\/)manifest\.json$/i.test(path)) continue;
    let m: any;
    try {
      m = parseJsonLoose(strFromU8(files[path]));
    } catch {
      warnings.push('Could not read ' + path);
      continue;
    }
    const header = m?.header;
    if (!header?.uuid) {
      warnings.push(path + ' has no header uuid');
      continue;
    }
    const mods: string[] = Array.isArray(m.modules) ? m.modules.map((x: any) => String(x?.type ?? '')) : [];
    let kind: PackInfo['kind'] | null = null;
    if (mods.includes('skin_pack')) kind = 'skin';
    else if (mods.includes('resources')) kind = 'resource';
    else if (mods.includes('data') || mods.includes('script')) kind = 'behavior';
    if (!kind) {
      warnings.push(path + ' has no supported module type (' + (mods.join(', ') || 'none') + ')');
      continue;
    }
    const iconEntry = files[dirOf(path) + 'pack_icon.png'];
    packs.push({
      name: String(header.name ?? 'Unnamed pack'),
      description: String(header.description ?? ''),
      uuid: String(header.uuid),
      version: versionString(header.version),
      minEngine: header.min_engine_version ? versionString(header.min_engine_version) : null,
      moduleTypes: mods,
      kind,
      icon: iconEntry && iconEntry.length < 120_000 ? bytesToDataUri(iconEntry, 'image/png') : null,
    });
  }
  return packs;
}

export function inspectPackArchive(bytes: Uint8Array, fileName: string): InspectResult {
  if (bytes.length > MAX_PACK_BYTES) {
    return { ok: false, error: 'File is larger than 60 MB; ORBIT will not load it into memory to inspect it.' };
  }
  const warnings: string[] = [];
  let files: Record<string, Uint8Array>;
  try {
    files = unzipSync(bytes, {
      filter: (f) => /(^|\/)(manifest\.json|pack_icon\.png)$/i.test(f.name) || /\.(mcpack|zip)$/i.test(f.name),
    });
  } catch {
    return { ok: false, error: 'Not a valid zip archive.' };
  }
  const packs = collectPacks(files, warnings);
  for (const path of Object.keys(files)) {
    if (!/\.(mcpack|zip)$/i.test(path)) continue;
    try {
      const inner = unzipSync(files[path], { filter: (f) => /(^|\/)(manifest\.json|pack_icon\.png)$/i.test(f.name) });
      packs.push(...collectPacks(inner, warnings));
    } catch {
      warnings.push('Could not open nested archive ' + path);
    }
  }
  if (packs.length === 0) {
    return { ok: false, error: 'No valid manifest.json found. This does not look like a Bedrock pack.' };
  }
  const isAddon = /\.mcaddon$/i.test(fileName) || packs.length > 1;
  return { ok: true, kind: isAddon ? 'addon' : packs[0].kind, packs, warnings };
}

export type WorldInfo = { name: string; icon: string | null };

export function inspectWorldArchive(bytes: Uint8Array): { ok: true; world: WorldInfo } | { ok: false; error: string } {
  if (bytes.length > MAX_WORLD_BYTES) {
    return { ok: false, error: 'World is larger than 100 MB; ORBIT will not load it into memory to inspect it. Open it directly in Minecraft instead.' };
  }
  let files: Record<string, Uint8Array>;
  try {
    files = unzipSync(bytes, {
      filter: (f) => /(^|\/)(level\.dat|levelname\.txt|world_icon\.(jpeg|jpg))$/i.test(f.name),
    });
  } catch {
    return { ok: false, error: 'Not a valid zip archive.' };
  }
  const names = Object.keys(files);
  if (!names.some((n) => /(^|\/)level\.dat$/i.test(n))) {
    return { ok: false, error: 'No level.dat found. This does not look like a Bedrock world.' };
  }
  const nameEntry = names.find((n) => /(^|\/)levelname\.txt$/i.test(n));
  const iconEntry = names.find((n) => /world_icon\.(jpeg|jpg)$/i.test(n));
  const name = nameEntry ? strFromU8(files[nameEntry]).trim() : '';
  const icon = iconEntry && files[iconEntry].length < 150_000 ? bytesToDataUri(files[iconEntry], 'image/jpeg') : null;
  return { ok: true, world: { name: name || 'Unnamed world', icon } };
}
