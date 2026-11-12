#pragma once
#include <cctype>
#include <cstddef>
#include <cstdlib>
#include <cstdint>
#include <map>
#include <set>
#include <string>
#include <vector>

namespace orbit {

/// Byte-pattern scanner for validating hooks against the loaded game library, e.g. "FD 7B ?? A9 ?? ?? 00 94".
class SignatureScanner {
public:
    /// Returns an empty vector for a malformed pattern.
    static std::vector<int> parse(const std::string& pattern) {
        std::vector<int> out;
        std::size_t i = 0;
        while (i < pattern.size()) {
            if (pattern[i] == ' ') { ++i; continue; }
            std::size_t j = pattern.find(' ', i); if (j == std::string::npos) j = pattern.size();
            std::string tok = pattern.substr(i, j - i);
            if (tok == "?" || tok == "??") out.push_back(-1);
            else if (tok.size() == 2 && std::isxdigit(static_cast<unsigned char>(tok[0])) && std::isxdigit(static_cast<unsigned char>(tok[1])))
                out.push_back(static_cast<int>(std::strtol(tok.c_str(), nullptr, 16)));
            else return {};
            i = j;
        }
        return out;
    }

    /// Up to `limit` match offsets.
    static std::vector<std::size_t> find(const std::uint8_t* data, std::size_t size, const std::vector<int>& pat, std::size_t limit = 2) {
        std::vector<std::size_t> hits;
        if (pat.empty() || pat.size() > size) return hits;
        for (std::size_t i = 0; i + pat.size() <= size && hits.size() < limit; ++i) {
            bool ok = true;
            for (std::size_t k = 0; k < pat.size(); ++k) if (pat[k] >= 0 && data[i + k] != pat[k]) { ok = false; break; }
            if (ok) hits.push_back(i);
        }
        return hits;
    }
};

struct HookSpec { std::string name; std::string pattern; std::ptrdiff_t offset = 0; };

struct ResolveResult {
    std::map<std::string, std::size_t> resolved;     // hook name -> offset into the scanned image
    std::map<std::string, std::string> failures;     // hook name -> reason
    std::set<std::string> names() const { std::set<std::string> s; for (auto& kv : resolved) s.insert(kv.first); return s; }
};

/// A hook only counts as resolved if its signature matches exactly once. Never guesses.
inline ResolveResult resolveHooks(const std::uint8_t* image, std::size_t size, const std::vector<HookSpec>& specs) {
    ResolveResult r;
    for (auto& s : specs) {
        auto pat = SignatureScanner::parse(s.pattern);
        if (pat.empty()) { r.failures[s.name] = "malformed signature"; continue; }
        auto hits = SignatureScanner::find(image, size, pat, 2);
        if (hits.empty()) r.failures[s.name] = "signature not found";
        else if (hits.size() > 1) r.failures[s.name] = "signature is ambiguous (matches more than once)";
        else r.resolved[s.name] = static_cast<std::size_t>(static_cast<std::ptrdiff_t>(hits[0]) + s.offset);
    }
    return r;
}

}  // namespace orbit

#include "json.hpp"
namespace orbit {

/// Per-game-version hook data. Flat JSON: {"version":"1.26.60.4","hook.player.tick":"FD 7B ?? A9|0", ...}
/// A profile only applies to the exact game version it names.
struct CompatProfile {
    std::string version;
    std::vector<HookSpec> hooks;

    static bool fromJson(const std::string& text, CompatProfile& out) {
        std::map<std::string, std::string> m;
        if (!json::read(text, m) || !m.count("version") || m["version"].empty()) return false;
        CompatProfile p; p.version = m["version"];
        for (auto& [k, v] : m) {
            if (k.rfind("hook.", 0) != 0 || k.size() <= 5) continue;
            HookSpec h; h.name = k.substr(5);
            auto bar = v.rfind('|');
            h.pattern = bar == std::string::npos ? v : v.substr(0, bar);
            if (bar != std::string::npos) h.offset = std::strtol(v.c_str() + bar + 1, nullptr, 10);
            p.hooks.push_back(std::move(h));
        }
        out = std::move(p); return true;
    }

    /// Resolves against a loaded image. On a version mismatch every hook fails with a clear reason.
    ResolveResult resolve(const std::string& gameVersion, const std::uint8_t* image, std::size_t size) const {
        if (gameVersion != version) {
            ResolveResult r;
            for (auto& h : hooks) r.failures[h.name] = "profile is for " + version + ", game is " + gameVersion;
            return r;
        }
        return resolveHooks(image, size, hooks);
    }
};

}  // namespace orbit
