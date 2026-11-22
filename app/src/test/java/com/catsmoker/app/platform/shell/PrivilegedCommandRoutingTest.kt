package com.catsmoker.app.platform.shell

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedCommandRoutingTest {
    private val root = File("src/main/java/com/catsmoker/app")
    private val runner = File(root, "platform/shell/ShellRunner.kt").readText()

    @Test
    fun shizukuWaitsInsideTheServerInsteadOfPollingExitValueOverBinder() {
        val remote = runner.substringAfter("private fun execShizukuRemoteRaw(").substringBefore("suspend fun trimCaches(")
        assertTrue(remote.contains("as? ShizukuRemoteProcess"))
        assertTrue(remote.contains("process.waitForTimeout("))
        assertFalse(remote.contains("process.waitFor("))
        assertTrue(remote.contains("if (!finished) return ExecResult.FAILED"))
    }

    @Test
    fun artUsesThePrivilegedOnlyExecutor() {
        val compiler = File(root, "screens/gaming/compilation/ArtCompiler.kt").readText()
        val compile = compiler.substringAfter("private suspend fun runCompileCommand(")
            .substringBefore("suspend fun cancelArtOptimization()")
        assertTrue(compile.contains("execPrivilegedSafeResult(*args, identity = identity)"))
        assertFalse(compile.contains("execSafeResult("))
        assertFalse(compile.contains("ProcessBuilder("))
    }

    @Test
    fun manualShellRecoveryCanDropFromAuthorizedRootAndVerifiesTheUidBeforeTheWrite() {
        val fallback = runner.substringAfter("internal suspend fun execAsShellViaRoot(", "")
            .substringBefore("internal suspend fun execCancellableRootSafeResult(")
        assertTrue(fallback.contains("val uid = PrivilegedIdentity.SHELL.uid"))
        assertTrue(fallback.contains("(id -u)"))
        assertTrue(fallback.contains("|| exit 126; exec"))
        assertTrue(fallback.contains("identity = PrivilegedIdentity.ROOT"))
    }
}
