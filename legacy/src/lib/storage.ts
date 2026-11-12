import { File, Directory, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as IntentLauncher from 'expo-intent-launcher';
import { MC_PACKAGE } from './minecraft';

/**
 * All file-system access goes through this module so that API changes in
 * expo-file-system only need fixing here.
 */

export function safeName(name: string): string {
  const cleaned = name.replace(/[^A-Za-z0-9._-]+/g, '_').replace(/^_+|_+$/g, '');
  return cleaned.length ? cleaned.slice(0, 80) : 'file';
}

function folder(name: string): Directory {
  const d = new Directory(Paths.document, 'orbit', name);
  if (!d.exists) d.create({ intermediates: true });
  return d;
}

export async function readBytes(uri: string): Promise<Uint8Array> {
  return await new File(uri).bytes();
}

export function fileSize(uri: string): number {
  const f = new File(uri);
  return f.exists ? f.size ?? 0 : 0;
}

export function copyIntoLibrary(srcUri: string, folderName: string, fileName: string): string {
  const dest = new File(folder(folderName), safeName(fileName));
  if (dest.exists) dest.delete();
  new File(srcUri).copy(dest);
  return dest.uri;
}

export function writeBytes(folderName: string, fileName: string, bytes: Uint8Array): string {
  const f = new File(folder(folderName), safeName(fileName));
  if (f.exists) f.delete();
  f.create();
  f.write(bytes);
  return f.uri;
}

export function removeFile(uri: string): void {
  try {
    const f = new File(uri);
    if (f.exists) f.delete();
  } catch {
    // already gone
  }
}

export function fileExists(uri: string): boolean {
  try {
    return new File(uri).exists;
  } catch {
    return false;
  }
}

/** Hands a file to Minecraft using Android's VIEW intent (the supported import path). */
export async function openInMinecraft(uri: string): Promise<'opened' | 'shared'> {
  try {
    const content = new File(uri).contentUri;
    await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
      data: content,
      flags: 1,
      packageName: MC_PACKAGE,
    });
    return 'opened';
  } catch {
    await shareFile(uri);
    return 'shared';
  }
}

export async function shareFile(uri: string, mimeType = 'application/octet-stream'): Promise<void> {
  if (!(await Sharing.isAvailableAsync())) throw new Error('Sharing is not available on this device.');
  await Sharing.shareAsync(uri, { mimeType, dialogTitle: 'Share file' });
}
