import { requireOptionalNativeModule } from 'expo-modules-core';

export type NativePackageInfo = { versionName: string; versionCode: number };

type OrbitNativeApi = {
  getPackageInfo(pkg: string): NativePackageInfo | null;
  launchPackage(pkg: string): boolean;
  getAndroidSdk(): number;
};

/** Null when the native module is not linked (e.g. running in Expo Go). */
export const orbitNative = requireOptionalNativeModule<OrbitNativeApi>('OrbitNative');
