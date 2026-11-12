#pragma once
#include <cstdint>
#include <exception>
#include <functional>
#include <mutex>
#include <typeindex>
#include <unordered_map>
#include <vector>

namespace orbit {

/// Typed publish/subscribe bus. A throwing handler never affects other handlers or the caller.
class EventBus {
public:
    using Token = std::uint64_t;
    using ErrorHandler = std::function<void(const char*)>;

    template <class E>
    Token subscribe(std::function<void(const E&)> h) {
        std::lock_guard<std::mutex> l(m_);
        Token id = ++next_;
        map_[std::type_index(typeid(E))].push_back(
            {id, [h = std::move(h)](const void* p) { h(*static_cast<const E*>(p)); }});
        return id;
    }

    void unsubscribe(Token t) {
        std::lock_guard<std::mutex> l(m_);
        for (auto& kv : map_) {
            auto& v = kv.second;
            for (auto it = v.begin(); it != v.end(); ++it)
                if (it->id == t) { v.erase(it); return; }
        }
    }

    template <class E>
    void post(const E& e) {
        std::vector<Entry> copy;
        {
            std::lock_guard<std::mutex> l(m_);
            auto it = map_.find(std::type_index(typeid(E)));
            if (it == map_.end()) return;
            copy = it->second;
        }
        for (auto& h : copy) {
            try { h.fn(&e); }
            catch (const std::exception& ex) { report(ex.what()); }
            catch (...) { report("unknown exception"); }
        }
    }

    void setErrorHandler(ErrorHandler h) { std::lock_guard<std::mutex> l(m_); err_ = std::move(h); }

    template <class E>
    std::size_t subscriberCount() {
        std::lock_guard<std::mutex> l(m_);
        auto it = map_.find(std::type_index(typeid(E)));
        return it == map_.end() ? 0 : it->second.size();
    }

private:
    struct Entry { Token id; std::function<void(const void*)> fn; };
    void report(const char* msg) {
        ErrorHandler h;
        { std::lock_guard<std::mutex> l(m_); h = err_; }
        if (h) h(msg);
    }
    std::mutex m_;
    Token next_ = 0;
    ErrorHandler err_;
    std::unordered_map<std::type_index, std::vector<Entry>> map_;
};

}  // namespace orbit
