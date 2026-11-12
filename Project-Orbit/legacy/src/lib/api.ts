export type ApiUser = { id: number; username: string; avatar: string | null; owned: string[] };
export type SyncedLoadout = {
  cosmetics: string[];
  capeId: string;
  capeData: string | null;
  petId: string | null;
};

async function request<T>(base: string, path: string, opts: { method?: string; token?: string | null; body?: unknown } = {}): Promise<T> {
  if (!base) throw new Error('No ORBIT server URL set. Add one in Settings.');
  if (!/^https:\/\//i.test(base)) throw new Error('The server URL must start with https://');
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 15000);
  try {
    const res = await fetch(base.replace(/\/+$/, '') + path, {
      method: opts.method ?? 'GET',
      headers: { 'Content-Type': 'application/json', ...(opts.token ? { Authorization: 'Bearer ' + opts.token } : {}) },
      body: opts.body ? JSON.stringify(opts.body) : undefined,
      signal: ctrl.signal,
    });
    const text = await res.text();
    let data: any = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      // non-JSON response
    }
    if (!res.ok) throw new Error((data && data.error) || 'Server returned HTTP ' + res.status);
    return data as T;
  } catch (e: any) {
    if (e?.name === 'AbortError') throw new Error('The server did not respond in time.');
    throw e;
  } finally {
    clearTimeout(timer);
  }
}

export const api = {
  health: (base: string) => request<{ ok: boolean }>(base, '/health'),
  signup: (base: string, username: string, password: string) =>
    request<{ token: string; user: ApiUser }>(base, '/auth/signup', { method: 'POST', body: { username, password } }),
  login: (base: string, username: string, password: string) =>
    request<{ token: string; user: ApiUser }>(base, '/auth/login', { method: 'POST', body: { username, password } }),
  me: (base: string, token: string) => request<{ user: ApiUser }>(base, '/me', { token }),
  setAvatar: (base: string, token: string, avatar: string | null) =>
    request<{ user: ApiUser }>(base, '/me/profile', { method: 'PUT', token, body: { avatar } }),
  putLoadout: (base: string, token: string, loadout: SyncedLoadout) =>
    request<{ ok: boolean }>(base, '/me/loadout', { method: 'PUT', token, body: { loadout } }),
  getLoadout: (base: string, token: string) => request<{ loadout: SyncedLoadout | null }>(base, '/me/loadout', { token }),
  userLoadout: (base: string, username: string) =>
    request<{ username: string; loadout: SyncedLoadout | null }>(base, '/users/' + encodeURIComponent(username) + '/loadout'),
};
