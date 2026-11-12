# Writing an ORBIT module

See `native/examples/session_clock.hpp` (tested in `native/tests/test_capi.cpp`).

1. Derive from `orbit::Module(id, name, description, category)`.
2. Declare settings with `add(Setting::boolean/number/choice/color(...))`. Values are validated and persisted automatically.
3. Declare each event source you need with `require("hook.name")`. If the runtime could not verify that hook on the current
   game version, the module refuses to enable and the UI shows why. Never read game memory directly.
4. In `onEnable()` call `listen<EventType>(handler)`. Subscriptions are removed automatically on disable. Throwing is safe:
   the module is left disabled and `lastError()` holds the message.
5. For HUD modules return text from `hudLines()`. It is called from the render thread, so guard shared state with a mutex.
6. Register with `registry.add(std::make_unique<YourModule>())`.

Event types: `native/include/orbit/game_events.hpp`. To add a new event kind, add a struct there and a hook that posts it.
