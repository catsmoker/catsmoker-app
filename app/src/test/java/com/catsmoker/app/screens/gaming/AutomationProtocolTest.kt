package com.catsmoker.app.screens.gaming

import com.catsmoker.app.screens.gaming.automation.AutomationProtocol
import com.catsmoker.app.screens.gaming.automation.AutomationStatus
import com.catsmoker.app.screens.gaming.mode.GamingModeObservation
import com.catsmoker.app.screens.gaming.mode.GamingOptimizationSnapshot
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AutomationProtocolTest {
    @Test fun accessIsDisabledWithoutExactToken() {
        val secret = "a".repeat(64)
        assertFalse(AutomationProtocol.authorized(null, null))
        assertFalse(AutomationProtocol.authorized(secret, null))
        assertFalse(AutomationProtocol.authorized(secret, "a".repeat(63)))
        assertFalse(AutomationProtocol.authorized(secret, "b".repeat(64)))
        assertTrue(AutomationProtocol.authorized(secret, secret))
    }

    @Test fun onlyClosedOperationsAndStrictPackagesAreAccepted() {
        listOf("sh", "RUN_COMMAND", "apply_saved", "", "RESTORE;reboot").forEach {
            assertNull(AutomationProtocol.parse(it, null))
        }
        listOf("../com.game", "com.game;reboot", "com.game\n", "com..game", "", "a".repeat(256)).forEach {
            assertNull(AutomationProtocol.parse("SELECT_GAME", it))
        }
        assertNull(AutomationProtocol.parse("LAUNCH_GAME", null))
        assertNull(AutomationProtocol.parse("RESTORE", "com.game"))
        assertNotNull(AutomationProtocol.parse("APPLY_SAVED", "com.Game_1.app"))
        assertNotNull(AutomationProtocol.parse("QUERY_STATUS", null))
    }

    @Test fun receiverCannotLaunchAnActivityOrPrivilegedWorkDirectly() {
        val receiver = File("src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomationReceiver.kt").readText()
        assertFalse(receiver.contains("startActivity("))
        assertTrue(receiver.contains("ContextCompat.startForegroundService("))
        assertTrue(receiver.contains("catch (_: IllegalStateException)"))
        assertTrue(receiver.contains("catch (_: SecurityException)"))
        assertFalse(receiver.contains("automation.execute("))
        assertTrue(receiver.contains("FLAG_IMMUTABLE"))
        assertTrue(receiver.contains("automation.parse(intent)"))
        assertTrue(receiver.contains("foreground_required"))
    }

    @Test fun activityWaitsForResumedBeforeExecutingEvenAnImmediateLaunch() {
        val activity = File("src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomationActivity.kt").readText()
        val ready = activity.indexOf("lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }")
        val execution = activity.indexOf("automation.execute(intent).await()")
        val foregroundRecheck = activity.indexOf("if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)")
        assertTrue(ready >= 0 && ready < execution && execution < foregroundRecheck)
        assertFalse(activity.contains("override fun onResume()"))
    }

    @Test fun gameSelectionDoesNotDeployResolution() {
        val vm = File("src/main/java/com/catsmoker/app/screens/gaming/GamingToolsViewModel.kt").readText()
        val selection = vm.substringAfter("fun selectGame(").substringBefore("private fun rememberResolutionInputs")
        assertFalse(selection.contains("execSafe"))
        assertFalse(selection.contains("executeResolution"))
        assertFalse(selection.contains("gamingModeJobs"))
        assertTrue(selection.contains("pendingResApply = null"))
    }

    @Test fun interruptedExecutionCannotBeReportedAsStillRunningAfterRestart() {
        val running = """{"version":1,"result":"running","time":1}"""
        assertEquals("interrupted", JSONObject(AutomationStatus.afterProcessStart(running)).getString("result"))
        assertEquals("unknown", JSONObject(AutomationStatus.afterProcessStart("broken")).getString("result"))
        assertEquals("idle", JSONObject(AutomationStatus.afterProcessStart(null)).getString("result"))
    }

    @Test fun statusObservationDoesNotCreateOrDriveAnEngine() {
        val observation = GamingModeObservation()
        assertNull(observation.current())
        var state = GamingModeObservation.Snapshot("Active", true, false, 2)
        observation.observe { state }
        assertEquals(state, observation.current())
        state = GamingModeObservation.Snapshot("Idle", false, false, 0)
        assertEquals(state, observation.current())
        val automation = File("src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomation.kt").readText()
        val query = automation.substringAfter("fun query(): String").substringBefore("fun select(")
        assertFalse(query.contains("engine."))
        assertTrue(query.contains("modeObservation.current()"))
    }

    @Test fun activationCapturesPackageInputsBeforeSuspensionAndRestoreIsOwned() {
        val engine = File("src/main/java/com/catsmoker/app/screens/gaming/mode/GamingEngine.kt").readText()
        val activation = engine.substringAfter("private suspend fun enableGamingMode(")
        assertTrue(activation.contains("appChanges.apply(targets, identity)"))
        assertTrue(activation.indexOf("gameSettingsStore.read(it)") in 0 until activation.indexOf("appChanges.apply("))
        assertTrue(activation.contains("PointerSpeed.sanitize(pointerChoice)"))
        assertTrue(activation.contains("val downscale = downscaleChoice"))
        assertTrue(engine.contains("appChanges.restoreSuspensions()"))
        assertFalse(engine.contains("affected + getSuspendTargets"))
        val automation = File("src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomation.kt").readText()
        assertTrue(automation.contains("jobs.deactivate().join()"))
    }

    @Test fun desktopOwnershipAndPriorGameModeSurviveSnapshotRoundTrip() {
        val recorded = GamingOptimizationSnapshot.fromJson(
            """{"preparationPolicyVersion":1,"displayOverridesOwned":false,"previousGameMode":"battery","activeGamePackage":"com.game","timestamp":1,"affectedPackages":[]}"""
        )!!
        val restored = GamingOptimizationSnapshot.fromJson(recorded.toJson())!!
        assertEquals(1, restored.preparationPolicyVersion)
        assertEquals(false, restored.displayOverridesOwned)
        assertEquals("battery", restored.previousGameMode)
        val legacy = GamingOptimizationSnapshot.fromJson("""{"activeGamePackage":"com.game","timestamp":1}""")!!
        assertNull(legacy.preparationPolicyVersion)
        assertNull(legacy.displayOverridesOwned)
        assertNull(legacy.previousGameMode)
    }

    @Test fun exitRestoreWaitsAndBusyDeliveryDoesNotOverwriteActiveReport() {
        val automation = File("src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomation.kt").readText()
        assertTrue(automation.contains("writes.lock()"))
        assertTrue(automation.contains("restorePending = true"))
        assertTrue(automation.contains("cachePreloader.cancel()"))
        assertTrue(automation.contains("lastDelivery"))
        assertTrue(automation.contains("lastDelivery = JSONObject().put(\"version\", 1)"))
        assertTrue(automation.contains("else if (!writes.tryLock()) return response(\"busy\")"))
        val cache = File("src/main/java/com/catsmoker/app/screens/gaming/cache/GameCachePreloader.kt").readText()
        assertTrue(cache.contains("ParcelFileDescriptor.AutoCloseInputStream"))
        assertTrue(cache.contains("Os.munmap(address, count)"))
    }

    @Test fun recoveryRetainsSnapshotUntilAllOwnedStepsAreClean() {
        val engine = File("src/main/java/com/catsmoker/app/screens/gaming/mode/GamingEngine.kt").readText()
        val journal = engine.substringAfter("private fun beginSession(", "").substringBefore("private fun currentBootCount()")
        assertTrue(journal.contains(".commit()"))
        val close = engine.substringAfter("private suspend fun disableGamingMode(")
            .substringAfter("if (clean) {").substringBefore("} else {")
        assertTrue(close.contains("remove(\"last_snapshot\")"))
        val revert = engine.substringAfter("private suspend fun revertFromSnapshot(").substringBefore("private suspend fun restoreGlobalSetting(")
        assertFalse(revert.contains("remove(\"last_snapshot\")"))
    }

    @Test fun sessionRestorePreservesTheStandaloneFixedPerformanceOwner() {
        val engine = File("src/main/java/com/catsmoker/app/screens/gaming/mode/GamingEngine.kt").readText()
        val restore = engine.substringAfter("private suspend fun disableGamingMode(", "")
            .substringBefore("private suspend fun recoverThermalOverrideIfNeeded(")
        assertTrue(restore.contains("fixedPerformanceArgs(manualFixedPerformance)"))
        assertFalse(restore.contains("putBoolean(\"fixed_perf_manual\", false)"))
        val appsOnly = engine.substringAfter("private suspend fun restoreAppsFromRecord(", "")
            .substringBefore("private fun beginSession(")
        assertTrue(appsOnly.contains("appChanges.restoreSuspensions()"))
        assertFalse(appsOnly.contains("fixed_perf_manual"))
        val manual = engine.substringAfter("suspend fun toggleFixedPerformanceMode(")
            .substringBefore("fun setInterventionDownscale(")
        assertTrue(manual.contains("if (_sessionOpen.value)"))
    }
    @org.junit.Test fun launchOnlyDoesNotConstructThePrivilegedEngineForItsReport() {
        val automation = java.io.File("src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomation.kt").readText()
        val reportGuard = automation.substringAfter("}.let { result ->")
            .substringBefore("JSONObject(result)")
        assertTrue(reportGuard.contains("AutomationProtocol.Operation.LAUNCH_GAME"))
        assertTrue(reportGuard.contains("return@let result"))
        assertTrue(automation.contains("(pkg == null || JSONObject(cachePreloader.status.value)"))
    }

    @org.junit.Test fun cacheStatusAndInitializationAreInsideTheOwnedLock() {
        val cache = java.io.File("src/main/java/com/catsmoker/app/screens/gaming/cache/GameCachePreloader.kt").readText()
        val entry = cache.substringAfter("suspend fun preload(pkg: String)")
            .substringBefore("private suspend fun preloadLocked")
        assertTrue(entry.indexOf("mutex.tryLock()") < entry.indexOf("preloadLocked(pkg)"))
        assertTrue(entry.contains("finally"))
        assertTrue(entry.contains("mutex.unlock()"))
        assertFalse(entry.contains("\"disabled\""))
        val read = cache.substringAfter("private suspend fun preloadLocked")
        assertTrue(read.contains("if (!desired.cachePreload)"))
        assertTrue(read.contains("manager.getMemoryInfo"))
    }
}
