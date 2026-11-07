package app.orbit.core

import org.junit.Assert.*
import org.junit.Test

class HudProfileTest {
    @Test fun hudPersistsClampsAndRestores() {
        val s = InMemoryConfigStore()
        HudConfig(s).update(x = 5f, y = 0.5f, scale = 9f, opacity = 0.1f, color = "#4ADE80")
        val h = HudConfig(s)
        assertEquals(1f, h.x); assertEquals(0.5f, h.y); assertEquals(2f, h.scale); assertEquals(0.2f, h.opacity); assertEquals("#4ADE80", h.color)
        h.restoreDefaults(); assertEquals(0.02f, HudConfig(s).x)
    }

    @Test fun hudRejectsUnknownColor() {
        val h = HudConfig(InMemoryConfigStore()); h.update(color = "#123456"); assertEquals("#EDE7F6", h.color)
    }

    @Test fun profilesSnapshotAndRestore() {
        val core = ClientCore(); val m = core.modules.get("Memory (ORBIT)")!!
        core.modules.setEnabled(m, true); core.modules.saveProfile("Match")
        core.modules.setEnabled(m, false)
        assertTrue(core.modules.loadProfile("Match")); assertTrue(m.enabled)
        assertEquals(listOf("Match"), core.modules.profiles())
        core.modules.deleteProfile("Match"); assertFalse(core.modules.loadProfile("Match"))
    }

    @Test fun badProfileNameRejected() {
        assertThrows(IllegalArgumentException::class.java) { ClientCore().modules.saveProfile("../x") }
    }

    @Test fun favoritesToggle() {
        val core = ClientCore(); val m = core.modules.all.first()
        assertFalse(core.modules.isFavorite(m)); core.modules.toggleFavorite(m); assertTrue(core.modules.isFavorite(m))
    }
}
