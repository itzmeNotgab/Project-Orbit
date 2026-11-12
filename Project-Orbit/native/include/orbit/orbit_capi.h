#pragma once
/* ORBIT native runtime C API. All functions are thread-safe. Returned strings stay valid until the next call
 * from the same thread. Plain C so a Kotlin/JNI layer, a Lua binding or a hook trampoline can call it. */
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

/* Lifecycle */
int orbit_init(const char* game_version);       /* 1 ok. Registers built-in modules. Every module starts unsupported until hooks resolve. */
void orbit_shutdown(void);

/* Compatibility: profile is flat JSON {"version":"..","hook.<name>":"PATTERN|offset"}.
 * Returns the number of hooks resolved (0 on mismatch or bad profile). */
int orbit_resolve_hooks(const uint8_t* image, size_t size, const char* profile_json);
const char* orbit_hook_report(void);             /* JSON: {"resolved":[..],"failures":{name:reason}} */

/* Control (what the ClickGUI calls). 1 = success. */
int orbit_set_enabled(const char* module_id, int enabled);
int orbit_set_setting(const char* module_id, const char* name, const char* value);
int orbit_toggle_favorite(const char* module_id);
void orbit_gui_search(const char* text);
void orbit_gui_category(int category);           /* -1 = all */
void orbit_gui_expand(const char* module_id);
const char* orbit_gui_json(void);                /* full ClickGUI view model */
int orbit_save_profile(const char* name);
int orbit_load_profile(const char* name);

/* HUD */
const char* orbit_hud_json(void);                /* draw commands for this frame */
int orbit_hud_update(const char* id, float x, float y, float scale, float opacity, uint32_t argb, int visible);
void orbit_hud_reset(void);

/* HUD Edit Mode (all coordinates are fractions of the screen) */
void orbit_hud_edit_begin(void);
void orbit_hud_edit_done(void);                  /* positions are already saved on every change */
int orbit_hud_is_editing(void);
void orbit_hud_set_size(const char* id, float w, float h);  /* renderer reports the measured size so guides can use it */
const char* orbit_hud_drag(const char* id, float x, float y); /* {"moved":bool,"x":..,"y":..,"guides":[{"vertical":bool,"pos":..}]} */
int orbit_hud_resize(const char* id, float scale);
int orbit_hud_lock(const char* id, int locked);
int orbit_hud_set_visible(const char* id, int visible);
void orbit_hud_reset_layout(void);               /* only while editing */

/* Notifications and themes */
void orbit_notify(const char* text, int level);  /* 0 info, 1 success, 2 warning, 3 error */
const char* orbit_notifications_json(void);      /* currently visible toasts */
int orbit_set_theme(const char* name);           /* Nebula, Ion, Aurora, Ember */

/* Persistence */
const char* orbit_config_export(void);
int orbit_config_import(const char* json);       /* 0 and nothing changed if invalid */

/* Event sources: called by version-specific hook code */
void orbit_post_frame(double time_sec);
void orbit_post_player_tick(double x, double y, double z, float yaw, double time_sec);
void orbit_post_world_time(int64_t ticks);
void orbit_post_key(char key, int down);
void orbit_post_ping(int ms);

#ifdef __cplusplus
}
#endif
