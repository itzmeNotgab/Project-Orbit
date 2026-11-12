# Project ORBIT: honest status

See docs/STATUS.md for the per-feature truth table. Summary:

- Launcher (Kotlin/Compose): Home, Customize (Head / Back / Cape / Pets galleries + 3D preview), Library (addons, textures,
  skins, worlds), Settings, account button. Written, NOT yet compiled or run by me; CI will tell.
- Relay and launcher-side module/HUD screens were removed on purpose. Modules and HUD editing belong in-game.
- Native C++ core with HUD Edit Mode logic: host-tested (217 checks, sanitizers clean). Not yet in Minecraft.
- Blocked: anything that runs inside Minecraft (needs verified hook data for the exact version), Microsoft login.

Nothing has been tested on a device. Push, let Actions build, then follow docs/TESTING.md.

## Native mod track (new)
`native/` holds ORBIT's C++20 mod core: event bus, module registry, settings, JSON config, favorites/profiles,
signature scanner and per-version compatibility profiles, 7 HUD modules (FPS, Coordinates, Compass, Speed, World Time,
Keystrokes, Ping), HUD layout + Edit Mode logic, notifications, themes, headless ClickGUI model, C API, example module.
Compiled and tested on a Linux host (217 checks, ASan/UBSan/TSan clean). NOT yet running in Minecraft: no hooks (need
signatures for the exact game version), no renderer, no in-game drawing. See docs/NATIVE-AUDIT-AND-PLAN.md, NATIVE-API.md,
MODULE-DEV.md, COMPAT.md.

## Setup
1. Keep the old app: branch `legacy-rn`. `legacy/` also holds it.
2. Copy this folder's contents to the repo root and push.
3. Optional release signing: repo secrets ORBIT_KEYSTORE_B64, ORBIT_KS_PASS, ORBIT_KEY_ALIAS, ORBIT_KEY_PASS; tag `v0.5.0`.
