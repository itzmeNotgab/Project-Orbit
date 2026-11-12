# Native mod runtime: Phase 1 audit and integration plan

Sources: the four GitHub repository pages, read Nov 2026. I did NOT read their source files, submodules
or LICENSE files directly (rate-limited), so everything below is from repo pages and READMEs. Verify before copying code.

## Reference projects
| Project | License (as shown) | What it is | Relevance |
|---|---|---|---|
| LeviLaunchroid (LiteLDev) | Apache-2.0, has NOTICE file | Launcher: imports your own Minecraft APK, runs it without installing, loads native `.so` mods, multi-version, Xbox accounts. Uses git submodules. | The runtime that real in-game mods run on. |
| GlowberryClient (ChimeraAnt-DEV) | Apache-2.0 per GitHub and badge; README footer also says "All rights reserved" (contradiction) | Launcher in the same family, adds `.AntEgg` packs, Lua mods, in-game menu, HUD editor. Preloader and LibHttpClient are submodules. | Ideas for lifecycle, packaging, HUD editor. Small project (3 stars). |
| BedrockTools (QYCottage) | CONFLICT: GitHub says MIT, README says GPL-3.0 | 51-module native C++20 mod for LeviLauncher, xmake, NDK r28c, public SDK headers, `levimod.json`, `.levipack`. Signatures and offsets are version specific. | Best reference for module behaviour and SDK shape. Treat as GPL-3.0 until the LICENSE file is read. Contains modules ORBIT will not adopt (Hitbox, Reach Counter, Skin Stealer, AutoSprint, Hive Utils, No Disconnect). |
| Xelo-Client | GPL-3.0 | Launcher with launcher-based modules, themes, shader support (materialbinloader2), particle/shadow optimizations. Its own checklist says the in-game mod menu is NOT done. | Themes and optimization ideas only. |

## Key facts that shape the plan
1. Real in-game modules need code running inside Minecraft's process (hooks into libminecraftpe.so). Every project above does this by hosting the game in a launcher with a Preloader. A companion app or proxy cannot do it.
2. Hooks are found by byte signatures that change with every Minecraft build. Nobody can write them for 1.26.60.4 without analysing that exact library. I cannot obtain it here and will not invent signatures.
3. Whether any existing Preloader or BedrockTools release supports 1.26.60.4 is unknown. Check their releases.

## Plan
- Reuse the LeviLauncher Preloader. Do not write a second loader.
- ORBIT native mod (`liborbit.so`, C++20) built on the core in `native/`, packaged for the launcher.
- Version support is data: `compat/<version>.json` listing hook signatures. Unresolved hook => module reported unsupported (implemented).
- Event sources (hooks) are the only version-specific code. Modules consume events and are testable without the game (implemented).
- Phase order: (done) core + HUD logic; (needs signatures) hook producers; (needs hooks) HUD rendering, ClickGUI, Visual/Performance modules.
- Existing Kotlin app stays as launcher/config UI and companion. The proxy track is paused.

## Decisions needed from you
1. License. To reuse BedrockTools or Xelo code, ORBIT's distributed work must be GPL-3.0. Otherwise ORBIT stays Apache-2.0/MIT with original code only. Which?
2. Runtime. (A) ORBIT ships as a native mod inside LeviLauncher (fastest, works now). (B) ORBIT embeds a fork of the launcher so it launches Minecraft itself (more work, Apache-2.0 permits it with NOTICE).
3. Signatures for 1.26.60.4. Does a LeviLauncher-compatible mod exist for your exact version? If yes I adapt its data; if no, someone must derive them.
