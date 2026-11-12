#pragma once
#include <deque>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "events.hpp"
#include "settings.hpp"

namespace orbit {

enum class Category { Hud, Visual, Utility, Performance, Client };
inline const char* categoryName(Category c) {
    switch (c) { case Category::Hud: return "HUD"; case Category::Visual: return "Visual";
        case Category::Utility: return "Utility"; case Category::Performance: return "Performance"; default: return "Client"; }
}

class Module {
public:
    Module(std::string id, std::string name, std::string desc, Category cat)
        : id_(std::move(id)), name_(std::move(name)), desc_(std::move(desc)), cat_(cat) {}
    virtual ~Module() = default;

    const std::string& id() const { return id_; }
    const std::string& name() const { return name_; }
    const std::string& description() const { return desc_; }
    Category category() const { return cat_; }
    bool enabled() const { return enabled_; }
    const std::string& lastError() const { return error_; }

    /// Game hooks (event sources) this module needs. Unresolved hook => module reported unsupported.
    const std::vector<std::string>& requiredHooks() const { return hooks_; }
    std::deque<Setting>& settings() { return settings_; }
    const std::deque<Setting>& settings() const { return settings_; }
    Setting* setting(const std::string& n) { for (auto& s : settings_) if (s.name == n) return &s; return nullptr; }

    /// Text lines for the HUD renderer. Called from the render thread; implementations must be thread-safe.
    virtual std::vector<std::string> hudLines() const { return {}; }

protected:
    Setting& add(Setting s) { settings_.push_back(std::move(s)); return settings_.back(); }
    void require(std::string hook) { hooks_.push_back(std::move(hook)); }
    virtual void onEnable() {}
    virtual void onDisable() {}

    /// Subscribes while enabled; subscriptions are removed automatically on disable.
    template <class E>
    void listen(std::function<void(const E&)> h) { tokens_.push_back(bus_->subscribe<E>(std::move(h))); }

private:
    friend class ModuleRegistry;
    std::string id_, name_, desc_;
    Category cat_;
    bool enabled_ = false;
    std::string error_;
    std::vector<std::string> hooks_;
    std::deque<Setting> settings_;  // deque: references stay valid as settings are added
    EventBus* bus_ = nullptr;
    std::vector<EventBus::Token> tokens_;
};

}  // namespace orbit
