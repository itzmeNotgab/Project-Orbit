import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Anim, Cam, COSMETICS } from '../catalog';
import { PackKind } from '../lib/packs';
import { useSettings } from '../settings/SettingsContext';

export type SkinItem = { id: string; name: string; dataUri: string; slim: boolean; addedAt: number };
export type CapeItem = { id: string; name: string; dataUri: string; addedAt: number };
export type PackEntry = {
  id: string;
  name: string;
  kind: PackKind;
  fileName: string;
  uri: string;
  sizeBytes: number;
  addedAt: number;
  version: string;
  minEngine: string | null;
  description: string;
  icon: string | null;
  packs: { name: string; uuid: string; version: string; kind: string }[];
  warnings: string[];
  installedHint: boolean;
};
export type WorldEntry = {
  id: string;
  name: string;
  fileName: string;
  uri: string;
  sizeBytes: number;
  addedAt: number;
  icon: string | null;
  favorite: boolean;
  backupOf: string | null;
};

export type Loadout = {
  cosmetics: string[];
  skinId: string | null;
  capeId: string; // 'none' | builtin id | custom id
  capePhysics: boolean;
  capeStiffness: number;
  capeSway: number;
  petId: string | null;
  petDistance: number;
  petVisible: boolean;
  anim: Anim;
  camera: Cam;
};

const DEFAULT_LOADOUT: Loadout = {
  cosmetics: [],
  skinId: null,
  capeId: 'none',
  capePhysics: true,
  capeStiffness: 0.5,
  capeSway: 0.6,
  petId: null,
  petDistance: 22,
  petVisible: true,
  anim: 'idle',
  camera: 'free',
};

function useStored<T>(key: string, initial: T, sanitize: (raw: unknown) => T) {
  const [value, setValue] = useState<T>(initial);
  const [loaded, setLoaded] = useState(false);
  const valueRef = useRef<T>(initial);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    AsyncStorage.getItem(key)
      .then((json) => {
        if (json) {
          const v = sanitize(JSON.parse(json));
          valueRef.current = v;
          setValue(v);
        }
      })
      .catch(() => {})
      .finally(() => setLoaded(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const set = useCallback(
    (updater: (prev: T) => T) => {
      const next = updater(valueRef.current);
      valueRef.current = next;
      setValue(next);
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => {
        AsyncStorage.setItem(key, JSON.stringify(valueRef.current)).catch(() => {});
      }, 250);
    },
    [key],
  );
  return { value, set, loaded };
}

const arr = <T,>(x: unknown): T[] => (Array.isArray(x) ? (x as T[]) : []);

function sanitizeLoadout(raw: unknown): Loadout {
  const r = (raw ?? {}) as Partial<Loadout>;
  const known = new Set(COSMETICS.map((c) => c.id));
  return {
    ...DEFAULT_LOADOUT,
    ...r,
    cosmetics: arr<string>(r.cosmetics).filter((id) => known.has(id)),
  };
}

type LibraryCtx = {
  ready: boolean;
  skins: SkinItem[];
  capes: CapeItem[];
  packs: PackEntry[];
  worlds: WorldEntry[];
  loadout: Loadout;
  setSkins: (u: (p: SkinItem[]) => SkinItem[]) => void;
  setCapes: (u: (p: CapeItem[]) => CapeItem[]) => void;
  setPacks: (u: (p: PackEntry[]) => PackEntry[]) => void;
  setWorlds: (u: (p: WorldEntry[]) => WorldEntry[]) => void;
  updateLoadout: (patch: Partial<Loadout>) => void;
  resetAll: () => Promise<void>;
  viewerState: ViewerState;
};

export type ViewerState = {
  skin: string | null;
  slim: boolean;
  anim: Anim;
  camera: Cam;
  cosmetics: string[];
  cape: { enabled: boolean; id: string; tex: string | null; physics: boolean; stiffness: number; sway: number };
  pet: { id: string | null; distance: number; visible: boolean };
  accent: string;
  spin: boolean;
};

const Ctx = createContext<LibraryCtx | null>(null);

export function LibraryProvider({ children }: { children: ReactNode }) {
  const { settings } = useSettings();
  const skins = useStored<SkinItem[]>('orbit.skins.v1', [], arr);
  const capes = useStored<CapeItem[]>('orbit.capes.v1', [], arr);
  const packs = useStored<PackEntry[]>('orbit.packs.v1', [], arr);
  const worlds = useStored<WorldEntry[]>('orbit.worlds.v1', [], arr);
  const loadout = useStored<Loadout>('orbit.loadout.v1', DEFAULT_LOADOUT, sanitizeLoadout);

  const updateLoadout = useCallback(
    (patch: Partial<Loadout>) => loadout.set((p) => ({ ...p, ...patch })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [loadout.set],
  );

  const resetAll = useCallback(async () => {
    skins.set(() => []);
    capes.set(() => []);
    packs.set(() => []);
    worlds.set(() => []);
    loadout.set(() => DEFAULT_LOADOUT);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [skins.set, capes.set, packs.set, worlds.set, loadout.set]);

  const L = loadout.value;
  const viewerState = useMemo<ViewerState>(() => {
    const skin = skins.value.find((s) => s.id === L.skinId) ?? null;
    const custom = capes.value.find((c) => c.id === L.capeId) ?? null;
    return {
      skin: skin ? skin.dataUri : null,
      slim: skin ? skin.slim : false,
      anim: L.anim,
      camera: L.camera,
      cosmetics: L.cosmetics,
      cape: {
        enabled: L.capeId !== 'none',
        id: custom ? 'orbit_nebula' : L.capeId === 'none' ? 'orbit_nebula' : L.capeId,
        tex: custom ? custom.dataUri : null,
        physics: L.capePhysics,
        stiffness: L.capeStiffness,
        sway: L.capeSway,
      },
      pet: { id: L.petId, distance: L.petDistance, visible: L.petVisible },
      accent: settings.accent,
      spin: false,
    };
  }, [L, skins.value, capes.value, settings.accent]);

  const ready = skins.loaded && capes.loaded && packs.loaded && worlds.loaded && loadout.loaded;

  const value: LibraryCtx = {
    ready,
    skins: skins.value,
    capes: capes.value,
    packs: packs.value,
    worlds: worlds.value,
    loadout: L,
    setSkins: skins.set,
    setCapes: capes.set,
    setPacks: packs.set,
    setWorlds: worlds.set,
    updateLoadout,
    resetAll,
    viewerState,
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useLibrary(): LibraryCtx {
  const c = useContext(Ctx);
  if (!c) throw new Error('useLibrary must be used inside LibraryProvider');
  return c;
}
