package com.catsmoker.app.screens.gaming.mode

import com.catsmoker.app.platform.shell.ShellRunner.PrivilegedIdentity
import com.catsmoker.app.screens.gaming.apps.SuspendVerdict
import com.catsmoker.app.screens.gaming.apps.SuspendVerdict.PackageState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspensionRescueTest {
    private val shell = PackageState(suspended = true, installed = true, owners = setOf("com.android.shell"))
    private val root = shell.copy(owners = setOf("root"))
    private val awake = shell.copy(suspended = false, owners = emptySet())

    @Test
    fun parsesInlineAndNestedOwnerFormatsWithoutMixingAndroidUsers() {
        val dump = """
            Packages:
              Package [com.example.inline] (abc):
                User 0: installed=true suspended=true suspendingPackage=com.android.shell dialogInfo=null
                User 10: installed=true suspended=true suspendingPackage=android
              Package [com.example.nested] (abc):
                User 0: installed=true suspended=true
                  Suspend params:
                    suspendingPackage=<0>root dialogInfo=null quarantined=false
                    suspendingPackage=<0>com.android.shell dialogInfo=null quarantined=false
                User 10: installed=true suspended=true
                  Suspend params:
                    suspendingPackage=<10>android
              Package [com.example.crossuser] (abc):
                User 0: installed=true suspended=true
                  Suspend params:
                    suspendingPackage=<10>root dialogInfo=null quarantined=false
              Package [com.example.quarantine] (abc):
                User 0: installed=true suspended=true
                    suspendingPackage=<0>com.android.shell dialogInfo=null quarantined=true
            Hidden system packages:
              Package [com.example.inline] (abc):
                User 0: installed=true suspended=false
        """.trimIndent()
        val states = SuspendVerdict.parseDumpsysPackages(dump)
        assertEquals(shell, states["com.example.inline"])
        assertEquals(shell.copy(owners = setOf("root", "com.android.shell")), states["com.example.nested"])
        assertEquals(setOf("<10>root"), states.getValue("com.example.crossuser").owners)
        assertTrue(states.getValue("com.example.quarantine").quarantined)
    }

    @Test
    fun showsOnlyInstalledUnrecordedPackagesWithKnownShellOrRootOwners() {
        val states = mapOf(
            "com.example.shell" to shell,
            "com.example.root" to root,
            "com.example.recorded" to shell,
            "com.example.policy" to shell.copy(owners = setOf("android")),
            "com.example.otherprofile" to root.copy(owners = setOf("<10>root")),
            "com.example.multiple" to shell.copy(owners = setOf("com.android.shell", "com.example.manager")),
            "com.example.unknown" to shell.copy(owners = emptySet()),
            "com.example.quarantine" to shell.copy(quarantined = true),
            "com.example.absent" to root.copy(installed = false),
            "com.example.awake" to awake,
            "com.example.invalid;name" to shell
        )
        assertEquals(
            mapOf("com.example.shell" to listOf(PrivilegedIdentity.SHELL), "com.example.root" to listOf(PrivilegedIdentity.ROOT)),
            SuspensionRescue.candidates(states, excluded = setOf("com.example.recorded"))
        )
    }

    @Test
    fun restoresOnlySelectedPackagesWithCurrentEligibleOwners() = runBlocking {
        val commands = mutableListOf<Pair<String, PrivilegedIdentity>>()
        val selected = setOf("com.example.both", "com.example.awake", "com.example.policy", "com.example.recorded")
        val verified = SuspensionRescue.restore(
            selected = selected,
            fresh = mapOf(
                "com.example.both" to shell.copy(owners = setOf("com.android.shell", "root")),
                "com.example.awake" to awake,
                "com.example.policy" to shell.copy(owners = setOf("android")),
                "com.example.recorded" to shell,
                "com.example.unselected" to root
            ),
            excluded = setOf("com.example.recorded"),
            canContinue = { true },
            release = { pkg, owner ->
                commands += pkg to owner
                owner == PrivilegedIdentity.SHELL   // still suspended after Shell; awake after root
            },
            readBack = { error("Explicit awake result already confirmed the write") }
        )
        assertEquals(setOf("com.example.both", "com.example.awake"), verified)
        assertEquals(
            listOf("com.example.both" to PrivilegedIdentity.SHELL, "com.example.both" to PrivilegedIdentity.ROOT),
            commands
        )
    }

    @Test
    fun missingOrRefusedRepliesRequireExplicitReadBack() = runBlocking {
        val verified = SuspensionRescue.restore(
            selected = setOf("com.example.back", "com.example.uncertain"),
            fresh = mapOf("com.example.back" to root, "com.example.uncertain" to shell),
            excluded = emptySet(),
            canContinue = { true },
            release = { _, owner -> if (owner == PrivilegedIdentity.SHELL) true else null },
            readBack = { mapOf("com.example.back" to awake) }
        )
        assertEquals(setOf("com.example.back"), verified)
    }

    @Test
    fun stopsFurtherWritesWhenConsentChangesDuringRestore() = runBlocking {
        var allowed = true
        var attempts = 0
        SuspensionRescue.restore(
            selected = setOf("com.example.first", "com.example.second"),
            fresh = mapOf("com.example.first" to root, "com.example.second" to shell),
            excluded = emptySet(),
            canContinue = { allowed },
            release = { _, _ ->
                attempts++
                allowed = false
                null
            },
            readBack = { error("No inventory read after consent was revoked") }
        )
        assertEquals(1, attempts)
    }
}
