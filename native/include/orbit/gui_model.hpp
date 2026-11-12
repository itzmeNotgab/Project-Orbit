#pragma once
#include <algorithm>
#include <string>
#include <vector>

#include "hud_layout.hpp"
#include "notifications.hpp"

namespace orbit {

/// Headless ClickGUI: all state and actions, no drawing. A renderer (in-game or overlay) draws viewJson()
/// and calls back into these methods. Every action goes through the real registry.
class ClickGuiModel {
public:
    explicit ClickGuiModel(ModuleRegistry& r) : reg_(r) {}

    /// Unknown names are rejected. Stored in the same config file as everything else.
    bool setTheme(const std::string& name) {
        std::size_t n; const ThemePreset* p = themePresets(n);
        for (std::size_t i = 0; i < n; ++i) if (name == p[i].name) { reg_.setRaw("theme.name", name); return true; }
        return false;
    }
    std::string themeName() const { return reg_.getRaw("theme.name", "Nebula"); }
    NotificationQueue& notifications() { return notes_; }

    void setSearch(std::string s) { search_ = std::move(s); }
    void setCategory(int c) { cat_ = c; }          // -1 = all
    void expand(const std::string& id) { expanded_ = expanded_ == id ? std::string() : id; }

    std::vector<Module*> visible() const {
        std::vector<Module*> v;
        for (Module* m : reg_.all()) {
            if (cat_ >= 0 && static_cast<int>(m->category()) != cat_) continue;
            if (!search_.empty() && !contains(m->name(), search_) && !contains(m->description(), search_)) continue;
            v.push_back(m);
        }
        std::stable_sort(v.begin(), v.end(), [&](Module* a, Module* b) {
            bool fa = reg_.favorite(a->id()), fb = reg_.favorite(b->id());
            if (fa != fb) return fa;
            return lower(a->name()) < lower(b->name());
        });
        return v;
    }

    bool toggle(const std::string& id) {
        Module* m = reg_.find(id); if (!m) return false;
        return m->enabled() ? reg_.disable(id) : reg_.enable(id);
    }

    /// Value uses the same text encoding as config files. Returns false for unknown module/setting or invalid value.
    bool setSetting(const std::string& id, const std::string& name, const std::string& value) {
        Module* m = reg_.find(id); if (!m) return false;
        Setting* s = m->setting(name); return s && s->decode(value);
    }
    void toggleFavorite(const std::string& id) { reg_.setFavorite(id, !reg_.favorite(id)); }

    std::string viewJson() const {
        using json::escape;
        std::string o = "{\"search\":\"" + escape(search_) + "\",\"category\":" + std::to_string(cat_) + ",\"modules\":[";
        bool first = true;
        for (Module* m : visible()) {
            if (!first) { o += ","; }
            first = false;
            auto sup = reg_.support(*m);
            o += "{\"id\":\"" + escape(m->id()) + "\",\"name\":\"" + escape(m->name()) + "\",\"description\":\"" + escape(m->description()) +
                 "\",\"category\":\"" + categoryName(m->category()) + "\",\"enabled\":" + (m->enabled() ? "true" : "false") +
                 ",\"supported\":" + (sup.ok ? "true" : "false") + ",\"reason\":\"" + escape(sup.ok ? m->lastError() : sup.reason) +
                 "\",\"favorite\":" + (reg_.favorite(m->id()) ? "true" : "false") + ",\"expanded\":" + (expanded_ == m->id() ? "true" : "false") + ",\"settings\":[";
            bool f2 = true;
            for (auto& s : m->settings()) {
                if (!f2) { o += ","; }
                f2 = false;
                static const char* kinds[] = {"bool", "float", "choice", "color"};
                o += "{\"name\":\"" + escape(s.name) + "\",\"kind\":\"" + kinds[static_cast<int>(s.kind)] + "\",\"value\":\"" + escape(s.encode()) + "\"";
                if (s.kind == SettingKind::Float) o += ",\"min\":" + std::to_string(s.lo) + ",\"max\":" + std::to_string(s.hi);
                if (s.kind == SettingKind::Choice) { o += ",\"options\":["; for (std::size_t i = 0; i < s.options.size(); ++i) o += (i ? ",\"" : "\"") + escape(s.options[i]) + "\""; o += "]"; }
                o += "}";
            }
            o += "]}";
        }
        std::string pr = "["; auto ps = reg_.profiles();
        for (std::size_t i = 0; i < ps.size(); ++i) pr += (i ? ",\"" : "\"") + escape(ps[i]) + "\"";
        std::size_t n; const ThemePreset* tp = themePresets(n); std::string tn = themeName();
        const ThemePreset* cur = &tp[0]; for (std::size_t i = 0; i < n; ++i) if (tn == tp[i].name) cur = &tp[i];
        char tc[96]; std::snprintf(tc, sizeof tc, "{\"name\":\"%s\",\"accent\":\"#%08X\",\"background\":\"#%08X\",\"surface\":\"#%08X\"}", cur->name, cur->accent, cur->background, cur->surface);
        return o + "],\"profiles\":" + pr + "],\"theme\":" + tc + "}";
    }

private:
    static std::string lower(std::string s) { for (auto& c : s) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c))); return s; }
    static bool contains(const std::string& h, const std::string& n) { return lower(h).find(lower(n)) != std::string::npos; }
    ModuleRegistry& reg_;
    std::string search_, expanded_;
    NotificationQueue notes_;
    int cat_ = -1;
};

}  // namespace orbit
