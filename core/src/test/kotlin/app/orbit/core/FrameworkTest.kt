package app.orbit.core

import app.orbit.core.modules.NetworkStats
import app.orbit.core.modules.SessionTimer
import org.junit.Assert.*
import org.junit.Test

class FrameworkTest {
    private class Boom : Module("Boom", "fails", Category.UTILITY) { override fun onEnable() = error("nope") }
    private class Incompat : Module("Inc", "x", Category.UTILITY) { override fun compatibilityError() = "needs newer game" }
    private class Cfg : Module("Cfg", "x", Category.CLIENT) {
        val f = float("v", 5f, 0f, 10f); val c = choice("c", listOf("a", "b"), "a"); val b = bool("b", false)
    }

    @Test fun busDeliversUnsubscribesAndIsolatesErrors() {
        val errors = mutableListOf<Throwable>()
        val bus = EventBus { errors += it }
        val got = mutableListOf<String>()
        bus.subscribe(String::class.java) { error("bad handler") }
        val sub = bus.subscribe(String::class.java) { got += it }
        bus.post("a"); sub.cancel(); bus.post("b")
        assertEquals(listOf("a"), got); assertEquals(2, errors.size)
    }

    @Test fun stickyReplay() {
        val bus = EventBus(); bus.postSticky("last")
        val got = mutableListOf<String>()
        bus.subscribe(String::class.java, replay = true) { got += it }
        assertEquals(listOf("last"), got)
    }

    @Test fun duplicateRegistrationRejected() {
        val m = ModuleManager(EventBus(), InMemoryConfigStore())
        m.register(Cfg())
        assertThrows(IllegalArgumentException::class.java) { m.register(Cfg()) }
    }

    @Test fun failingModuleStaysDisabledWithError() {
        val m = ModuleManager(EventBus(), InMemoryConfigStore()); val b = Boom(); m.register(b)
        assertFalse(m.setEnabled(b, true)); assertFalse(b.enabled); assertEquals("nope", b.lastError)
    }

    @Test fun incompatibleModuleRefused() {
        val m = ModuleManager(EventBus(), InMemoryConfigStore()); val i = Incompat(); m.register(i)
        assertFalse(m.setEnabled(i, true)); assertEquals("needs newer game", i.lastError)
    }

    @Test fun enabledStateAndSettingsPersist() {
        val store = InMemoryConfigStore()
        val m1 = ModuleManager(EventBus(), store); val a = Cfg(); m1.register(a)
        m1.setEnabled(a, true); a.f.value = 7f; a.c.value = "b"; a.b.value = true; m1.save()
        val m2 = ModuleManager(EventBus(), store); val b = Cfg(); m2.register(b)
        assertTrue(b.enabled); assertEquals(7f, b.f.value); assertEquals("b", b.c.value); assertTrue(b.b.value)
    }

    @Test fun corruptConfigIsIgnoredAndClamped() {
        val store = InMemoryConfigStore()
        store.save(mapOf("module.Cfg.v" to "999", "module.Cfg.c" to "zzz", "module.Cfg.b" to "maybe"))
        val m = ModuleManager(EventBus(), store); val c = Cfg(); m.register(c)
        assertEquals(10f, c.f.value); assertEquals("a", c.c.value); assertFalse(c.b.value)
    }

    @Test fun networkStatsComputesThroughput() {
        val core = ClientCore(); val ns = core.modules.get("Network Stats")!! as NetworkStats
        core.modules.setEnabled(ns, true)
        core.bus.post(NetworkSample(1000, 1, 10, 10, 0, 0))
        core.bus.post(NetworkSample(3000, 1, 30, 30, 2048, 4096))
        val lines = ns.hudLines()
        assertEquals("Packets ↑30 ↓30", lines[0]); assertEquals("Rate ↑1.0 ↓2.0 KB/s", lines[1])
        core.modules.setEnabled(ns, false)
        core.bus.post(NetworkSample(4000, 1, 99, 99, 9, 9))
        assertEquals("Packets ↑30 ↓30", ns.hudLines()[0])
    }

    @Test fun uptimeUsesStickyEngineState() {
        val core = ClientCore(); core.setEngineState(EngineState.Running)
        val t = core.modules.get("Relay Uptime")!! as SessionTimer
        core.modules.setEnabled(t, true)
        t.nowMs = { System.currentTimeMillis() + 65_000 }
        assertTrue(t.hudLines()[0].startsWith("Relay 1:0"))
        core.setEngineState(EngineState.Stopped)
        assertTrue(t.hudLines().isEmpty())
    }
}
