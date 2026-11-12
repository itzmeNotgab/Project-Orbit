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
    private fun zip(vararg f: Pair<String, ByteArray>): ByteArray {
        val o = ByteArrayOutputStream()
        ZipOutputStream(o).use { z -> f.forEach { z.putNextEntry(ZipEntry(it.first)); z.write(it.second); z.closeEntry() } }
        return o.toByteArray()
    }
    private fun zipT(vararg f: Pair<String, String>) = zip(*f.map { it.first to it.second.toByteArray() }.toTypedArray())
    private val BP = """{ // comment
          "header": {"name":"B","uuid":"u1","version":[1,0,0],"min_engine_version":[1,21,0]},
          "modules":[{"type":"data","uuid":"x","version":[1,0,0]},],}"""

    @Test fun pngValidationHasNoSizeCap() {
        assertTrue(Png.isValidSkin(png(64, 64))); assertTrue(Png.isValidSkin(png(64, 32))); assertTrue(Png.isValidSkin(png(128, 128)))
        assertTrue(Png.isValidSkin(png(64, 64, extra = 600 * 1024)))
        assertFalse(Png.isValidSkin(png(64, 48))); assertFalse(Png.isValidSkin(png(32, 32))); assertFalse(Png.isValidSkin(png(100, 100)))
        assertNull(Png.info(ByteArray(40)))
        assertTrue(Png.isValidCape(png(64, 32))); assertFalse(Png.isValidCape(png(32, 64)))
    }

    @Test fun skinPackRoundTrip() {
        val built = SkinPack.build("My Pack!", listOf(SkinInput("Alex", png(64, 64), true), SkinInput("Steve", png(64, 32), false)))
        val r = SkinPack.read(built)
        assertEquals(2, r.skins.size); assertEquals(0, r.skipped)
        assertTrue(r.skins[0].slim); assertFalse(r.skins[1].slim)
        assertEquals(PackKind.SKIN, Packs.inspect(built, "x.mcpack").getOrThrow().kind)
    }

    @Test fun skinPackSkipsBadTextures() {
        assertEquals(1, SkinPack.read(SkinPack.build("p", listOf(SkinInput("a", png(64, 64), false)))).skins.size)
        assertThrows(IllegalStateException::class.java) { SkinPack.read(zipT("manifest.json" to "{}")) }
    }

    @Test fun inspectsLooseManifestAndAddon() {
        val r = Packs.inspect(zipT("bp/manifest.json" to BP), "b.mcpack").getOrThrow()
        assertEquals(PackKind.BEHAVIOR, r.kind); assertEquals("1.0.0", r.packs[0].version); assertEquals("1.21.0", r.packs[0].minEngine)
        val two = Packs.inspect(zipT("a/manifest.json" to BP, "b/manifest.json" to BP.replace("data", "resources")), "x.mcaddon").getOrThrow()
        assertEquals(PackKind.ADDON, two.kind); assertEquals(2, two.packs.size)
    }

    @Test fun nestedMcaddonIsStreamed() {
        val inner = zipT("manifest.json" to BP)
        val outer = zip("pack.mcpack" to inner, "readme.txt" to "hi".toByteArray())
        val r = Packs.inspect(outer, "all.mcaddon").getOrThrow()
        assertEquals(1, r.packs.size); assertEquals(PackKind.ADDON, r.kind)
    }

    @Test fun compatibilityComparesEngineVersion() {
        val r = Packs.inspect(zipT("manifest.json" to BP), "b.mcpack").getOrThrow()
        assertTrue(Packs.compatibility(r, "1.26.60.4").isEmpty())
        assertEquals(1, Packs.compatibility(r, "1.20.80").size)
        assertEquals(1, Packs.compatibility(r, null).size)
    }

    @Test fun rejectsNonPacksAndTraversal() {
        assertTrue(Packs.inspect(zipT("readme.txt" to "hi"), "a.mcpack").isFailure)
        assertTrue(Packs.inspect(ByteArray(10), "a.mcpack").isFailure)
        assertTrue(Zip.readSelected(zipT("../evil.json" to "{}")) { true }.isEmpty())
    }

    @Test fun worldInspection() {
        assertEquals("My World", Packs.inspectWorld(zipT("level.dat" to "x", "levelname.txt" to "My World\n")).getOrThrow().name)
        assertTrue(Packs.inspectWorld(zipT("a.txt" to "x")).isFailure)
    }

    @Test fun versions() {
        assertTrue(Versions.compare("1.26.60.4", "1.26.60") > 0); assertEquals(0, Versions.compare("1.26", "1.26.0.0"))
        assertTrue(Versions.compare("1.9.0", "1.10.0") < 0)
        assertTrue(Versions.sameRelease("1.26.60.7", "1.26.60.4")); assertFalse(Versions.sameRelease("1.26.50.1", "1.26.60.4"))
        assertFalse(Versions.sameRelease("", "1.26.60.4"))
    }

    @Test fun cosmeticSlotsAreExclusiveButCombinable() {
        var l = emptyList<String>()
        l = Catalog.toggle(l, "hat_crown"); l = Catalog.toggle(l, "halo_ring"); l = Catalog.toggle(l, "wings_angel")
        assertEquals(setOf("hat_crown", "halo_ring", "wings_angel"), l.toSet())       // different slots combine
        l = Catalog.toggle(l, "hat_wizard")
        assertTrue("hat_wizard" in l && "hat_crown" !in l)                             // same slot replaces
        l = Catalog.toggle(l, "other_scarf"); l = Catalog.toggle(l, "other_belt")
        assertTrue("other_scarf" in l && "other_belt" in l)                            // 'other' stacks
        assertFalse("wings_angel" in Catalog.toggle(l, "wings_angel"))                 // tap again unequips
        assertEquals(l, Catalog.toggle(l, "nope"))
    }

    @Test fun galleriesAreSeparate() {
        assertEquals(4, Gallery.entries.size)
        assertTrue(Catalog.inGallery(Gallery.HEAD).all { it.slot in setOf("hat", "halo", "head") })
        assertTrue(Catalog.inGallery(Gallery.BACK).isNotEmpty())
        assertTrue(Catalog.inGallery(Gallery.CAPE).isEmpty() && Catalog.inGallery(Gallery.PETS).isEmpty())
        assertEquals(Catalog.cosmetics.size, Catalog.cosmetics.map { it.id }.toSet().size)
    }

    @Test fun loadoutsAreUnlimitedAndRoundTrip() {
        var list = emptyList<SavedLoadout>()
        for (i in 1..500) list = Loadouts.upsert(list, SavedLoadout("L$i", listOf("hat_crown"), true, "orbit_ion", i % 2 == 0, null))
        assertEquals(500, list.size)
        list = Loadouts.upsert(list, SavedLoadout("L1", emptyList(), false, "orbit_ember", true, "pet_cat"))
        assertEquals(500, list.size)
        val back = Loadouts.fromJson(Loadouts.toJson(list))
        assertEquals(list, back); assertTrue(Loadouts.fromJson("garbage").isEmpty())
        val st = Loadouts.apply(SavedLoadout("x", listOf("hat_crown", "bogus"), true, "orbit_ion", false, "pet_cat"), ViewerState())
        assertEquals(listOf("hat_crown"), st.cosmetics); assertEquals("pet_cat", st.petId)
    }

    @Test fun viewerStateJson() {
        val j = org.json.JSONObject(ViewerState(cosmetics = listOf("hat_crown"), petId = "pet_cat").toJson())
        assertEquals("hat_crown", j.getJSONArray("cosmetics").getString(0)); assertTrue(j.isNull("skin"))
        assertEquals("pet_cat", j.getJSONObject("pet").getString("id"))
    }

    @Test fun resourcePackBuildsValidManifest() {
        val b = ResourcePack.build("ORBIT Branding", "d", mapOf("textures/ui/title.png" to png(64, 64)))
        val r = Packs.inspect(b, "x.mcpack").getOrThrow()
        assertEquals(PackKind.RESOURCE, r.kind)
        assertThrows(IllegalArgumentException::class.java) { ResourcePack.build("x", "y", mapOf("../a" to ByteArray(1))) }
    }

    @Test fun accountApiRejectsHttp() {
        assertThrows(IllegalArgumentException::class.java) { AccountApi("http://x.com").signIn("a", "b") }
    }
}
