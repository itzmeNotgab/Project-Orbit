import React, { createContext, useCallback, useContext, useEffect, useState, ReactNode } from 'react';
import * as SecureStore from 'expo-secure-store';
import { api, ApiUser } from '../lib/api';
import { useSettings } from '../settings/SettingsContext';

const TOKEN_KEY = 'orbit.token';

type AuthCtx = {
  user: ApiUser | null;
  token: string | null;
  busy: boolean;
  error: string | null;
  signUp: (u: string, p: string) => Promise<boolean>;
  signIn: (u: string, p: string) => Promise<boolean>;
  signOut: () => Promise<void>;
  setAvatar: (dataUri: string | null) => Promise<boolean>;
  setUser: (u: ApiUser) => void;
};

const Ctx = createContext<AuthCtx | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const { settings, loaded } = useSettings();
  const [user, setUser] = useState<ApiUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!loaded || !settings.serverUrl) return;
    let cancelled = false;
    (async () => {
      try {
        const t = await SecureStore.getItemAsync(TOKEN_KEY);
        if (!t) return;
        const res = await api.me(settings.serverUrl, t);
        if (!cancelled) {
          setToken(t);
          setUser(res.user);
        }
      } catch {
        // offline or expired: stay signed out, keep the stored token for the next try
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loaded, settings.serverUrl]);

  const run = useCallback(async (fn: () => Promise<{ token: string; user: ApiUser }>) => {
    setBusy(true);
    setError(null);
    try {
      const res = await fn();
      await SecureStore.setItemAsync(TOKEN_KEY, res.token);
      setToken(res.token);
      setUser(res.user);
      return true;
    } catch (e: any) {
      setError(String(e?.message ?? e));
      return false;
    } finally {
      setBusy(false);
    }
  }, []);

  const signUp = useCallback((u: string, p: string) => run(() => api.signup(settings.serverUrl, u, p)), [run, settings.serverUrl]);
  const signIn = useCallback((u: string, p: string) => run(() => api.login(settings.serverUrl, u, p)), [run, settings.serverUrl]);

  const signOut = useCallback(async () => {
    await SecureStore.deleteItemAsync(TOKEN_KEY).catch(() => {});
    setToken(null);
    setUser(null);
  }, []);

  const setAvatar = useCallback(
    async (dataUri: string | null) => {
      if (!token) return false;
      setBusy(true);
      setError(null);
      try {
        const res = await api.setAvatar(settings.serverUrl, token, dataUri);
        setUser(res.user);
        return true;
      } catch (e: any) {
        setError(String(e?.message ?? e));
        return false;
      } finally {
        setBusy(false);
      }
    },
    [token, settings.serverUrl],
  );

  return <Ctx.Provider value={{ user, token, busy, error, signUp, signIn, signOut, setAvatar, setUser }}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthCtx {
  const c = useContext(Ctx);
  if (!c) throw new Error('useAuth must be used inside AuthProvider');
  return c;
}
