package app.orbit.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle of the (not yet implemented) networking engine. Phase 2 adds Starting/Running. */
enum class EngineState { Stopped, Starting, Running, Failed }

/**
 * Root object of ORBIT's client logic. Phase 1 only owns engine state;
 * the relay, modules and events are attached in later phases.
 */
class ClientCore(store: ConfigStore = InMemoryConfigStore()) {
    val hud = HudConfig(store)
    val bus = EventBus()
    val modules = ModuleManager(bus, store)
    init { app.orbit.core.modules.registerBuiltins(modules) }

    private val _engine = MutableStateFlow(EngineState.Stopped)
    val engine: StateFlow<EngineState> = _engine.asStateFlow()

    /** Set by the network layer once it exists. */
    fun setEngineState(state: EngineState) {
        if (_engine.value == state) return
        _engine.value = state
        bus.postSticky(EngineStateChanged(state, System.currentTimeMillis()))
    }
}
