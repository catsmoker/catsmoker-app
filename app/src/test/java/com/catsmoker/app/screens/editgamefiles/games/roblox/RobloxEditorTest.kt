package com.catsmoker.app.screens.editgamefiles.games.roblox

import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RobloxEditorTest {
    private val original = file(" \n{\"Other\":true,\"DFIntTaskSchedulerTargetFps\":60}\n").copy(mode = "640", uid = 0, gid = 0)

    @Test fun `backup precedes mutation and repeated applies keep the first original`() = runBlocking {
        val files = Files(original)
        val backups = Backups()
        files.beforeReplace = { desired ->
            assertEquals(original, backups.value?.original)
            assertEquals(desired, backups.value?.pending)
        }
        val editor = RobloxEditor(files, backups)
        editor.apply(120, original)
        editor.apply(240, files.current)
        assertEquals(original, backups.value?.original)
        editor.restore(files.current)
        assertEquals(original, files.current)
        assertFalse(editor.status().canRestore)
        assertNotNull(backups.value)
        editor.apply(144, original)
        editor.restore(files.current)
        assertEquals(original, files.current)
    }

    @Test fun `restore removes only a file originally absent`() = runBlocking {
        val files = Files(RobloxFile())
        val editor = RobloxEditor(files, Backups())
        editor.apply(120, files.current)
        assertTrue(editor.status().canRestore)
        editor.restore(files.current)
        assertEquals(RobloxFile(), files.current)
    }

    @Test fun `unchanged target does not write or create a backup`() = runBlocking {
        val files = Files(original)
        val backups = Backups()
        RobloxEditor(files, backups).apply(60, original)
        assertEquals(0, files.writes)
        assertNull(backups.value)
    }

    @Test fun `stale UI snapshot cannot overwrite a newer file`() = runBlocking {
        val files = Files(original)
        val editor = RobloxEditor(files, Backups())
        files.current = file("{\"outside\":true}")
        fails(RobloxProblem.CONFLICT) { editor.apply(120, original) }
        assertEquals(0, files.writes)
    }

    @Test fun `external change blocks apply and restore even after refresh`() = runBlocking {
        val files = Files(original)
        val editor = RobloxEditor(files, Backups())
        editor.apply(120, original)
        files.current = file("{\"outside\":true}")
        assertTrue(editor.status().conflict)
        assertFalse(editor.status().canRestore)
        fails(RobloxProblem.CONFLICT) { editor.apply(240, files.current) }
        fails(RobloxProblem.CONFLICT) { editor.restore(files.current) }
        assertEquals(1, files.writes)
    }

    @Test fun `failed backup prevents all target mutation`() = runBlocking {
        val files = Files(original)
        val backups = Backups().apply { failSave = 1 }
        fails(RobloxProblem.BACKUP) { RobloxEditor(files, backups).apply(120, original) }
        assertEquals(original, files.current)
        assertEquals(0, files.writes)
    }

    @Test fun `unreadable journal is not treated as a missing backup`() = runBlocking {
        val files = Files(original)
        val backups = Backups().apply { failLoad = true }
        fails(RobloxProblem.BACKUP) { RobloxEditor(files, backups).apply(120, original) }
        assertEquals(0, files.writes)
    }

    @Test fun `unreadable file is not treated as absent`() = runBlocking {
        val files = Files(original).apply { failRead = true }
        val backups = Backups()
        fails(RobloxProblem.IO) { RobloxEditor(files, backups).apply(120, original) }
        assertNull(backups.value)
    }

    @Test fun `invalid JSON is never replaced`() = runBlocking {
        val files = Files(file("{invalid}"))
        val backups = Backups()
        val editor = RobloxEditor(files, backups)
        assertTrue(editor.status().invalidConfig)
        fails(RobloxProblem.INVALID_CONFIG) { editor.apply(120, files.current) }
        assertNull(backups.value)
        assertEquals(0, files.writes)
    }

    @Test fun `failed replacement retains recovery and allows retry`() = runBlocking {
        val files = Files(original).apply { failWrite = true }
        val backups = Backups()
        val editor = RobloxEditor(files, backups)
        fails(RobloxProblem.IO) { editor.apply(120, original) }
        assertEquals(original, backups.value?.original)
        assertNotNull(backups.value?.pending)
        files.failWrite = false
        editor.apply(120, original)
        editor.restore(files.current)
        assertEquals(original, files.current)
    }

    @Test fun `unverified write is not success and retains recovery`() = runBlocking {
        val files = Files(original).apply { ignoreWrite = true }
        val backups = Backups()
        fails(RobloxProblem.VERIFY) { RobloxEditor(files, backups).apply(120, original) }
        assertNotNull(backups.value?.pending)
        assertEquals(original, backups.value?.original)
    }

    @Test fun `termination after mutation is recoverable in a fresh editor`() = runBlocking {
        val files = Files(original).apply { cancelAfterWrite = true }
        val backups = Backups()
        try {
            RobloxEditor(files, backups).apply(120, original)
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        files.cancelAfterWrite = false
        val restarted = RobloxEditor(files, backups)
        assertTrue(restarted.status().canRestore)
        restarted.restore(files.current)
        assertEquals(original, files.current)
    }

    @Test fun `failed final journal commit still recognises applied file`() = runBlocking {
        val files = Files(original)
        val backups = Backups().apply { failSave = 2 }
        fails(RobloxProblem.BACKUP) { RobloxEditor(files, backups).apply(120, original) }
        assertEquals(120, RobloxConfig.target(files.current))
        val restarted = RobloxEditor(files, backups)
        assertFalse(restarted.status().conflict)
        restarted.restore(files.current)
        assertEquals(original, files.current)
    }

    @Test fun `failed restore verification keeps original record for retry`() = runBlocking {
        val files = Files(original)
        val backups = Backups()
        val editor = RobloxEditor(files, backups)
        editor.apply(120, original)
        files.ignoreWrite = true
        fails(RobloxProblem.VERIFY) { editor.restore(files.current) }
        assertEquals(original, backups.value?.original)
        files.ignoreWrite = false
        editor.restore(files.current)
        assertEquals(original, files.current)
    }

    @Test fun `backend compare check rejects a change between backup and replacement`() = runBlocking {
        val files = Files(original)
        val backups = Backups()
        files.beforeReplace = { files.current = file("{\"outside\":true}") }
        fails(RobloxProblem.CONFLICT) { RobloxEditor(files, backups).apply(120, original) }
        assertEquals(0, files.writes)
        assertEquals(original, backups.value?.original)
    }

    private suspend fun fails(problem: RobloxProblem, action: suspend () -> Unit) {
        try { action(); fail("Expected $problem") }
        catch (failure: RobloxFailure) { assertEquals(problem, failure.problem) }
    }

    private class Files(var current: RobloxFile) : RobloxFiles {
        var writes = 0
        var failRead = false
        var failWrite = false
        var ignoreWrite = false
        var cancelAfterWrite = false
        var beforeReplace: (RobloxFile) -> Unit = {}
        override suspend fun read(): RobloxFile {
            if (failRead) throw RobloxFailure(RobloxProblem.IO)
            return current
        }
        override suspend fun replace(expected: RobloxFile, desired: RobloxFile) {
            beforeReplace(desired)
            if (expected != current) throw RobloxFailure(RobloxProblem.CONFLICT)
            if (failWrite) throw RobloxFailure(RobloxProblem.IO)
            writes++
            if (!ignoreWrite) current = desired
            if (cancelAfterWrite) throw CancellationException()
        }
    }

    private class Backups : RobloxBackups {
        var value: RobloxBackup? = null
        var failSave = -1
        var failLoad = false
        private var saves = 0
        override fun load(): RobloxBackup? {
            if (failLoad) throw RobloxFailure(RobloxProblem.BACKUP)
            return value
        }
        override fun save(backup: RobloxBackup) {
            if (++saves == failSave) throw RobloxFailure(RobloxProblem.BACKUP)
            value = backup
        }
    }

    private fun file(text: String) = RobloxFile(Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)))
}
