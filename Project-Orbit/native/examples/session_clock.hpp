#pragma once
// Example third-party module written against the ORBIT SDK headers only.
// Shows: id/name/category, a setting, a required hook, an event subscription, thread-safe HUD output.
#include <mutex>
#include "orbit/game_events.hpp"
#include "orbit/module.hpp"

class SessionClock : public orbit::Module {
public:
    SessionClock() : Module("session_clock", "Session Clock", "Time since you enabled it.", orbit::Category::Hud) {
        require("render.frame");                       // event source this module depends on
        showSeconds_ = &add(orbit::Setting::boolean("Show seconds", true));
    }
    std::vector<std::string> hudLines() const override {
        std::lock_guard<std::mutex> l(m_);
        if (!started_) return {};
        long t = static_cast<long>(now_ - start_);
        char b[96];
        if (showSeconds_->getBool()) std::snprintf(b, sizeof b, "Session: %ld:%02ld", t / 60, t % 60);
        else std::snprintf(b, sizeof b, "Session: %ld min", t / 60);
        return {b};
    }
protected:
    void onEnable() override {
        listen<orbit::events::FrameEvent>([this](const orbit::events::FrameEvent& e) {
            std::lock_guard<std::mutex> l(m_);
            if (!started_) { start_ = e.timeSec; started_ = true; }
            now_ = e.timeSec;
        });
    }
    void onDisable() override { std::lock_guard<std::mutex> l(m_); started_ = false; }
private:
    mutable std::mutex m_;
    double start_ = 0, now_ = 0; bool started_ = false;
    orbit::Setting* showSeconds_;
};
