# ORBIT server

Accounts, profile pictures and cosmetic sync for the ORBIT app.

```
cd backend
npm install
JWT_SECRET="$(openssl rand -hex 32)" npm start
```

- Put it behind HTTPS (Render, Railway, Fly.io, or a reverse proxy). The app only accepts https:// URLs.
- Set `JWT_SECRET` (32+ chars). Optional: `PORT`, `DB_PATH` (use a persistent disk).
- Passwords are hashed with scrypt and a per-user salt. Auth endpoints are rate limited per IP.
- `GET /users/:name/loadout` is public so ORBIT-compatible clients can read a player's cosmetics.
- All cosmetics are currently free (`owned: ["*"]`); there is no store yet.
