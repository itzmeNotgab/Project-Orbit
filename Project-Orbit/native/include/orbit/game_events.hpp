#pragma once
#include <cstdint>

namespace orbit::events {
/// Produced by game hooks (version specific). Modules only consume these, never touch game memory.
struct FrameEvent { double timeSec; };
struct PlayerTickEvent { double x, y, z; float yaw; double timeSec; };
struct WorldTimeEvent { std::int64_t ticks; };
struct KeyEvent { char key; bool down; };   // 'W','A','S','D',' ' (space)
struct PingEvent { int ms; };
}  // namespace orbit::events
