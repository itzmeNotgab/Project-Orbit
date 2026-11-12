# ORBIT: audit, independent architecture and realistic plan

Status date: Nov 2026. No code was changed for this document. Nothing here has been run on a device.

## 1. Audit: what existed before the redesign (see STATUS.md for the current state)

| Area | State | Evidence level |
|---|---|---|
| Kotlin/Compose launcher shell, intro, theme, tabs | Written | Compiled only up to a duplicate-file error you were fixing; full APK build NOT confirmed |
| Minecraft detect + launch (package intent) | Written | Untested on device |
| Customize: 3D viewer (WebView), cosmetics (hats/halos/head/wings/tails/back/shoulder/other), capes (built-in, custom PNG, physics), pets (preview), animations, cameras | Ported from legacy | Untested on device |
| Skins: PNG import, skin pack read/build (.mcpack) | Written | Logic unit-tested (library module), UI untested |
| Packs/add-ons: manifest validation, store, hand over to Minecraft | Written | Logic unit-tested, UI untested |
| Worlds: import .mcworld, backup, export | Written | Logic unit-tested, UI untested |
| Account: ORBIT backend sign-in, loadout sync, Keystore token | Written | Untested; this is NOT Microsoft auth |
| Modules tab, favorites, profiles, in-app HUD preview, HUD editor, system overlay | Written | Untested. Overlay is a separate Android window |
| UDP relay + server probe | Written | Relay unit-tested; never run against Minecraft |
| C++ native core (events, registry, settings, config, profiles, scanner, compat profiles, 7 HUD modules, HUD layout, ClickGUI model, C API) | Written | Host-tested (179 checks, ASan/UBSan/TSan). NOT built with NDK, NO JNI, not in Gradle |
| CI | Written | Android build had unresolved errors at last report |

Incomplete or missing: Microsoft authentication; Settings screen; notifications and theme system; pet walking-beside-player preview; texture-pack enabling (impossible from outside the game); Script API generator (still in `legacy/src/lib/ingame.ts`); NDK/CMake/JNI integration; any in-game code path; any compatibility data for 1.26.60.4.

## 2. Integration methods for real in-game features

| Method | Possible? | What it can do | Needs | Limits |
|---|---|---|---|---|
| A. Official content: behavior pack + Script API, resource pack, skin pack | Yes, supported by Mojang | Real in-game output today: action-bar/title text, forms (@minecraft/server-ui), reading player position/rotation, game time, scoreboards; resource-pack visuals | Pack generation, user enables it in a world, Minecraft on a device | Per world/server. No FPS, no keystrokes, no render hooks. Which Script API modules are stable on 1.26.60.4 must be checked against Mojang docs. Servers/Realms may block packs |
| B. Network proxy | Partly | Anything visible in packets (ping, chat, movement); can inject action-bar text into the real game HUD | Bedrock codec for the target protocol (2225 line), RakNet, Microsoft/Xbox login | Servers only. No codec found for 1.26.60 from a trusted source. No rendering control |
| C. Overlay window | Yes (built) | Draw text above any app | Overlay permission | Not Minecraft's renderer. Needs a data source (B) to show game data |
| D. In-process native runtime (own launcher hosts the game) | Technically yes (other projects do it) | True client modules: FPS, keystrokes, render options, in-game ClickGUI | Reverse engineering of libminecraftpe.so for the exact version, a hooking backend, game hosting, graphics API integration | Biggest effort and risk. Breaks on every Minecraft update. Mojang/server rules (anti-cheat bans) must be checked. Not testable without a device and the game |
| E. Hooking from outside the process | No | | | Android sandboxing forbids it on a normal device |

## 3. Proposed independent architecture

Four separable parts, communicating only through small interfaces. No LeviLauncher code or runtime is required at any point.

1. `app` (Kotlin, exists): UI, library, accounts, settings. Talks to the engine through `EngineClient` (new interface). Works fully with no engine present.
2. `launcher` (Kotlin, new): `GameLocator` (installed package, base + split APK paths from PackageManager, version, signing check), `VersionRegistry` (known-good versions and their compat profiles), `LaunchPlan` (vanilla / Script API pack / hosted). Hosted mode is the only part that is research.
3. `engine` (C++, exists as `native/`): module framework, config, HUD model, ClickGUI model, compat profile verification, C API. Gains: NDK build, JNI wrapper (`libminecraftpe`-independent), safe mode.
4. `integration` (C++, new, research): `HookBackend` interface (choose an existing permissively licensed hooking library, license checked), `ImageResolver` (loaded game library base/size), event producers that call `orbit_post_*`, renderer adapter. Everything here is optional and version-gated.

Compatibility strategy (verifiable, no guessing): version detected from PackageManager; profile must match the exact version; every hook must match its signature exactly once; any failure means vanilla launch and a clear report ("safe mode"). Already implemented in `native/` except the Android glue.

Visual identity stays: planet logo, dark/purple theme, dots, intro. In-game menu will reuse the same tokens (colors, spacing) exported from one theme file.

## 4. Biggest blockers
1. Signatures/offsets for 1.26.60.4 do not exist and cannot be invented. They require analysing that exact game build, or reusing a project that already supports it under its license.
2. Hosting the game in ORBIT's process (split APKs, native library load order, Android 14+ dynamic-code rules, game licence checks) is unproven.
3. In-game rendering needs a hook into the game's graphics layer; the graphics API used by 1.26.60.4 must be confirmed on a device.
4. Microsoft/Xbox login needs a registered Azure client ID and is only needed for the proxy/profile features.
5. The current Android build and all UI are unverified on a device.
6. Legal/policy: check Minecraft's EULA/usage guidelines and server rules before shipping hook-based modules.
7. Cosmetics (wings, halos, tails, pets) cannot show in vanilla for other players; they show only in ORBIT's preview, in your own client if method D works, or as skin geometry (to be verified).

## 5. Work that needs no Minecraft internals
Get the APK building; Settings, notifications, themes; Microsoft login (once you register a client ID); GameLocator/VersionRegistry; NDK + CMake + JNI for `native/` with an emulator smoke test in CI; port the Script API generator from legacy; HUD-via-Script-API pack; pet preview polish; docs.

## 6. Work that needs a real device and Minecraft
Every in-game claim: Script API pack behaviour, relay against the game, hosting, signature derivation, hook and render tests, memory/crash checks, Mali performance.

## 7. Phased plan with exit criteria
1. Audit (this document). Exit: you agree with it.
2. Architecture. Exit: interfaces above written as code (`EngineClient`, `GameLocator`, `HookBackend`) with no game dependency.
3. NDK/CMake/JNI. Exit: CI builds `libOrbitEngine.so` for arm64, an emulator test calls `orbit_init` and reads the ClickGUI JSON from Kotlin.
4. Compatibility strategy. Exit: GameLocator reports installed version and APK splits on your phone; profile verification runs against a known-good test image; safe mode proven in a test.
5. First real in-game feature (recommended: Script API pack showing coordinates/compass/time in the action bar, generated and imported by ORBIT). Exit: you see it in a world on your phone. In parallel research track: prove one hook on the target version.
6. In-game HUD and ClickGUI. Only after a verified render path (method D) or by keeping them as Script API forms (method A) plus overlay.
7. More modules and customization, each one only when its event source is verified.

## 8. Things I will not do
Invent signatures or compatibility data, claim device results, add LeviLauncher as a dependency, or ship placeholder modules as working.
