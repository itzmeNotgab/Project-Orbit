# Phase 5 report (framework only)

## Implemented (`core`, pure Kotlin, unit tested)
- `EventBus`: thread-safe, error-isolated, sticky replay, unsubscribe.
- `Module` / `ModuleManager`: categories, enable/disable, typed settings (bool, float, choice),
  compatibility validation, failing modules stay disabled with a visible error, persistence to
  `orbit-config.properties` (corrupt values ignored/clamped).
- Built-in modules, each backed by real data: Relay Uptime, Network Stats (packets, KB/s from relay
  counters), Memory (ORBIT's own heap).
- App: Modules tab with search, category filter, toggles, expandable settings, HUD preview.
- Tests: `FrameworkTest` (9).

## NOT implemented
- Packet listeners and entity tracking: these need decoded Bedrock packets (Phase 3/4). Not faked.
  So no coordinates, direction, armor or FPS modules exist.
- ClickGUI/HUD inside Minecraft. The Modules tab is an in-app screen; the HUD preview is a preview only.
  A real in-game overlay is not possible without injection; a system overlay (SYSTEM_ALERT_WINDOW) can
  draw over the game but is not Minecraft's renderer, and is not built.
- HUD editor (move/resize/colors/layouts).
