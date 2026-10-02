package com.catsmoker.app.features.gamingtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Behaviour lock for the smallest-width preset library.
 *
 * Ported from `reference/gamingtools/Custom-Animations/.../utils/WidthPresetManager.kt`
 * (read in full): UUID-keyed named presets holding a width in dp, save / list / delete,
 * corrupt storage reading back as empty. Same two divergences as [AnimationPresetStoreTest]:
 * Gson-in-a-file, and save-time validation — here the 72..1000 dp clamp `wm density` itself
 * enforces (see `DisplayMetricsProvider.densityForSmallestWidthDp`), so anything outside it
 * is refused instead of stored.
 */
class WidthPresetStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = WidthPresetStore(tmp.newFile("width_presets.json"))

    @Test
    fun saveThenListRoundTrips() {
        val s = store()
        val saved = s.savePreset("Compact", 360)
        assertNotNull(saved)
        val all = s.getAll()
        assertEquals(1, all.size)
        assertEquals("Compact", all[0].name)
        assertEquals(360, all[0].widthDp)
        assertEquals(saved!!.id, all[0].id)
    }

    @Test
    fun blankNameIsRefused() {
        val s = store()
        assertNull(s.savePreset("", 360))
        assertNull(s.savePreset("   ", 360))
        assertTrue(s.getAll().isEmpty())
    }

    @Test
    fun widthsOutsideTheWmClampAreRefused() {
        val s = store()
        assertNull(s.savePreset("zero", 0))
        assertNull(s.savePreset("negative", -100))
        assertNull(s.savePreset("narrow", 71))
        assertNull(s.savePreset("wide", 1001))
        assertTrue(s.getAll().isEmpty())
    }

    @Test
    fun clampEdgesAreLegal() {
        val s = store()
        assertNotNull(s.savePreset("min", 72))
        assertNotNull(s.savePreset("max", 1000))
        assertEquals(2, s.getAll().size)
    }

    @Test
    fun deleteExistingRemovesOnlyIt() {
        val s = store()
        val a = s.savePreset("a", 360)!!
        val b = s.savePreset("b", 411)!!
        assertTrue(s.delete(a.id))
        assertEquals(listOf(b), s.getAll())
    }

    @Test
    fun deleteMissingReturnsFalse() {
        assertFalse(store().delete("no-such-id"))
    }

    @Test
    fun corruptFileReadsEmpty() {
        val f = tmp.newFile("width_presets.json")
        f.writeText("not json {{{")
        assertTrue(WidthPresetStore(f).getAll().isEmpty())
    }
}
