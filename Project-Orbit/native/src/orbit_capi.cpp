#include "orbit/orbit_capi.h"

#include <chrono>
#include <memory>
#include <mutex>

#include "orbit/compat.hpp"
#include "orbit/gui_model.hpp"
#include "orbit/hud_modules.hpp"

using namespace orbit;

namespace {
std::int64_t nowMs() { return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count(); }
struct Runtime {
    EventBus bus;
    ModuleRegistry reg{bus};
    HudLayout hud{reg};
    ClickGuiModel gui{reg};
    HudEditor editor{hud};
    std::string version;
    std::string hookReport = "{\"resolved\":[],\"failures\":{}}";
};
std::mutex g_m;
std::unique_ptr<Runtime> g_rt;
thread_local std::string t_out;

const char* ret(std::string s) { t_out = std::move(s); return t_out.c_str(); }
std::string str(const char* p) { return p ? p : ""; }

template <class E>
void post(const E& e) {
    // Hold no global lock while handlers run; copy the pointer under the lock.
    Runtime* r; { std::lock_guard<std::mutex> l(g_m); r = g_rt.get(); }
    if (r) r->bus.post(e);
}
}  // namespace

extern "C" {

int orbit_init(const char* v) {
    std::lock_guard<std::mutex> l(g_m);
    auto rt = std::make_unique<Runtime>();
    rt->version = str(v);
    rt->reg.setGameInfo(rt->version, {}, {});
    modules::registerHud(rt->reg);
    for (Module* m : rt->reg.all()) rt->hud.ensure(m->id());
    g_rt = std::move(rt);
    return 1;
}

void orbit_shutdown(void) { std::lock_guard<std::mutex> l(g_m); g_rt.reset(); }

int orbit_resolve_hooks(const uint8_t* image, size_t size, const char* profile_json) {
    std::lock_guard<std::mutex> l(g_m);
    if (!g_rt) return 0;
    CompatProfile p;
    if (!image || !CompatProfile::fromJson(str(profile_json), p)) {
        g_rt->reg.setGameInfo(g_rt->version, {}, {});
        g_rt->hookReport = "{\"resolved\":[],\"failures\":{\"profile\":\"missing or malformed\"}}";
        return 0;
    }
    auto r = p.resolve(g_rt->version, image, size);
    g_rt->reg.setGameInfo(g_rt->version, r.names(), r.failures);
    std::string rep = "{\"resolved\":[";
    bool f = true; for (auto& kv : r.resolved) { rep += (f ? "\"" : ",\"") + json::escape(kv.first) + "\""; f = false; }
    rep += "],\"failures\":{"; f = true;
    for (auto& kv : r.failures) { rep += (f ? "\"" : ",\"") + json::escape(kv.first) + "\":\"" + json::escape(kv.second) + "\""; f = false; }
    g_rt->hookReport = rep + "}}";
    return static_cast<int>(r.resolved.size());
}

const char* orbit_hook_report(void) { std::lock_guard<std::mutex> l(g_m); return ret(g_rt ? g_rt->hookReport : "{}"); }

#define WITH_RT(default_val) std::lock_guard<std::mutex> l(g_m); if (!g_rt) return default_val; Runtime& rt = *g_rt

int orbit_set_enabled(const char* id, int on) { WITH_RT(0); return (on ? rt.reg.enable(str(id)) : rt.reg.disable(str(id))) ? 1 : 0; }
int orbit_set_setting(const char* id, const char* n, const char* v) { WITH_RT(0); return rt.gui.setSetting(str(id), str(n), str(v)) ? 1 : 0; }
int orbit_toggle_favorite(const char* id) { WITH_RT(0); rt.gui.toggleFavorite(str(id)); return 1; }
void orbit_gui_search(const char* t) { std::lock_guard<std::mutex> l(g_m); if (g_rt) g_rt->gui.setSearch(str(t)); }
void orbit_gui_category(int c) { std::lock_guard<std::mutex> l(g_m); if (g_rt) g_rt->gui.setCategory(c); }
void orbit_gui_expand(const char* id) { std::lock_guard<std::mutex> l(g_m); if (g_rt) g_rt->gui.expand(str(id)); }
const char* orbit_gui_json(void) { std::lock_guard<std::mutex> l(g_m); return ret(g_rt ? g_rt->gui.viewJson() : "{}"); }
int orbit_save_profile(const char* n) { WITH_RT(0); return rt.reg.saveProfile(str(n)) ? 1 : 0; }
int orbit_load_profile(const char* n) { WITH_RT(0); return rt.reg.loadProfile(str(n)) ? 1 : 0; }

const char* orbit_hud_json(void) {
    std::lock_guard<std::mutex> l(g_m);
    if (!g_rt) return ret("[]");
    std::string o = "[";
    bool first = true;
    for (auto& c : g_rt->hud.frame(g_rt->editor.editing())) {
        if (!first) { o += ","; }
            first = false;
        char col[16]; std::snprintf(col, sizeof col, "#%08X", c.color);
        o += "{\"id\":\"" + json::escape(c.id) + "\",\"x\":" + std::to_string(c.x) + ",\"y\":" + std::to_string(c.y) +
             ",\"scale\":" + std::to_string(c.scale) + ",\"opacity\":" + std::to_string(c.opacity) + ",\"color\":\"" + col + "\",\"locked\":" + (c.locked ? "true" : "false") + ",\"lines\":[";
        for (std::size_t i = 0; i < c.lines.size(); ++i) o += (i ? ",\"" : "\"") + json::escape(c.lines[i]) + "\"";
        o += "]}";
    }
    return ret(o + "]");
}
int orbit_hud_update(const char* id, float x, float y, float s, float op, uint32_t argb, int vis) { WITH_RT(0); return rt.hud.update(str(id), x, y, s, op, argb, vis != 0) ? 1 : 0; }
void orbit_hud_reset(void) { std::lock_guard<std::mutex> l(g_m); if (g_rt) g_rt->hud.resetAll(); }

void orbit_hud_edit_begin(void) { std::lock_guard<std::mutex> l(g_m); if (g_rt) g_rt->editor.begin(); }
void orbit_hud_edit_done(void) { std::lock_guard<std::mutex> l(g_m); if (g_rt) g_rt->editor.done(); }
int orbit_hud_is_editing(void) { WITH_RT(0); return rt.editor.editing() ? 1 : 0; }
void orbit_hud_set_size(const char* id, float w, float h) { std::lock_guard<std::mutex> l(g_m); if (g_rt) g_rt->hud.setSize(str(id), w, h); }
const char* orbit_hud_drag(const char* id, float x, float y) {
    std::lock_guard<std::mutex> l(g_m);
    if (!g_rt) return ret("{}");
    auto r = g_rt->editor.drag(str(id), x, y);
    std::string o = std::string("{\"moved\":") + (r.moved ? "true" : "false") + ",\"x\":" + std::to_string(r.x) + ",\"y\":" + std::to_string(r.y) + ",\"guides\":[";
    for (std::size_t i = 0; i < r.guides.size(); ++i) {
        if (i) { o += ","; }
        o += std::string("{\"vertical\":") + (r.guides[i].vertical ? "true" : "false") + ",\"pos\":" + std::to_string(r.guides[i].pos) + "}";
    }
    return ret(o + "]}");
}
int orbit_hud_resize(const char* id, float s) { WITH_RT(0); return rt.editor.resize(str(id), s) ? 1 : 0; }
int orbit_hud_lock(const char* id, int v) { WITH_RT(0); return rt.editor.setLocked(str(id), v != 0) ? 1 : 0; }
int orbit_hud_set_visible(const char* id, int v) { WITH_RT(0); return rt.editor.setVisible(str(id), v != 0) ? 1 : 0; }
void orbit_hud_reset_layout(void) { std::lock_guard<std::mutex> l(g_m); if (g_rt) g_rt->editor.resetLayout(); }

void orbit_notify(const char* text, int level) {
    std::lock_guard<std::mutex> l(g_m);
    if (!g_rt) return;
    int lv = level < 0 ? 0 : (level > 3 ? 3 : level);
    g_rt->gui.notifications().post(str(text), static_cast<NoteLevel>(lv), nowMs());
}
const char* orbit_notifications_json(void) {
    std::lock_guard<std::mutex> l(g_m);
    if (!g_rt) return ret("[]");
    static const char* names[] = {"info", "success", "warning", "error"};
    std::string o = "[";
    auto v = g_rt->gui.notifications().active(nowMs());
    for (std::size_t i = 0; i < v.size(); ++i) {
        if (i) { o += ","; }
        o += "{\"text\":\"" + json::escape(v[i].text) + "\",\"level\":\"" + names[static_cast<int>(v[i].level)] + "\"}";
    }
    return ret(o + "]");
}
int orbit_set_theme(const char* n) { WITH_RT(0); return rt.gui.setTheme(str(n)) ? 1 : 0; }

const char* orbit_config_export(void) { std::lock_guard<std::mutex> l(g_m); return ret(g_rt ? g_rt->reg.toJson() : "{}"); }
int orbit_config_import(const char* j) {
    WITH_RT(0);
    if (!rt.reg.fromJson(str(j))) return 0;
    rt.hud.load(); return 1;
}

void orbit_post_frame(double t) { post(events::FrameEvent{t}); }
void orbit_post_player_tick(double x, double y, double z, float yaw, double t) { post(events::PlayerTickEvent{x, y, z, yaw, t}); }
void orbit_post_world_time(int64_t t) { post(events::WorldTimeEvent{t}); }
void orbit_post_key(char k, int d) { post(events::KeyEvent{k, d != 0}); }
void orbit_post_ping(int ms) { post(events::PingEvent{ms}); }

}  // extern "C"
