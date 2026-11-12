// ORBIT account + cosmetic sync server.
// Required env: JWT_SECRET (32+ random chars). Optional: PORT, DB_PATH.
// Deploy behind HTTPS (the Android app refuses plain http).
import express from 'express';
import Database from 'better-sqlite3';
import jwt from 'jsonwebtoken';
import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';

const SECRET = process.env.JWT_SECRET;
if (!SECRET || SECRET.length < 32) {
  console.error('Set JWT_SECRET to a random string of at least 32 characters.');
  process.exit(1);
}
const PORT = Number(process.env.PORT || 3000);
const db = new Database(process.env.DB_PATH || 'orbit.db');
db.pragma('journal_mode = WAL');
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE COLLATE NOCASE,
  salt BLOB NOT NULL,
  hash BLOB NOT NULL,
  avatar TEXT,
  created INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS loadouts (
  user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  json TEXT NOT NULL,
  updated INTEGER NOT NULL
);
`);

const hashPw = (pw, salt) =>
  new Promise((resolve, reject) => scrypt(pw, salt, 64, { N: 16384, r: 8, p: 1 }, (e, k) => (e ? reject(e) : resolve(k))));

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '600kb' }));

// Tiny in-memory rate limiter for auth endpoints (per IP).
const hits = new Map();
function limit(max, windowMs) {
  return (req, res, next) => {
    const key = req.ip + req.path;
    const now = Date.now();
    const arr = (hits.get(key) || []).filter((t) => now - t < windowMs);
    if (arr.length >= max) return res.status(429).json({ error: 'Too many attempts. Try again later.' });
    arr.push(now);
    hits.set(key, arr);
    next();
  };
}
setInterval(() => hits.clear(), 3600_000).unref();

const USER_RE = /^[A-Za-z0-9_]{3,20}$/;
const publicUser = (u) => ({ id: u.id, username: u.username, avatar: u.avatar, owned: ['*'] });
const sign = (u) => jwt.sign({ sub: u.id }, SECRET, { expiresIn: '30d' });

function auth(req, res, next) {
  const h = req.headers.authorization || '';
  if (!h.startsWith('Bearer ')) return res.status(401).json({ error: 'Not signed in.' });
  try {
    const p = jwt.verify(h.slice(7), SECRET);
    const u = db.prepare('SELECT * FROM users WHERE id = ?').get(p.sub);
    if (!u) return res.status(401).json({ error: 'Account no longer exists.' });
    req.user = u;
    next();
  } catch {
    res.status(401).json({ error: 'Session expired. Sign in again.' });
  }
}

app.get('/health', (_req, res) => res.json({ ok: true }));

app.post('/auth/signup', limit(10, 3600_000), async (req, res) => {
  const { username, password } = req.body || {};
  if (typeof username !== 'string' || !USER_RE.test(username)) return res.status(400).json({ error: 'Username must be 3-20 letters, numbers, or underscores.' });
  if (typeof password !== 'string' || password.length < 8 || password.length > 100) return res.status(400).json({ error: 'Password must be 8-100 characters.' });
  if (db.prepare('SELECT 1 FROM users WHERE username = ?').get(username)) return res.status(409).json({ error: 'That username is taken.' });
  const salt = randomBytes(16);
  const hash = await hashPw(password, salt);
  const info = db.prepare('INSERT INTO users (username, salt, hash, created) VALUES (?,?,?,?)').run(username, salt, hash, Date.now());
  const u = db.prepare('SELECT * FROM users WHERE id = ?').get(info.lastInsertRowid);
  res.json({ token: sign(u), user: publicUser(u) });
});

app.post('/auth/login', limit(20, 900_000), async (req, res) => {
  const { username, password } = req.body || {};
  if (typeof username !== 'string' || typeof password !== 'string') return res.status(400).json({ error: 'Enter a username and password.' });
  const u = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
  const fake = randomBytes(16);
  const hash = await hashPw(password, u ? u.salt : fake);
  if (!u || !timingSafeEqual(hash, u.hash)) return res.status(401).json({ error: 'Wrong username or password.' });
  res.json({ token: sign(u), user: publicUser(u) });
});

app.get('/me', auth, (req, res) => res.json({ user: publicUser(req.user) }));

app.put('/me/profile', auth, (req, res) => {
  const { avatar } = req.body || {};
  if (avatar !== null && (typeof avatar !== 'string' || !/^data:image\/(png|jpeg);base64,[A-Za-z0-9+/=]+$/.test(avatar) || avatar.length > 220_000)) {
    return res.status(400).json({ error: 'Profile picture must be a PNG or JPEG under 150 KB.' });
  }
  db.prepare('UPDATE users SET avatar = ? WHERE id = ?').run(avatar, req.user.id);
  res.json({ user: publicUser({ ...req.user, avatar }) });
});

function validLoadout(l) {
  if (!l || typeof l !== 'object') return false;
  if (!Array.isArray(l.cosmetics) || l.cosmetics.length > 40 || l.cosmetics.some((c) => typeof c !== 'string' || c.length > 40)) return false;
  if (typeof l.capeId !== 'string' || l.capeId.length > 40) return false;
  if (l.capeData !== null && (typeof l.capeData !== 'string' || !l.capeData.startsWith('data:image/png;base64,') || l.capeData.length > 450_000)) return false;
  if (l.petId !== null && (typeof l.petId !== 'string' || l.petId.length > 40)) return false;
  return true;
}

app.put('/me/loadout', auth, (req, res) => {
  const l = req.body && req.body.loadout;
  if (!validLoadout(l)) return res.status(400).json({ error: 'Invalid loadout.' });
  const clean = { cosmetics: l.cosmetics, capeId: l.capeId, capeData: l.capeData, petId: l.petId };
  db.prepare('INSERT INTO loadouts (user_id, json, updated) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET json = excluded.json, updated = excluded.updated').run(req.user.id, JSON.stringify(clean), Date.now());
  res.json({ ok: true });
});

app.get('/me/loadout', auth, (req, res) => {
  const row = db.prepare('SELECT json FROM loadouts WHERE user_id = ?').get(req.user.id);
  res.json({ loadout: row ? JSON.parse(row.json) : null });
});

// Public: lets ORBIT-compatible clients read another player's cosmetics.
app.get('/users/:username/loadout', limit(120, 60_000), (req, res) => {
  const u = db.prepare('SELECT id, username FROM users WHERE username = ?').get(req.params.username);
  if (!u) return res.status(404).json({ error: 'No such player.' });
  const row = db.prepare('SELECT json FROM loadouts WHERE user_id = ?').get(u.id);
  res.json({ username: u.username, loadout: row ? JSON.parse(row.json) : null });
});

app.use((_req, res) => res.status(404).json({ error: 'Not found.' }));
app.listen(PORT, () => console.log('ORBIT server listening on ' + PORT));
