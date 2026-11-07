package app.orbit.library

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryTest {
    private fun png(w: Int, h: Int, extra: Int = 0): ByteArray {
        val b = ByteBuffer.allocate(33 + extra)
        b.put(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)).putInt(13).putInt(0x49484452).putInt(w).putInt(h)
        return b.array()
    }
    private fun zip(vararg f: Pair<String, String>): ByteArray {
        val o = ByteArrayOutputStream()
        ZipOutputStream(o).use { z -> f.forEach { z.putNextEntry(ZipEntry(it.first)); z.write(it.second.toByteArray()); z.closeEntry() } }
        return o.toByteArray()
    }

    @Test fun pngValidation() {
        assertTrue(Png.isValidSkin(png(64, 64))); assertTrue(Png.isValidSkin(png(64, 32))); assertTrue(Png.isValidSkin(png(128, 128)))
        assertFalse(Png.isValidSkin(png(64, 48))); assertFalse(Png.isValidSkin(png(32, 32)))
        assertFalse(Png.isValidSkin(png(64, 64, extra = 300 * 1024))); assertNull(Png.info(ByteArray(40)))
    }

    @Test fun skinPackRoundTrip() {
        val built = SkinPack.build("My Pack!", listOf(SkinInput("Alex", png(64, 64), true), SkinInput("Steve", png(64, 32), false)))
        val r = SkinPack.read(built)
        assertEquals(2, r.skins.size); assertEquals(0, r.skipped)
        assertTrue(r.skins[0].slim); assertFalse(r.skins[1].slim)
        val info = Packs.inspect(built, "x.mcpack").getOrThrow()
        assertEquals(PackKind.SKIN, info.kind)
    }

    @Test fun skinPackSkipsBadTextures() {
        val z = SkinPack.build("p", listOf(SkinInput("a", png(64, 64), false)))
        assertEquals(1, SkinPack.read(z).skins.size)
        assertThrows(IllegalStateException::class.java) { SkinPack.read(zip("manifest.json" to "{}")) }
    }

    @Test fun inspectsLooseManifestAndAddon() {
        val m = """{ // comment
          "header": {"name":"B","uuid":"u1","version":[1,0,0],},
          "modules":[{"type":"data","uuid":"x","version":[1,0,0]},],}"""
        val r = Packs.inspect(zip("bp/manifest.json" to m), "b.mcpack").getOrThrow()
        assertEquals(PackKind.BEHAVIOR, r.kind); assertEquals("1.0.0", r.packs[0].version)
        val two = Packs.inspect(zip("a/manifest.json" to m, "b/manifest.json" to m.replace("data", "resources")), "x.mcaddon").getOrThrow()
        assertEquals(PackKind.ADDON, two.kind); assertEquals(2, two.packs.size)
    }

    @Test fun rejectsNonPacksAndTraversal() {
        assertTrue(Packs.inspect(zip("readme.txt" to "hi"), "a.mcpack").isFailure)
        assertTrue(Packs.inspect(ByteArray(10), "a.mcpack").isFailure)
        assertTrue(Zip.readSelected(zip("../evil.json" to "{}")) { true }.isEmpty())
    }

    @Test fun worldInspection() {
        assertEquals("My World", Packs.inspectWorld(zip("level.dat" to "x", "levelname.txt" to "My World\n")).getOrThrow().name)
        assertTrue(Packs.inspectWorld(zip("a.txt" to "x")).isFailure)
    }

    @Test fun viewerStateJson() {
        val j = org.json.JSONObject(ViewerState(cosmetics = listOf("hat_crown"), petId = "pet_cat").toJson())
        assertEquals("hat_crown", j.getJSONArray("cosmetics").getString(0)); assertTrue(j.isNull("skin"))
        assertEquals("pet_cat", j.getJSONObject("pet").getString("id"))
    }

    @Test fun catalogIdsUnique() = assertEquals(Catalog.cosmetics.size, Catalog.cosmetics.map { it.id }.toSet().size)

    @Test fun accountApiRejectsHttp() = assertThrows(IllegalArgumentException::class.java) { AccountApi("http://x.com").signIn("a", "b") }
}
