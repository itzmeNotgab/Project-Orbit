# Feature status (honest)

Categories: 1 = implemented and tested, 2 = implemented, not yet tested in Minecraft / on a device, 3 = partial,
4 = blocked by missing Minecraft integration, 5 = not implemented.

"Tested" below means automated tests that actually ran (native C++ on a Linux host with ASan/UBSan/TSan; Kotlin
library tests ran in GitHub Actions earlier before this redesign). NO feature has been tested on an Android device or
inside Minecraft. The redesigned Android app has not been compiled by me; `tools/check_repo.py` passes.

| Feature | Category | Notes |
|---|---|---|
| 4-tab launcher (Home, Customize, Library, Settings), account button top right | 2 | |
| Relay, UDP relay, server probe, Kotlin module/HUD screens, overlay | removed | Modules and HUD editing live in-game only. Kotlin `core`/`network` modules deleted; native C++ core supersedes |
| Partial liquid-glass buttons | 2 | Layered translucency, not real backdrop blur. Fallback to solid surfaces (Settings, auto on low-RAM) |
| Themes (Nebula, Ion, Aurora, Ember), animation/glass/dots/intro settings | 2 | |
| Home: install/version detection, launch, ORBIT launch splash | 2 | Splash is ORBIT's own screen only. Minecraft's own screens are not changed |
| Nebula title in Minecraft | 3 | Branding resource pack generator exists (Library > Textures). Whether 1.26.60.4 applies `textures/ui/title.png` is unverified |
| Customize: four galleries (Head, Back, Cape, Pets), search, filters, equipped markers, empty states, slot rules, unlimited saved loadouts | 2 | Loadout/slot logic unit-tested (library module, ran in CI before redesign: re-run needed) |
| Thumbnails | 3 | Drawn schematic icons, not renders of the models |
| 3D character preview (skin, cosmetics, cape, pet, rotation, animations) | 2 | See "3D viewer fix" below. Renderer logic passes automated tests; NOT verified in a real WebView or on a device |
| Cape physics settings | 2 | Preview only |
| Pets walking beside the player | 4 | Preview only; needs game integration |
| Library: addons, texture packs, skins, worlds, streaming import with no size cap, compatibility checks | 2 | Validation logic unit-tested. A valid file is not proof it works in Minecraft |
| Settings: appearance, performance, storage, compatibility, account, about | 2 | |
| ORBIT account sign-in and loadout sync | 2 | Needs your backend. Not Microsoft |
| Microsoft authentication | 5 | Needs an Azure client ID. Not started |
| Native C++ core: events, modules, settings, config, profiles, favorites, compat profiles, signature scanner, HUD layout, HUD Edit Mode logic (drag, snap guides, lock, resize, visibility, reset), notifications, themes, ClickGUI model, C API | 1 for the logic | Host-tested: 217 checks. Not built with the Android NDK, no JNI, not running in Minecraft |
| HUD modules (FPS, Coordinates, Compass, Speed, World Time, Keystrokes, Ping) | 4 | Module logic tested with synthetic events. Real events need hooks for the exact game version |
| In-game ClickGUI, in-game HUD rendering and drag editing | 4 | Logic exists (native). Drawing needs a verified render hook |
| Visual and Performance modules (Fullbright, No Fog, Zoom, ...) | 5 | Need render/game hooks |
| Hook signatures / compatibility data for 1.26.60.4 | 5 | None exist. Cannot be invented |
| NDK, CMake, JNI integration of the native core | 5 | Next phase |

## Known limits and why
- Minecraft files are never modified. ORBIT starts the installed game and hands files to it.
- Android hides Minecraft's own data folder, so worlds/packs already inside Minecraft cannot be listed.
- Only technical limits remain: a manifest or level.dat entry over 4 MB is rejected (zip-bomb guard); HUD scale is bounded to
  0.25-4x so elements cannot vanish; profile names cannot contain '.' (config key delimiter). No limits on loadouts, profiles,
  HUD elements, imported content count or size.

## 3D viewer fix (blank preview report)

Diagnosis, from reading the code and running the renderer script under Node with a mock canvas:
1. The renderer draws nothing until the host sends it a state. In the first Android version the state was sent only
   after the page's "ready" message reached Kotlin. The bridge class was `private` (WebView reaches it by reflection) and the
   page's `post()` swallowed any failure silently, so a failed bridge meant no state, no drawing, no error: a blank view.
2. Pet distance used 1.0 instead of the original renderer's world units (22, range 12-40), so a pet would overlap the character.
3. Layout: fixed screen-height fractions and a too-wide tab row clipped tabs and buttons and let the gallery overflow.

Changes:
- Page: bridge-first `post()` with React Native fallback that logs instead of failing silently; error and unhandled-rejection
  forwarding; double-init guard; resize detected by polling (WebView can start 0x0 and skip resize events); frame errors are
  caught so the loop survives; posts `drawn` after the first real frame.
- Kotlin: public `ViewerBridge`; state is pushed on page load, on `ready` and on every state change (idempotent, so any one
  path is enough); render-process crash handled instead of killing the app; WebView torn down on release; console and
  load errors logged (tag `OrbitViewer`) and shown on screen with a Retry button; 6 s watchdog.
- Settings > 3D viewer shows WebView version, page loaded, bridge ready, first frame size, states sent, last error.
- Customize layout: responsive (`BoxWithConstraints`), portrait and landscape variants, equal-width gallery tabs, search +
  Loadouts on one row, bottom padding in galleries, viewer height from available space.

| Check | Result | Kind |
|---|---|---|
| Renderer script: `ready` posted on load; nothing drawn before first state; draws with the exact default state the app sends and with `{}`; every cosmetic, pet, cape, animation, camera; skin and bad skin; 0x0 start then resize; touch rotate and pinch zoom change output; errors forwarded; no-bridge fallback | 41 checks pass (`node tools/viewer_test.js`) | Automated, mock canvas. NOT a WebView or device test |
| Native C++ core | 217 checks pass | Automated, Linux host |
| Repo checker (`tools/check_repo.py`) | passes | Static |
| Android debug APK build | NOT RUN here (no SDK). Needs a GitHub Actions run | n/a |
| WebView loads renderer; bridge receives `ready`; character visible with no cosmetics; each category; rotation, zoom, resize on a phone; logcat check; low-end hardware | NOT TESTED | Needs your device |

Pixels were never rendered in a real browser engine here, so "the character is visible" is unproven until you check the phone.
