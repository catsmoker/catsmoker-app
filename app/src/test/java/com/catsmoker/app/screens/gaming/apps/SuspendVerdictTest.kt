package com.catsmoker.app.screens.gaming.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendVerdictTest {
    @Test
    fun readsBothStatesAndOnlyUserZero() {
        val output = """
            Packages:
            Package [com.frozen.one] (abc123):
              User 0: ceDataInode=1 installed=true hidden=false suspended=true
            Package [com.awake.two] (def456):
              User 0: ceDataInode=3 installed=true hidden=false suspended=false
              User 10: installed=true suspended=true
            Package [com.frozen.three] (ghi789):
              User 0: installed=true hidden=false suspended=true suspendingPackage=com.android.shell
        """.trimIndent()
        assertEquals(
            mapOf("com.frozen.one" to true, "com.awake.two" to false, "com.frozen.three" to true),
            SuspendVerdict.parseDumpsysStates(output)
        )
    }

    @Test
    fun factoryCopyDoesNotOverrideLiveSuspension() {
        val output = """
            Packages:
            Package [com.example.updated]:
              User 0: installed=true suspended=true
            Hidden system packages:
            Package [com.example.updated] (factory):
              User 0: installed=true suspended=false
        """.trimIndent()
        assertEquals(mapOf("com.example.updated" to true), SuspendVerdict.parseDumpsysStates(output))
    }

    @Test
    fun incompletePackageBlocksAreUnknownNotAwake() {
        val output = """
            Package [com.example.game]:
              User 0: lastSuspendedTime=123
              User 10: installed=true suspended=false
            Package [com.example.truncated] (abc):
        """.trimIndent()
        assertTrue(SuspendVerdict.parseDumpsysStates(output).isEmpty())
        assertTrue(SuspendVerdict.parseDumpsysStates("Permission denial: can't dump").isEmpty())
    }

    @Test
    fun acceptsExplicitStateForTheCommandedPackage() {
        assertEquals(
            true, SuspendVerdict.reportedState("com.example.app", 0, "Package com.example.app new suspended state: true")
        )
        assertEquals(
            false, SuspendVerdict.reportedState("com.example.app", 0, "Package com.example.app New Suspended State: False")
        )
    }

    @Test
    fun silentRefusedOrMismatchedReplyDoesNotVerifyUnsuspend() {
        val pkg = "com.example.app"
        assertNull(SuspendVerdict.reportedState(pkg, 0, ""))
        assertNull(SuspendVerdict.reportedState(pkg, 1, "Package $pkg new suspended state: false"))
        assertNull(SuspendVerdict.reportedState(pkg, 0, "Package com.example.other new suspended state: false"))
        assertNull(SuspendVerdict.reportedState(pkg, 0, "Error: permission denied"))
    }

    @Test
    fun uninstalledPackageIsDifferentFromGenericCommandFailure() {
        val pkg = "com.example.app"
        assertTrue(SuspendVerdict.isMissingPackage(
            pkg, 1, "java.lang.IllegalArgumentException: Unknown target package: $pkg"
        ))
        assertFalse(SuspendVerdict.isMissingPackage(pkg, 1, "Error: package manager unavailable"))
        assertFalse(SuspendVerdict.isMissingPackage(
            pkg, 1, "java.lang.IllegalArgumentException: Unknown target package: com.example.other"
        ))
    }
}
