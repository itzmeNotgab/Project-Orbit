package app.orbit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientCoreTest {
    @Test fun startsStopped() = assertEquals(EngineState.Stopped, ClientCore().engine.value)

    @Test fun stateUpdatesAreObservable() {
        val core = ClientCore()
        core.setEngineState(EngineState.Failed)
        assertEquals(EngineState.Failed, core.engine.value)
    }
}
