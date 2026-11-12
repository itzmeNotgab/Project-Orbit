#pragma once
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

namespace orbit {

enum class NoteLevel { Info, Success, Warning, Error };

struct Notification { std::string text; NoteLevel level; std::int64_t expiresAtMs; };

/// In-game toast queue. Time is passed in, so behaviour is deterministic and testable.
class NotificationQueue {
public:
    void post(std::string text, NoteLevel level, std::int64_t nowMs, std::int64_t durationMs = 3000) {
        std::lock_guard<std::mutex> l(m_);
        q_.push_back({std::move(text), level, nowMs + durationMs});
    }
    /// Non-expired notifications, oldest first. Expired ones are dropped.
    std::vector<Notification> active(std::int64_t nowMs) {
        std::lock_guard<std::mutex> l(m_);
        while (!q_.empty() && q_.front().expiresAtMs <= nowMs) q_.pop_front();
        std::vector<Notification> v;
        for (auto& n : q_) if (n.expiresAtMs > nowMs) v.push_back(n);
        return v;
    }
private:
    std::mutex m_;
    std::deque<Notification> q_;
};

struct ThemePreset { const char* name; std::uint32_t accent, background, surface; };

/// Same palettes as the launcher so the in-game menu matches.
inline const ThemePreset* themePresets(std::size_t& count) {
    static const ThemePreset p[] = {
        {"Nebula", 0xFFA855F7u, 0xFF0A0612u, 0xFF130C20u}, {"Ion", 0xFF60A5FAu, 0xFF060B14u, 0xFF0D1626u},
        {"Aurora", 0xFF4ADE80u, 0xFF06100Bu, 0xFF0D1F16u}, {"Ember", 0xFFFB923Cu, 0xFF120A06u, 0xFF1F130Cu},
    };
    count = sizeof p / sizeof p[0]; return p;
}

}  // namespace orbit
