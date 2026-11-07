import * as DocumentPicker from 'expo-document-picker';
import { readBytes } from './storage';

export type Picked = { uri: string; name: string; size: number; bytes: Uint8Array };

/** Opens the system file picker and returns the file's bytes, or null if cancelled. */
export async function pickFile(types: string[] = ['*/*'], maxBytes = 100 * 1024 * 1024): Promise<Picked | null> {
  const res = await DocumentPicker.getDocumentAsync({ type: types, copyToCacheDirectory: true, multiple: false });
  if (res.canceled || !res.assets?.length) return null;
  const a = res.assets[0];
  const size = a.size ?? 0;
  if (size > maxBytes) throw new Error('File is too large (' + Math.round(size / 1048576) + ' MB; limit ' + Math.round(maxBytes / 1048576) + ' MB).');
  const bytes = await readBytes(a.uri);
  return { uri: a.uri, name: a.name, size: size || bytes.length, bytes };
}

/** Like pickFile but returns only the URI (for large files that are copied, not read). */
export async function pickUri(types: string[] = ['*/*']): Promise<{ uri: string; name: string; size: number } | null> {
  const res = await DocumentPicker.getDocumentAsync({ type: types, copyToCacheDirectory: true, multiple: false });
  if (res.canceled || !res.assets?.length) return null;
  const a = res.assets[0];
  return { uri: a.uri, name: a.name, size: a.size ?? 0 };
}
