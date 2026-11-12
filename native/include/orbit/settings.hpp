#pragma once
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

namespace orbit {

enum class SettingKind { Bool, Float, Choice, Color };

/// One configurable value. decode() validates and returns false (leaving the value unchanged) on bad input.
class Setting {
public:
    static Setting boolean(std::string n, bool d) { Setting s(std::move(n), SettingKind::Bool); s.b = d; s.defB = d; return s; }
    static Setting number(std::string n, float d, float lo, float hi) {
        Setting s(std::move(n), SettingKind::Float); s.lo = lo; s.hi = hi; s.f = s.defF = std::clamp(d, lo, hi); return s; }
    static Setting choice(std::string n, std::vector<std::string> opts, std::string d) {
        Setting s(std::move(n), SettingKind::Choice); s.options = std::move(opts);
        if (std::find(s.options.begin(), s.options.end(), d) == s.options.end() && !s.options.empty()) d = s.options[0];
        s.c = s.defC = std::move(d); return s; }
    static Setting color(std::string n, std::uint32_t argb) { Setting s(std::move(n), SettingKind::Color); s.argb = s.defArgb = argb; return s; }

    const std::string name;
    const SettingKind kind;
    float lo = 0, hi = 0;
    std::vector<std::string> options;

    bool getBool() const { return b; }
    float getFloat() const { return f; }
    const std::string& getChoice() const { return c; }
    std::uint32_t getColor() const { return argb; }

    void setBool(bool v) { b = v; }
    void setFloat(float v) { if (std::isfinite(v)) f = std::clamp(v, lo, hi); }
    bool setChoice(const std::string& v) { if (std::find(options.begin(), options.end(), v) == options.end()) return false; c = v; return true; }
    void setColor(std::uint32_t v) { argb = v; }
    void reset() { b = defB; f = defF; c = defC; argb = defArgb; }

    std::string encode() const {
        switch (kind) {
            case SettingKind::Bool: return b ? "true" : "false";
            case SettingKind::Float: { char buf[32]; std::snprintf(buf, sizeof buf, "%g", f); return buf; }
            case SettingKind::Choice: return c;
            case SettingKind::Color: { char buf[16]; std::snprintf(buf, sizeof buf, "#%08X", argb); return buf; }
        }
        return {};
    }

    bool decode(const std::string& s) {
        switch (kind) {
            case SettingKind::Bool: if (s == "true") b = true; else if (s == "false") b = false; else return false; return true;
            case SettingKind::Float: {
                char* end = nullptr; float v = std::strtof(s.c_str(), &end);
                if (s.empty() || end != s.c_str() + s.size() || !std::isfinite(v)) return false;
                f = std::clamp(v, lo, hi); return true; }
            case SettingKind::Choice: return setChoice(s);
            case SettingKind::Color: {
                if (s.size() != 9 || s[0] != '#') return false;
                for (std::size_t i = 1; i < 9; ++i) if (!std::isxdigit(static_cast<unsigned char>(s[i]))) return false;
                argb = static_cast<std::uint32_t>(std::strtoul(s.c_str() + 1, nullptr, 16)); return true; }
        }
        return false;
    }

private:
    Setting(std::string n, SettingKind k) : name(std::move(n)), kind(k) {}
    bool b = false, defB = false;
    float f = 0, defF = 0;
    std::string c, defC;
    std::uint32_t argb = 0xFFFFFFFF, defArgb = 0xFFFFFFFF;
};

}  // namespace orbit
