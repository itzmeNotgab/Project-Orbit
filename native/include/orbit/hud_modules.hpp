#pragma once
#include <cmath>
#include <cstdio>
#include <deque>
#include <mutex>
#include <string>

#include "game_events.hpp"
#include "module.hpp"
#include "registry.hpp"

namespace orbit::modules {

inline std::string fmt(const char* f, double a) { char b[64]; std::snprintf(b, sizeof b, f, a); return b; }

class FpsCounter : public Module {
public:
    FpsCounter() : Module("fps", "FPS Counter", "Frames rendered in the last second.", Category::Hud) {
        require("render.frame"); showMs_ = &add(Setting::boolean("Show frame time", false)); }
    std::vector<std::string> hudLines() const override {
        std::lock_guard<std::mutex> l(m_);
        if (frames_.size() < 2) return {};
        std::string s = "FPS: " + std::to_string(frames_.size());
        if (showMs_->getBool()) s += " (" + fmt("%.1f", 1000.0 / static_cast<double>(frames_.size())) + " ms)";
        return {s};
    }
protected:
    void onEnable() override { listen<events::FrameEvent>([this](const events::FrameEvent& e) {
        std::lock_guard<std::mutex> l(m_);
        if (!frames_.empty() && e.timeSec < frames_.back()) frames_.clear();  // clock went backwards
        frames_.push_back(e.timeSec);
        while (!frames_.empty() && e.timeSec - frames_.front() >= 1.0) frames_.pop_front();
    }); }
    void onDisable() override { std::lock_guard<std::mutex> l(m_); frames_.clear(); }
private:
    mutable std::mutex m_; std::deque<double> frames_; Setting* showMs_;
};

class Coordinates : public Module {
public:
    Coordinates() : Module("coords", "Coordinates", "Your XYZ position.", Category::Hud) {
        require("player.tick"); dec_ = &add(Setting::number("Decimals", 1, 0, 3)); }
    std::vector<std::string> hudLines() const override {
        std::lock_guard<std::mutex> l(m_);
        if (!has_) return {};
        char f[8]; std::snprintf(f, sizeof f, "%%.%df", static_cast<int>(std::lround(dec_->getFloat())));
        return {"XYZ: " + fmt(f, x_) + ", " + fmt(f, y_) + ", " + fmt(f, z_)};
    }
protected:
    void onEnable() override { listen<events::PlayerTickEvent>([this](const events::PlayerTickEvent& e) {
        std::lock_guard<std::mutex> l(m_); x_ = e.x; y_ = e.y; z_ = e.z; has_ = true; }); }
    void onDisable() override { std::lock_guard<std::mutex> l(m_); has_ = false; }
private:
    mutable std::mutex m_; double x_ = 0, y_ = 0, z_ = 0; bool has_ = false; Setting* dec_;
};

class Compass : public Module {
public:
    Compass() : Module("compass", "Compass", "Direction you are facing.", Category::Hud) {
        require("player.tick"); style_ = &add(Setting::choice("Style", {"Short", "Long"}, "Short")); }
    /// Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 (or -90) = east.
    static std::string direction(float yaw, bool lng) {
        static const char* s[] = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        static const char* l[] = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};
        double y = std::fmod(static_cast<double>(yaw), 360.0); if (y < 0) y += 360.0;
        int i = static_cast<int>(std::floor((y + 22.5) / 45.0)) % 8;
        return lng ? l[i] : s[i];
    }
    std::vector<std::string> hudLines() const override {
        std::lock_guard<std::mutex> l(m_);
        if (!has_) return {};
        return {direction(yaw_, style_->getChoice() == "Long")};
    }
protected:
    void onEnable() override { listen<events::PlayerTickEvent>([this](const events::PlayerTickEvent& e) {
        std::lock_guard<std::mutex> l(m_); yaw_ = e.yaw; has_ = std::isfinite(e.yaw); }); }
    void onDisable() override { std::lock_guard<std::mutex> l(m_); has_ = false; }
private:
    mutable std::mutex m_; float yaw_ = 0; bool has_ = false; Setting* style_;
};

class SpeedDisplay : public Module {
public:
    SpeedDisplay() : Module("speed", "Speed Display", "Horizontal movement speed.", Category::Hud) {
        require("player.tick"); unit_ = &add(Setting::choice("Unit", {"blocks/s", "km/h"}, "blocks/s")); }
    std::vector<std::string> hudLines() const override {
        std::lock_guard<std::mutex> l(m_);
        if (!hasSpeed_) return {};
        bool kmh = unit_->getChoice() == "km/h";
        return {"Speed: " + fmt("%.2f", kmh ? bps_ * 3.6 : bps_) + (kmh ? " km/h" : " b/s")};
    }
protected:
    void onEnable() override { listen<events::PlayerTickEvent>([this](const events::PlayerTickEvent& e) {
        std::lock_guard<std::mutex> l(m_);
        if (hasPrev_) {
            double dt = e.timeSec - pt_, d = std::hypot(e.x - px_, e.z - pz_);
            if (dt > 0 && d < 50.0) { bps_ = d / dt; hasSpeed_ = true; }  // >50 blocks in one tick = teleport, ignore
        }
        px_ = e.x; pz_ = e.z; pt_ = e.timeSec; hasPrev_ = true; }); }
    void onDisable() override { std::lock_guard<std::mutex> l(m_); hasPrev_ = hasSpeed_ = false; }
private:
    mutable std::mutex m_; double px_ = 0, pz_ = 0, pt_ = 0, bps_ = 0; bool hasPrev_ = false, hasSpeed_ = false; Setting* unit_;
};

class WorldTime : public Module {
public:
    WorldTime() : Module("worldtime", "World Time", "In-game clock.", Category::Hud) {
        require("world.time"); fmt_ = &add(Setting::choice("Format", {"24h", "12h"}, "24h")); }
    /// Tick 0 = 06:00. 1000 ticks = 1 hour.
    static std::string format(std::int64_t ticks, bool h12) {
        std::int64_t t = ((ticks % 24000) + 24000) % 24000;
        int h = static_cast<int>((t / 1000 + 6) % 24), m = static_cast<int>((t % 1000) * 60 / 1000);
        char b[32];
        if (!h12) std::snprintf(b, sizeof b, "%02d:%02d", h, m);
        else std::snprintf(b, sizeof b, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
        return b;
    }
    std::vector<std::string> hudLines() const override {
        std::lock_guard<std::mutex> l(m_);
        if (!has_) return {};
        return {"Time: " + format(ticks_, fmt_->getChoice() == "12h")};
    }
protected:
    void onEnable() override { listen<events::WorldTimeEvent>([this](const events::WorldTimeEvent& e) {
        std::lock_guard<std::mutex> l(m_); ticks_ = e.ticks; has_ = true; }); }
    void onDisable() override { std::lock_guard<std::mutex> l(m_); has_ = false; }
private:
    mutable std::mutex m_; std::int64_t ticks_ = 0; bool has_ = false; Setting* fmt_;
};

class Keystrokes : public Module {
public:
    Keystrokes() : Module("keys", "Keystrokes", "Shows which movement keys are held.", Category::Hud) { require("input.key"); }
    std::vector<std::string> hudLines() const override {
        std::lock_guard<std::mutex> l(m_);
        auto k = [&](char c, const char* label) { return std::string(down_[idx(c)] ? "[" : " ") + label + (down_[idx(c)] ? "]" : " "); };
        return {"  " + k('W', "W"), k('A', "A") + k('S', "S") + k('D', "D"), k(' ', "_")};
    }
protected:
    void onEnable() override { listen<events::KeyEvent>([this](const events::KeyEvent& e) {
        int i = idx(e.key); if (i < 0) return;
        std::lock_guard<std::mutex> l(m_); down_[i] = e.down; }); }
    void onDisable() override { std::lock_guard<std::mutex> l(m_); for (auto& d : down_) d = false; }
private:
    static int idx(char c) { switch (c) { case 'W': return 0; case 'A': return 1; case 'S': return 2; case 'D': return 3; case ' ': return 4; } return 5; }
    mutable std::mutex m_; bool down_[6] = {false, false, false, false, false, false};
};

class PingDisplay : public Module {
public:
    PingDisplay() : Module("ping", "Ping Display", "Latency to the server.", Category::Hud) { require("net.ping"); }
    std::vector<std::string> hudLines() const override {
        std::lock_guard<std::mutex> l(m_);
        if (ms_ < 0) return {};
        return {"Ping: " + std::to_string(ms_) + " ms"};
    }
protected:
    void onEnable() override { listen<events::PingEvent>([this](const events::PingEvent& e) {
        std::lock_guard<std::mutex> l(m_); ms_ = e.ms >= 0 ? e.ms : -1; }); }
    void onDisable() override { std::lock_guard<std::mutex> l(m_); ms_ = -1; }
private:
    mutable std::mutex m_; int ms_ = -1;
};

inline void registerHud(ModuleRegistry& r) {
    r.add(std::make_unique<FpsCounter>()); r.add(std::make_unique<Coordinates>()); r.add(std::make_unique<Compass>());
    r.add(std::make_unique<SpeedDisplay>()); r.add(std::make_unique<WorldTime>()); r.add(std::make_unique<Keystrokes>());
    r.add(std::make_unique<PingDisplay>());
}

}  // namespace orbit::modules
