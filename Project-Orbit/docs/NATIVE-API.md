# ORBIT native API

Header: `native/include/orbit/orbit_capi.h` (plain C, thread-safe). Static library target: `orbit_runtime`.

Flow when running inside a mod loader:
1. `orbit_init("<game version>")`. All modules start **unsupported**.
2. Resolve hooks: `orbit_resolve_hooks(image, size, profileJson)` scans the loaded game library with the signatures in the
   profile for that exact version. A hook counts only if its signature matches exactly once. `orbit_hook_report()` says why any failed.
3. Hook code (version specific) calls `orbit_post_frame / player_tick / world_time / key / ping` as the game runs.
4. UI calls `orbit_gui_json()` to draw the ClickGUI and `orbit_set_enabled / orbit_set_setting / ...` for actions.
   The renderer calls `orbit_hud_json()` each frame for text blocks to draw.
5. Persist with `orbit_config_export()` / `orbit_config_import()`.

Tested on a Linux host with ASan, UBSan and TSan (`native/tests`). Not yet loaded inside Minecraft.

## Config keys (flat JSON, all values are strings)
`mod.<id>.enabled`, `mod.<id>.<setting>`, `fav.<id>`, `profile.<name>.mod.*`, `hud.<id>.{x,y,scale,opacity,color,visible}`.
Invalid values are ignored; a file that is not valid JSON changes nothing.
