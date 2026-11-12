import React, { createContext, useCallback, useContext, useEffect, useRef, useState, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ACCENTS, DEFAULT_ACCENT } from '../theme/theme';

export type Settings = { introEnabled: boolean; accent: string; serverUrl: string };

const KEY = 'orbit.settings.v1';
const DEFAULTS: Settings = { introEnabled: true, accent: DEFAULT_ACCENT, serverUrl: '' };

type Ctx = { settings: Settings; loaded: boolean; update: (patch: Partial<Settings>) => void };
const SettingsContext = createContext<Ctx | null>(null);

function sanitize(raw: unknown): Settings {
  const r = (raw ?? {}) as Partial<Settings>;
  return {
    introEnabled: typeof r.introEnabled === 'boolean' ? r.introEnabled : DEFAULTS.introEnabled,
    accent: ACCENTS.some((a) => a.value === r.accent) ? (r.accent as string) : DEFAULTS.accent,
    serverUrl: typeof r.serverUrl === 'string' ? r.serverUrl.trim().slice(0, 200) : '',
  };
}

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<Settings>(DEFAULTS);
  const [loaded, setLoaded] = useState(false);
  const ref = useRef<Settings>(DEFAULTS);

  useEffect(() => {
    AsyncStorage.getItem(KEY)
      .then((json) => {
        const next = sanitize(json ? JSON.parse(json) : null);
        ref.current = next;
        setSettings(next);
      })
      .catch(() => {})
      .finally(() => setLoaded(true));
  }, []);

  const update = useCallback((patch: Partial<Settings>) => {
    const next = sanitize({ ...ref.current, ...patch });
    ref.current = next;
    setSettings(next);
    AsyncStorage.setItem(KEY, JSON.stringify(next)).catch(() => {});
  }, []);

  return <SettingsContext.Provider value={{ settings, loaded, update }}>{children}</SettingsContext.Provider>;
}

export function useSettings(): Ctx {
  const ctx = useContext(SettingsContext);
  if (!ctx) throw new Error('useSettings must be used inside SettingsProvider');
  return ctx;
}
