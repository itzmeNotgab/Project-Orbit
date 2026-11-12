#pragma once
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <map>
#include <string>
#include <vector>

#include "registry.hpp"

namespace orbit {

struct HudElement {
    std::string id;
    float x = 0.02f, y = 0.05f;      // top-left, fraction of the screen (resolution and aspect independent)
    float w = 0.f, h = 0.f;          // size as a fraction of the screen, measured and reported by the renderer
    float scale = 1.f, opacity = 0.9f;
    std::uint32_t color = 0xFFEDE7F6u;
    bool visible = true, locked = false;
};

/// What the renderer draws: one block of text per visible, enabled HUD module.
struct HudDrawCommand { std::string id; std::vector<std::string> lines; float x, y, scale, opacity; std::uint32_t color; bool locked; };

class HudLayout {
public:
    static constexpr float kMinScale = 0.25f, kMaxScale = 4.f;   // technical bounds: zero/negative or absurd sizes cannot render

    explicit HudLayout(ModuleRegistry& r) : reg_(r) {}

    /// New elements are stacked so they do not overlap by default. There is no limit on the number of elements.
    HudElement& ensure(const std::string& id) {
        for (auto& e : els_) if (e.id == id) return e;
        HudElement e; e.id = id; e.y = 0.05f + 0.06f * static_cast<float>(els_.size());
        els_.push_back(e); return els_.back();
    }
    HudElement* find(const std::string& id) { for (auto& e : els_) if (e.id == id) return &e; return nullptr; }
    const std::vector<HudElement>& elements() const { return els_; }

    bool update(const std::string& id, float x, float y, float scale, float opacity, std::uint32_t color, bool visible) {
        if (!std::isfinite(x) || !std::isfinite(y) || !std::isfinite(scale) || !std::isfinite(opacity)) return false;
        HudElement* e = find(id); if (!e) return false;
        e->x = std::clamp(x, 0.f, 1.f); e->y = std::clamp(y, 0.f, 1.f);
        e->scale = std::clamp(scale, kMinScale, kMaxScale); e->opacity = std::clamp(opacity, 0.f, 1.f);
        e->color = color; e->visible = visible; save(*e); return true;
    }

    bool move(const std::string& id, float x, float y) {
        HudElement* e = find(id); return e && update(id, x, y, e->scale, e->opacity, e->color, e->visible);
    }

    bool setLocked(const std::string& id, bool v) { HudElement* e = find(id); if (!e) return false; e->locked = v; save(*e); return true; }
    void setSize(const std::string& id, float w, float h) { if (HudElement* e = find(id)) if (std::isfinite(w) && std::isfinite(h)) { e->w = std::max(0.f, w); e->h = std::max(0.f, h); } }

    void resetAll() {
        std::size_t i = 0;
        for (auto& e : els_) { HudElement d; d.id = e.id; d.y = 0.05f + 0.06f * static_cast<float>(i++); d.w = e.w; d.h = e.h; e = d; save(e); }
    }

    void load() {
        for (auto& e : els_) {
            auto g = [&](const char* f) { return reg_.getRaw("hud." + e.id + "." + f); };
            auto num = [&](const char* f, float& dst, float lo, float hi) {
                std::string s = g(f); if (s.empty()) return;
                char* end = nullptr; float v = std::strtof(s.c_str(), &end);
                if (end == s.c_str() + s.size() && std::isfinite(v)) dst = std::clamp(v, lo, hi); };
            num("x", e.x, 0, 1); num("y", e.y, 0, 1); num("scale", e.scale, kMinScale, kMaxScale); num("opacity", e.opacity, 0, 1);
            std::string c = g("color");
            if (c.size() == 9 && c[0] == '#') { bool ok = true; for (std::size_t i = 1; i < 9; ++i) ok = ok && std::isxdigit(static_cast<unsigned char>(c[i]));
                if (ok) e.color = static_cast<std::uint32_t>(std::strtoul(c.c_str() + 1, nullptr, 16)); }
            std::string v = g("visible"); if (v == "true" || v == "false") e.visible = v == "true";
            std::string l = g("locked"); if (l == "true" || l == "false") e.locked = l == "true";
        }
    }

    /// In edit mode every enabled HUD module is shown (with a placeholder when it has no data yet) so it can be moved.
    std::vector<HudDrawCommand> frame(bool editing = false) {
        std::vector<HudDrawCommand> out;
        for (Module* m : reg_.all()) {
            if (m->category() != Category::Hud || !m->enabled()) continue;
            auto& e = ensure(m->id());
            if (!e.visible && !editing) continue;
            auto lines = m->hudLines();
            if (lines.empty()) { if (!editing) continue; lines = {"[" + m->name() + "]"}; }
            out.push_back({e.id, std::move(lines), e.x, e.y, e.scale, e.opacity, e.color, e.locked});
        }
        return out;
    }

private:
    void save(const HudElement& e) {
        char c[16]; std::snprintf(c, sizeof c, "#%08X", e.color);
        auto k = [&](const char* f) { return "hud." + e.id + "." + f; };
        reg_.setRaw(k("x"), std::to_string(e.x)); reg_.setRaw(k("y"), std::to_string(e.y));
        reg_.setRaw(k("scale"), std::to_string(e.scale)); reg_.setRaw(k("opacity"), std::to_string(e.opacity));
        reg_.setRaw(k("color"), c); reg_.setRaw(k("visible"), e.visible ? "true" : "false"); reg_.setRaw(k("locked"), e.locked ? "true" : "false");
    }
    ModuleRegistry& reg_;
    std::vector<HudElement> els_;
};

struct Guide { bool vertical; float pos; };   // vertical: line at x = pos; horizontal: line at y = pos
struct DragResult { bool moved; float x, y; std::vector<Guide> guides; };

/// HUD Edit Mode logic. Drag/resize/lock/visibility/reset/done, with snapping and alignment guides.
/// All geometry is normalized, so layouts survive any resolution or aspect ratio. Drawing is up to the renderer.
class HudEditor {
public:
    explicit HudEditor(HudLayout& l) : layout_(l) {}
    bool editing() const { return editing_; }
    void begin() { editing_ = true; }
    /// Positions are saved on every change, so Done only leaves edit mode.
    void done() { editing_ = false; }

    DragResult drag(const std::string& id, float x, float y, float threshold = 0.012f) {
        HudElement* e = layout_.find(id);
        if (!editing_ || !e || e->locked || !std::isfinite(x) || !std::isfinite(y)) return {false, e ? e->x : 0.f, e ? e->y : 0.f, {}};
        float maxX = std::max(0.f, 1.f - e->w), maxY = std::max(0.f, 1.f - e->h);
        x = std::clamp(x, 0.f, maxX); y = std::clamp(y, 0.f, maxY);
        std::vector<Guide> guides;
        auto snapAxis = [&](float pos, float size, bool vertical, float& out) {
            // candidate (reference line, offset of the element's own edge/center from pos)
            std::vector<float> lines{0.f, 0.5f, 1.f};
            for (auto& o : layout_.elements()) if (o.id != id) {
                float op = vertical ? o.x : o.y, os = vertical ? o.w : o.h;
                lines.push_back(op); lines.push_back(op + os * 0.5f); lines.push_back(op + os);
            }
            const float own[3] = {0.f, size * 0.5f, size};
            float best = threshold + 1.f, bestShift = 0.f, bestLine = 0.f; bool found = false;
            for (float ln : lines) for (float off : own) {
                float d = ln - (pos + off);
                if (std::fabs(d) <= threshold && std::fabs(d) < best) { best = std::fabs(d); bestShift = d; bestLine = ln; found = true; }
            }
            out = found ? pos + bestShift : pos;
            if (found) guides.push_back({vertical, bestLine});
        };
        float sx = x, sy = y;
        snapAxis(x, e->w, true, sx); snapAxis(y, e->h, false, sy);
        sx = std::clamp(sx, 0.f, maxX); sy = std::clamp(sy, 0.f, maxY);
        layout_.move(id, sx, sy);
        return {true, sx, sy, guides};
    }

    bool resize(const std::string& id, float scale) {
        HudElement* e = layout_.find(id);
        if (!editing_ || !e || e->locked) return false;
        return layout_.update(id, e->x, e->y, scale, e->opacity, e->color, e->visible);
    }
    bool setLocked(const std::string& id, bool v) { return editing_ && layout_.setLocked(id, v); }
    bool setVisible(const std::string& id, bool v) {
        HudElement* e = layout_.find(id);
        return editing_ && e && !e->locked && layout_.update(id, e->x, e->y, e->scale, e->opacity, e->color, v);
    }
    bool setColor(const std::string& id, std::uint32_t argb, float opacity) {
        HudElement* e = layout_.find(id);
        return editing_ && e && !e->locked && layout_.update(id, e->x, e->y, e->scale, opacity, argb, e->visible);
    }
    void resetLayout() { if (editing_) layout_.resetAll(); }

private:
    HudLayout& layout_;
    bool editing_ = false;
};

}  // namespace orbit
