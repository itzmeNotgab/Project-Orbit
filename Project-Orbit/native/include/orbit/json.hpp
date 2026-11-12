#pragma once
#include <cstdio>
#include <map>
#include <string>

namespace orbit::json {

inline std::string escape(const std::string& s) {
        std::string o;
        for (unsigned char ch : s) {
            switch (ch) {
                case '"': o += "\\\""; break; case '\\': o += "\\\\"; break;
                case '\n': o += "\\n"; break; case '\t': o += "\\t"; break; case '\r': o += "\\r"; break;
                default: if (ch < 0x20) { char b[8]; std::snprintf(b, sizeof b, "\\u%04x", ch); o += b; } else o += static_cast<char>(ch);
            }
        }
        return o;
}

/// Flat string->string JSON objects only (all ORBIT config values are stored as strings).
inline std::string write(const std::map<std::string, std::string>& m) {
    auto esc = [](const std::string& s) { return escape(s); };
    std::string out = "{";
    bool first = true;
    for (auto& [k, v] : m) { if (!first) out += ","; first = false; out += "\n  \"" + esc(k) + "\": \"" + esc(v) + "\""; }
    return out + (m.empty() ? "}" : "\n}");
}

inline bool read(const std::string& s, std::map<std::string, std::string>& out) {
    std::size_t i = 0;
    auto ws = [&] { while (i < s.size() && (s[i] == ' ' || s[i] == '\n' || s[i] == '\t' || s[i] == '\r')) ++i; };
    auto str = [&](std::string& r) -> bool {
        if (i >= s.size() || s[i] != '"') return false;
        ++i; r.clear();
        while (i < s.size() && s[i] != '"') {
            char ch = s[i++];
            if (ch != '\\') { r += ch; continue; }
            if (i >= s.size()) return false;
            char e = s[i++];
            switch (e) {
                case '"': r += '"'; break; case '\\': r += '\\'; break; case '/': r += '/'; break;
                case 'n': r += '\n'; break; case 't': r += '\t'; break; case 'r': r += '\r'; break;
                case 'u': {
                    if (i + 4 > s.size()) return false;
                    unsigned v = 0;
                    for (int k = 0; k < 4; ++k) {
                        char h = s[i++]; v <<= 4;
                        if (h >= '0' && h <= '9') v |= h - '0'; else if (h >= 'a' && h <= 'f') v |= h - 'a' + 10;
                        else if (h >= 'A' && h <= 'F') v |= h - 'A' + 10; else return false;
                    }
                    if (v > 0x7F) return false;  // flat config is ASCII-escaped by write()
                    r += static_cast<char>(v); break; }
                default: return false;
            }
        }
        if (i >= s.size()) return false;
        ++i; return true;
    };
    std::map<std::string, std::string> tmp;
    ws(); if (i >= s.size() || s[i++] != '{') return false;
    ws();
    if (i < s.size() && s[i] == '}') { out = tmp; return true; }
    while (true) {
        ws(); std::string k, v;
        if (!str(k)) return false;
        ws(); if (i >= s.size() || s[i++] != ':') return false;
        ws(); if (!str(v)) return false;
        tmp[k] = v;
        ws(); if (i >= s.size()) return false;
        if (s[i] == ',') { ++i; continue; }
        if (s[i] == '}') { ++i; break; }
        return false;
    }
    ws(); if (i != s.size()) return false;
    out = std::move(tmp); return true;
}

}  // namespace orbit::json
