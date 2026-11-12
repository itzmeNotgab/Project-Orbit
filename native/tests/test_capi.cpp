#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

#include "orbit/gui_model.hpp"
#include "orbit/orbit_capi.h"
#include "../examples/session_clock.hpp"

static int failures = 0, checks = 0;
#define CHECK(c) do { ++checks; if (!(c)) { ++failures; std::printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #c); } } while (0)
static bool has(const char* hay, const char* needle) { return std::strstr(hay, needle) != nullptr; }

static const char* PROFILE =
    "{\"version\":\"1.26.60.4\",\"hook.render.frame\":\"10 20 ?? 40|0\",\"hook.player.tick\":\"AA BB|4\","
    "\"hook.world.time\":\"DE AD BE EF|0\",\"hook.input.key\":\"99 99|0\",\"hook.net.ping\":\"77 ?? 77|0\"}";
static uint8_t IMAGE[64];

static void buildImage() {
    std::memset(IMAGE, 0, sizeof IMAGE);
    const uint8_t a[] = {0x10, 0x20, 0x33, 0x40}, b[] = {0xAA, 0xBB}, c[] = {0xDE, 0xAD, 0xBE, 0xEF}, d[] = {0x99, 0x99}, e[] = {0x77, 0x01, 0x77};
    std::memcpy(IMAGE + 2, a, 4); std::memcpy(IMAGE + 10, b, 2); std::memcpy(IMAGE + 20, c, 4); std::memcpy(IMAGE + 30, d, 2); std::memcpy(IMAGE + 40, e, 3);
}

static void testUnsupportedByDefault() {
    CHECK(orbit_init("1.26.60.4") == 1);
    const char* g = orbit_gui_json();
    CHECK(has(g, "\"supported\":false")); CHECK(!has(g, "\"supported\":true"));
    CHECK(has(g, "unsupported on 1.26.60.4: hook 'render.frame' not resolved"));
    CHECK(orbit_set_enabled("fps", 1) == 0);
    CHECK(orbit_resolve_hooks(IMAGE, sizeof IMAGE, "garbage") == 0);
    CHECK(has(orbit_hook_report(), "missing or malformed"));
    CHECK(orbit_set_enabled("nope", 1) == 0);
}

static void testVersionMismatch() {
    orbit_init("1.26.60.5");
    CHECK(orbit_resolve_hooks(IMAGE, sizeof IMAGE, PROFILE) == 0);
    CHECK(has(orbit_hook_report(), "profile is for 1.26.60.4, game is 1.26.60.5"));
    CHECK(orbit_set_enabled("coords", 1) == 0);
    CHECK(has(orbit_gui_json(), "profile is for 1.26.60.4"));
}

static void testEndToEnd() {
    orbit_init("1.26.60.4");
    CHECK(orbit_resolve_hooks(IMAGE, sizeof IMAGE, PROFILE) == 5);
    CHECK(has(orbit_gui_json(), "\"supported\":true"));
    CHECK(orbit_set_enabled("coords", 1) && orbit_set_enabled("fps", 1) && orbit_set_enabled("compass", 1));
    CHECK(std::strcmp(orbit_hud_json(), "[]") == 0);   // enabled but no data yet: nothing drawn
    for (int i = 0; i < 90; ++i) orbit_post_frame(i / 30.0);
    orbit_post_player_tick(1.5, 70, -2, 180.f, 5.0);
    const char* h = orbit_hud_json();
    CHECK(has(h, "FPS: 30")); CHECK(has(h, "XYZ: 1.5, 70.0, -2.0")); CHECK(has(h, "\"N\""));
    // settings and validation
    CHECK(orbit_set_setting("coords", "Decimals", "0") == 1); CHECK(has(orbit_hud_json(), "XYZ: 2, 70, -2"));
    CHECK(orbit_set_setting("coords", "Decimals", "abc") == 0); CHECK(orbit_set_setting("coords", "Nope", "1") == 0);
    CHECK(orbit_set_setting("compass", "Style", "Long") == 1); CHECK(has(orbit_hud_json(), "\"North\""));
    // HUD layout edit
    CHECK(orbit_hud_update("coords", 0.5f, 0.25f, 9.f, 0.5f, 0xFF00FF00u, 1) == 1);
    CHECK(has(orbit_hud_json(), "\"scale\":4.000000")); CHECK(has(orbit_hud_json(), "#FF00FF00"));
    CHECK(orbit_hud_update("ghost", 0, 0, 1, 1, 0, 1) == 0);
    CHECK(orbit_hud_update("coords", 0.5f, 0.25f, 1.f, 0.5f, 0xFF00FF00u, 0) == 1); CHECK(!has(orbit_hud_json(), "XYZ"));
    // GUI actions
    orbit_gui_search("compa"); { const char* g = orbit_gui_json(); CHECK(has(g, "\"id\":\"compass\"")); CHECK(!has(g, "\"id\":\"fps\"")); }
    orbit_gui_search(""); orbit_gui_category(0); CHECK(has(orbit_gui_json(), "\"id\":\"fps\"")); orbit_gui_category(2); CHECK(!has(orbit_gui_json(), "\"id\":\"fps\""));
    orbit_gui_category(-1); orbit_gui_expand("fps"); CHECK(has(orbit_gui_json(), "\"expanded\":true"));
    orbit_toggle_favorite("speed"); { const char* g = orbit_gui_json(); CHECK(std::strstr(g, "\"id\":\"speed\"") < std::strstr(g, "\"id\":\"compass\"")); }
    // disable stops data flow
    CHECK(orbit_set_enabled("fps", 0)); CHECK(!has(orbit_hud_json(), "FPS"));
    // profiles + persistence
    CHECK(orbit_save_profile("Match") == 1); CHECK(orbit_save_profile("../bad") == 0); CHECK(orbit_save_profile("Mi perfil de práctica #1") == 1);
    std::string cfg = orbit_config_export();
    CHECK(orbit_config_import("not json") == 0);
    orbit_init("1.26.60.4"); orbit_resolve_hooks(IMAGE, sizeof IMAGE, PROFILE);
    CHECK(orbit_config_import(cfg.c_str()) == 1);
    CHECK(orbit_set_enabled("coords", 1) == 1);  // already enabled by config: idempotent
    orbit_post_player_tick(0, 0, 0, 0, 1.0); orbit_post_player_tick(7, 0, 0, 0, 2.0);
    CHECK(has(orbit_gui_json(), "\"enabled\":true"));
    orbit_hud_reset(); CHECK(orbit_hud_json() != nullptr);
    orbit_shutdown(); CHECK(orbit_set_enabled("fps", 1) == 0); CHECK(std::strcmp(orbit_hud_json(), "[]") == 0); orbit_post_frame(1.0);
}

static void testEditMode() {
    orbit_init("1.26.60.4"); orbit_resolve_hooks(IMAGE, sizeof IMAGE, PROFILE);
    orbit_set_enabled("coords", 1); orbit_set_enabled("compass", 1);
    CHECK(orbit_hud_is_editing() == 0);
    CHECK(has(orbit_hud_drag("coords", 0.5f, 0.5f), "\"moved\":false"));          // not editing: refuses
    CHECK(std::strcmp(orbit_hud_json(), "[]") == 0);                                 // no data, not editing: nothing drawn
    orbit_hud_edit_begin(); CHECK(orbit_hud_is_editing() == 1);
    { const char* h = orbit_hud_json(); CHECK(has(h, "[Coordinates]")); CHECK(has(h, "[Compass]")); }  // placeholders while editing
    orbit_hud_set_size("coords", 0.2f, 0.05f); orbit_hud_set_size("compass", 0.1f, 0.05f);
    CHECK(orbit_hud_update("compass", 0.7f, 0.4f, 1.f, 0.9f, 0xFFFFFFFFu, 1) == 1);
    // snap to screen center (element center 0.1 => x = 0.4) and to compass top edge
    { const char* d = orbit_hud_drag("coords", 0.405f, 0.398f); CHECK(has(d, "\"moved\":true")); CHECK(has(d, "\"x\":0.400000")); CHECK(has(d, "\"y\":0.400000")); CHECK(has(d, "\"vertical\":true")); CHECK(has(d, "\"vertical\":false")); }
    // clamped to the screen
    CHECK(has(orbit_hud_drag("coords", 5.f, -3.f), "\"x\":0.800000"));
    // lock blocks drag, resize, visibility
    CHECK(orbit_hud_lock("coords", 1) == 1);
    CHECK(has(orbit_hud_drag("coords", 0.1f, 0.1f), "\"moved\":false")); CHECK(orbit_hud_resize("coords", 2.f) == 0); CHECK(orbit_hud_set_visible("coords", 0) == 0);
    CHECK(orbit_hud_lock("coords", 0) == 1); CHECK(orbit_hud_resize("coords", 2.f) == 1); CHECK(orbit_hud_set_visible("coords", 0) == 1);
    CHECK(orbit_hud_lock("ghost", 1) == 0);
    orbit_hud_reset_layout(); CHECK(has(orbit_hud_json(), "\"x\":0.020000"));
    orbit_hud_edit_done(); CHECK(orbit_hud_is_editing() == 0);
    // layout persists through config export/import
    orbit_hud_edit_begin(); orbit_hud_drag("compass", 0.3f, 0.6f); orbit_hud_edit_done();
    std::string cfg = orbit_config_export();
    orbit_init("1.26.60.4"); orbit_resolve_hooks(IMAGE, sizeof IMAGE, PROFILE);
    CHECK(orbit_config_import(cfg.c_str()) == 1); orbit_hud_edit_begin();
    CHECK(has(orbit_hud_json(), "\"x\":0.300000"));
    orbit_shutdown();
}

static void testThemeAndNotifications() {
    orbit_init("1.26.60.4");
    CHECK(has(orbit_gui_json(), "\"theme\":{\"name\":\"Nebula\""));
    CHECK(orbit_set_theme("Ember") == 1); CHECK(has(orbit_gui_json(), "\"name\":\"Ember\"")); CHECK(orbit_set_theme("Nope") == 0);
    std::string cfg = orbit_config_export(); orbit_init("1.26.60.4"); orbit_config_import(cfg.c_str());
    CHECK(has(orbit_gui_json(), "\"name\":\"Ember\""));
    CHECK(std::strcmp(orbit_notifications_json(), "[]") == 0);
    orbit_notify("Config saved", 1); orbit_notify("Hook missing", 99);
    { const char* n = orbit_notifications_json(); CHECK(has(n, "Config saved")); CHECK(has(n, "\"level\":\"success\"")); CHECK(has(n, "\"level\":\"error\"")); }
    orbit::NotificationQueue q; q.post("a", orbit::NoteLevel::Info, 1000, 500); q.post("b", orbit::NoteLevel::Warning, 1000, 2000);
    CHECK(q.active(1200).size() == 2); CHECK(q.active(1600).size() == 1); CHECK(q.active(4000).empty());
    orbit_shutdown();
}

static void testExampleModule() {
    orbit::EventBus bus; orbit::ModuleRegistry reg(bus);
    reg.setGameInfo("v", {"render.frame"});
    CHECK(reg.add(std::make_unique<SessionClock>()));
    CHECK(reg.enable("session_clock"));
    bus.post(orbit::events::FrameEvent{10.0}); bus.post(orbit::events::FrameEvent{75.0});
    CHECK(reg.find("session_clock")->hudLines()[0] == "Session: 1:05");
    reg.find("session_clock")->setting("Show seconds")->setBool(false);
    CHECK(reg.find("session_clock")->hudLines()[0] == "Session: 1 min");
    orbit::ModuleRegistry r2(bus); r2.setGameInfo("v", {}); r2.add(std::make_unique<SessionClock>());
    CHECK(!r2.enable("session_clock"));
}

static void testThreads() {
    orbit_init("1.26.60.4"); orbit_resolve_hooks(IMAGE, sizeof IMAGE, PROFILE);
    orbit_set_enabled("fps", 1); orbit_set_enabled("coords", 1); orbit_set_enabled("ping", 1);
    std::vector<std::thread> t;
    t.emplace_back([] { for (int i = 0; i < 3000; ++i) orbit_post_frame(i / 60.0); });
    t.emplace_back([] { for (int i = 0; i < 3000; ++i) orbit_post_player_tick(i, 0, 0, 1.f, i / 20.0); });
    t.emplace_back([] { for (int i = 0; i < 3000; ++i) orbit_post_ping(i % 200); });
    t.emplace_back([] { for (int i = 0; i < 300; ++i) { orbit_hud_json(); orbit_gui_json(); orbit_set_enabled("fps", i % 2); orbit_set_setting("coords", "Decimals", i % 2 ? "1" : "2"); } });
    for (auto& x : t) x.join();
    CHECK(true);
    orbit_shutdown();
}

int main() {
    buildImage();
    testUnsupportedByDefault(); testVersionMismatch(); testEndToEnd(); testEditMode(); testThemeAndNotifications(); testExampleModule(); testThreads();
    std::printf("%d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
