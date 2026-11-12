import { orbitNative } from '../native/orbitNative';

export const MC_PACKAGE = 'com.mojang.minecraftpe';

export type McStatus = {
  nativeAvailable: boolean;
  installed: boolean;
  versionName: string | null;
  versionCode: number | null;
  androidSdk: number | null;
};

export function detectMinecraft(): McStatus {
  if (!orbitNative) {
    return { nativeAvailable: false, installed: false, versionName: null, versionCode: null, androidSdk: null };
  }
  let info = null;
  let sdk: number | null = null;
  try {
    info = orbitNative.getPackageInfo(MC_PACKAGE);
    sdk = orbitNative.getAndroidSdk();
  } catch {
    // treated as not installed
  }
  return {
    nativeAvailable: true,
    installed: !!info,
    versionName: info ? info.versionName : null,
    versionCode: info ? info.versionCode : null,
    androidSdk: sdk,
  };
}

export function launchMinecraft(): boolean {
  if (!orbitNative) return false;
  try {
    return orbitNative.launchPackage(MC_PACKAGE);
  } catch {
    return false;
  }
}

export function parseVersion(v: string | null | undefined): number[] {
  if (!v) return [];
  const m = v.match(/\d+(\.\d+)*/);
  return m ? m[0].split('.').map((n) => parseInt(n, 10)) : [];
}

/** Returns <0 if a<b, 0 if equal, >0 if a>b. */
export function compareVersions(a: number[], b: number[]): number {
  const n = Math.max(a.length, b.length);
  for (let i = 0; i < n; i++) {
    const d = (a[i] ?? 0) - (b[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
}
