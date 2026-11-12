#pragma once
#include <cctype>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <string>
#include <vector>

#include "json.hpp"
#include "module.hpp"

namespace orbit {

struct Support { bool ok; std::string reason; };

class ModuleRegistry {
public:
    explicit ModuleRegistry(EventBus& bus) : bus_(bus) {}

    /// Set by the runtime after signature validation. Modules whose hooks are missing refuse to enable.
    void setGameInfo(std::string version, std::set<std::string> resolvedHooks, std::map<std::string, std::string> failures = {}) {
        std::lock_guard<std::recursive_mutex> l(m_); version_ = std::move(version); hooks_ = std::move(resolvedHooks); failures_ = std::move(failures);
    }
    const std::string& gameVersion() const { return version_; }

    /// Extra persisted values owned by other components (HUD layout, theme...). Survive toJson/fromJson.
    void setRaw(const std::string& k, const std::string& v) { std::lock_guard<std::recursive_mutex> l(m_); cfg_[k] = v; }
    std::string getRaw(const std::string& k, const std::string& d = {}) const { std::lock_guard<std::recursive_mutex> l(m_); auto it = cfg_.find(k); return it == cfg_.end() ? d : it->second; }

    bool add(std::unique_ptr<Module> m) {
        std::lock_guard<std::recursive_mutex> l(m_);
        if (!m || byId_.count(m->id())) return false;
        m->bus_ = &bus_;
        Module* raw = m.get();
        mods_.push_back(std::move(m)); byId_[raw->id()] = raw;
        for (auto& s : raw->settings()) {
            auto it = cfg_.find("mod." + raw->id() + "." + s.name);
            if (it != cfg_.end()) s.decode(it->second);
        }
        auto en = cfg_.find("mod." + raw->id() + ".enabled");
        if (en != cfg_.end() && en->second == "true") enable(raw->id());
        return true;
    }

    std::vector<Module*> all() const {
        std::lock_guard<std::recursive_mutex> l(m_);
        std::vector<Module*> v; for (auto& m : mods_) v.push_back(m.get()); return v;
    }
    Module* find(const std::string& id) const {
        std::lock_guard<std::recursive_mutex> l(m_);
        auto it = byId_.find(id); return it == byId_.end() ? nullptr : it->second;
    }

    Support support(const Module& m) const {
        std::lock_guard<std::recursive_mutex> l(m_);
        for (auto& h : m.requiredHooks())
            if (!hooks_.count(h))
                return {false, "unsupported on " + (version_.empty() ? std::string("this game version") : version_) + ": hook '" + h + "' not resolved" +
                           (failures_.count(h) ? " (" + failures_.at(h) + ")" : std::string())};
        return {true, {}};
    }

    bool enable(const std::string& id) {
        std::lock_guard<std::recursive_mutex> l(m_);
        Module* m = find(id); if (!m) return false;
        if (m->enabled_) return true;
        auto s = support(*m);
        if (!s.ok) { m->error_ = s.reason; return false; }
        try { m->onEnable(); m->enabled_ = true; m->error_.clear(); }
        catch (const std::exception& e) { teardown(*m); m->error_ = e.what(); return false; }
        catch (...) { teardown(*m); m->error_ = "unknown error"; return false; }
        return true;
    }

    bool disable(const std::string& id) {
        std::lock_guard<std::recursive_mutex> l(m_);
        Module* m = find(id); if (!m) return false;
        if (!m->enabled_) return true;
        teardown(*m); m->enabled_ = false; return true;
    }

    // ---- persistence ----
    std::string toJson() {
        std::lock_guard<std::recursive_mutex> l(m_);
        sync();
        return json::write(cfg_);
    }

    /// Returns false (and changes nothing) if the text is not a valid config. Bad individual values are ignored.
    bool fromJson(const std::string& text) {
        std::lock_guard<std::recursive_mutex> l(m_);
        std::map<std::string, std::string> parsed;
        if (!json::read(text, parsed)) return false;
        cfg_ = std::move(parsed);
        for (auto& m : mods_) {
            for (auto& s : m->settings()) { auto it = cfg_.find("mod." + m->id() + "." + s.name); if (it != cfg_.end()) s.decode(it->second); }
            auto en = cfg_.find("mod." + m->id() + ".enabled");
            if (en != cfg_.end()) { if (en->second == "true") enable(m->id()); else disable(m->id()); }
        }
        return true;
    }

    // ---- favorites and profiles ----
    bool favorite(const std::string& id) const { std::lock_guard<std::recursive_mutex> l(m_); auto it = cfg_.find("fav." + id); return it != cfg_.end() && it->second == "true"; }
    void setFavorite(const std::string& id, bool v) { std::lock_guard<std::recursive_mutex> l(m_); cfg_["fav." + id] = v ? "true" : "false"; }

    bool saveProfile(const std::string& name) {
        if (!validName(name)) return false;
        std::lock_guard<std::recursive_mutex> l(m_);
        sync();
        for (auto it = cfg_.begin(); it != cfg_.end();) it = it->first.rfind("profile." + name + ".", 0) == 0 ? cfg_.erase(it) : std::next(it);
        std::map<std::string, std::string> snap;
        for (auto& [k, v] : cfg_) if (k.rfind("mod.", 0) == 0) snap["profile." + name + "." + k] = v;
        cfg_.insert(snap.begin(), snap.end());
        return true;
    }

    bool loadProfile(const std::string& name) {
        std::lock_guard<std::recursive_mutex> l(m_);
        const std::string p = "profile." + name + ".";
        bool any = false;
        for (auto& m : mods_) {
            for (auto& s : m->settings()) { auto it = cfg_.find(p + "mod." + m->id() + "." + s.name); if (it != cfg_.end()) { s.decode(it->second); any = true; } }
            auto en = cfg_.find(p + "mod." + m->id() + ".enabled");
            if (en != cfg_.end()) { any = true; if (en->second == "true") enable(m->id()); else disable(m->id()); }
        }
        return any;
    }

    std::vector<std::string> profiles() const {
        std::lock_guard<std::recursive_mutex> l(m_);
        std::set<std::string> n;
        for (auto& [k, v] : cfg_) if (k.rfind("profile.", 0) == 0) n.insert(k.substr(8, k.find('.', 8) - 8));
        return {n.begin(), n.end()};
    }

private:
    static bool validName(const std::string& n) {
        // Only technical limits: '.' separates config key parts and control characters break the file. Any other text is fine.
        if (n.empty() || n.size() > 256) return false;
        for (unsigned char c : n) if (c == '.' || c < 0x20) return false;
        return true;
    }
    void teardown(Module& m) {
        for (auto t : m.tokens_) bus_.unsubscribe(t);
        m.tokens_.clear();
        try { m.onDisable(); } catch (...) {}
    }
    void sync() {
        for (auto& m : mods_) {
            cfg_["mod." + m->id() + ".enabled"] = m->enabled_ ? "true" : "false";
            for (auto& s : m->settings()) cfg_["mod." + m->id() + "." + s.name] = s.encode();
        }
    }

    EventBus& bus_;
    mutable std::recursive_mutex m_;
    std::string version_;
    std::set<std::string> hooks_;
    std::map<std::string, std::string> failures_;
    std::vector<std::unique_ptr<Module>> mods_;
    std::map<std::string, Module*> byId_;
    std::map<std::string, std::string> cfg_;
};

}  // namespace orbit
