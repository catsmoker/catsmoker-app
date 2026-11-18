package com.catsmoker.app.screens.gaming

import org.junit.Assert.*
import org.junit.Test

class GameSettingsRepositoryTest {
    private val disk = mutableMapOf<String, String>()
    private var writes = 0
    private fun open(legacy: GameSettings = GameSettings()) = GameSettingsRepository(
        { disk[it] }, { key, value ->
            writes++
            if (value == null) disk.remove(key) else disk[key] = value
        }, legacy
    )

    @Test fun packageEditsAndSelectionSurviveRecreation() {
        val store = open()
        store.select("com.game.a")
        store.update("com.game.a") { it.copy(downscale = .75f, pointerSpeed = 3,
            resolutionId = "custom", width = "900", height = "1600", dpi = "320") }
        store.select("com.game.b")
        store.update("com.game.b") { it.copy(downscale = .9f, pointerSpeed = -3) }
        val reopened = open()
        assertEquals("com.game.b", reopened.selection.value.packageName)
        assertEquals(.9f, reopened.selection.value.settings.downscale)
        reopened.select("com.game.a")
        assertEquals("900", reopened.selection.value.settings.width)
        assertEquals(.75f, reopened.selection.value.settings.downscale)
        assertEquals(3, reopened.selection.value.settings.pointerSpeed)
    }

    @Test fun lateEditIsAddressedToItsOriginalPackage() {
        val store = open()
        store.select("com.game.a")
        val captured = store.selection.value.packageName!!
        repeat(100) { store.select(if (it % 2 == 0) "com.game.a" else "com.game.b") }
        store.select("com.game.b")
        store.update(captured) { it.copy(pointerSpeed = 5) }
        assertNull(store.selection.value.settings.pointerSpeed)
        assertEquals(5, store.read("com.game.a").pointerSpeed)
    }

    @Test fun legacyDefaultsAreFrozenAndNotAnotherGamesChoices() {
        val store = open(GameSettings(downscale = .85f, pointerSpeed = 1))
        store.update("com.game.a") { it.copy(downscale = .5f) }
        assertEquals(.85f, store.read("com.game.b").downscale)
        assertEquals(.85f, open(GameSettings(downscale = .3f)).read("com.game.c").downscale)
    }

    @Test fun duplicateUpdatesDoNotWriteAndRemovalDoesNotErase() {
        val store = open()
        store.select("com.game.a")
        store.update("com.game.a") { it.copy(pointerSpeed = 3) }
        val before = writes
        store.update("com.game.a") { it.copy(pointerSpeed = 3) }
        store.select("com.game.a")
        assertEquals(before, writes)
        store.select(null)
        assertEquals(3, open().read("com.game.a").pointerSpeed)
    }

    @Test fun corruptOrFutureRecordsAndInvalidSelectionFailSafely() {
        disk["selected_package"] = "bad;command"
        disk["game:com.game.a"] = """{"version":42,"pointerSpeed":3}"""
        disk["game:com.game.b"] = "{broken"
        val store = open()
        assertNull(store.selection.value.packageName)
        assertEquals(GameSettings(), store.read("com.game.a"))
        assertEquals(GameSettings(), store.read("com.game.b"))
    }

    @Test fun nullableOffValuesRoundTripWithoutReturningLegacyValues() {
        val store = open(GameSettings(downscale = .75f, pointerSpeed = 3))
        store.update("com.game.a") { it.copy(downscale = null, pointerSpeed = null) }
        assertNull(open().read("com.game.a").downscale)
        assertNull(open().read("com.game.a").pointerSpeed)
    }

    @Test fun concurrentPackageEditsDoNotLoseEachOthersFields() {
        val store = open()
        val a = Thread { repeat(100) { store.update("com.game.a") { it.copy(pointerSpeed = 3) } } }
        val b = Thread { repeat(100) { store.update("com.game.a") { it.copy(width = "900") } } }
        a.start(); b.start(); a.join(); b.join()
        assertEquals(3, store.read("com.game.a").pointerSpeed)
        assertEquals("900", store.read("com.game.a").width)
    }

    @Test fun firstOpenAndExplicitGlobalSurviveDiscoveryAndRecreation() {
        val store = open()
        assertNull(store.selection.value.packageName)
        assertEquals("true", disk["selection_initialized"])
        assertNotNull(disk["selected_package"])
        store.reconcile(setOf("com.game.a", "com.game.b"))
        assertNull(store.selection.value.packageName)
        store.select("com.game.a")
        store.select(null)
        val reopened = open()
        reopened.reconcile(setOf("com.game.a"))
        assertNull(reopened.selection.value.packageName)
    }

    @Test fun tappingSelectedGameReturnsToGlobalAndOtherGameSwitchesDirectly() {
        val store = open()
        store.toggle("com.game.a")
        assertEquals("com.game.a", store.selection.value.packageName)
        store.toggle("com.game.b")
        assertEquals("com.game.b", store.selection.value.packageName)
        store.toggle("com.game.b")
        assertNull(store.selection.value.packageName)
        assertNull(open().selection.value.packageName)
    }

    @Test fun missingSelectedGameReturnsToGlobalWithoutErasingSettings() {
        val store = open()
        store.select("com.game.a")
        store.update("com.game.a") { it.copy(cachePreload = true, pointerSpeed = 3) }
        store.reconcile(setOf("com.game.b"))
        assertNull(open().selection.value.packageName)
        assertTrue(open().read("com.game.a").cachePreload)
        assertEquals(3, store.read("com.game.a").pointerSpeed)
    }

    @Test fun upgradeRetainsValidSelectionWithoutInventingOneForAnEmptyLegacyStore() {
        disk["selected_package"] = "com.game.a"
        assertEquals("com.game.a", open().selection.value.packageName)
        disk.clear()
        assertNull(open().selection.value.packageName)
    }

    @Test fun recentPicksAreBoundedDistinctAndPersistedButGlobalDoesNotReplaceThem() {
        val store = open()
        repeat(30) { store.select("com.game.g$it") }
        store.select("com.game.g5")
        val recent = store.recentPackages()
        assertEquals(24, recent.size)
        assertEquals("com.game.g5", recent.first())
        assertEquals(recent.size, recent.distinct().size)
        store.select(null)
        assertEquals(recent, open().recentPackages())
    }
}
