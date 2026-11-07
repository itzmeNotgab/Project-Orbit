# Project ORBIT: honest status

| Phase | Status |
|---|---|
| 1 Native Kotlin/Compose app, CI | Done |
| 2 Transport + local relay | Done as raw UDP relay + server probe |
| 3 Packet forwarding with decoding | NOT done, see docs/PHASE3-4-BLOCKER.md |
| 4 Authentication, 1.26.60.4 compatibility | NOT done, same reason |
| 5 Events, modules | Done (framework, 3 modules, profiles, favorites). Packet listeners/entity tracking need Phase 3 |
| 6 ClickGUI / HUD | In-app Modules tab + HUD editor + system overlay over Minecraft. No internal Minecraft renderer (impossible) |
| 7 Migration | Done: 3D viewer, cosmetics, capes, pets, skins/skinpacks, packs, worlds, account sync |
| 8 Release/optimization | Release workflow + R8 + signing via secrets. No device measurements yet |

Nothing has been compiled or run. Push, let Actions build, then follow docs/TESTING.md.
Not ported: account profile picture, cosmetic ownership, in-game Script API add-on generator (still in legacy/src/lib/ingame.ts).

## Setup
1. Keep the old app: branch `legacy-rn`. `legacy/` also holds it.
2. Copy this folder's contents to the repo root and push.
3. Optional release signing: repo secrets ORBIT_KEYSTORE_B64, ORBIT_KS_PASS, ORBIT_KEY_ALIAS, ORBIT_KEY_PASS; tag `v0.5.0`.
