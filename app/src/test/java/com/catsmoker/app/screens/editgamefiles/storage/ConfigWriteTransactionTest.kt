package com.catsmoker.app.screens.editgamefiles.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConfigWriteTransactionTest {
    private val old = "old".toByteArray()
    private val replacement = "new".toByteArray()
    private inner class Fake : ConfigWriteTransaction.Channel {
        val events = mutableListOf<String>()
        var current = old
        var saved: ByteArray? = null
        var backupOk = true
        var writeOk = true
        var mutateInBackup = false
        var corruptWrite = false
        var failPrepare = false
        var cancelWrite: (() -> Unit)? = null
        override suspend fun prepare() { events += "prepare"; check(!failPrepare) }
        override suspend fun read(): ByteArray { events += "read"; return current.copyOf() }
        override suspend fun backup(bytes: ByteArray): Boolean {
            events += "backup"
            saved = bytes.copyOf()
            if (mutateInBackup) current = "changed".toByteArray()
            return backupOk
        }
        override suspend fun write(bytes: ByteArray): Boolean {
            events += "write"
            cancelWrite?.invoke()
            if (writeOk) current = if (corruptWrite) "corrupt".toByteArray() else bytes.copyOf()
            return writeOk
        }
    }

    @Test fun `backs up actual bytes then verifies exact replacement in order`() = runBlocking {
        val channel = Fake()
        assertTrue(ConfigWriteTransaction.write(channel, old, replacement))
        assertArrayEquals(old, channel.saved)
        assertEquals(listOf("prepare", "read", "backup", "read", "write", "read"), channel.events)
    }

    @Test fun `stop failure snapshot conflict and backup failure never write`() = runBlocking {
        listOf(Fake().apply { failPrepare = true }, Fake().apply { current = replacement },
            Fake().apply { backupOk = false }, Fake().apply { mutateInBackup = true }).forEach { channel ->
            try { ConfigWriteTransaction.write(channel, old, replacement); fail("Expected failure") } catch (_: IllegalStateException) {
            } catch (_: IllegalArgumentException) { }
            assertFalse(channel.events.contains("write"))
        }
    }

    @Test fun `write failure and read-back mismatch retain pre-write backup`() = runBlocking {
        val failure = Fake().apply { writeOk = false }
        try { ConfigWriteTransaction.write(failure, old, replacement); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertArrayEquals(old, failure.saved)
        val mismatch = Fake().apply { corruptWrite = true }
        assertFalse(ConfigWriteTransaction.write(mismatch, old, replacement))
        assertArrayEquals(old, mismatch.saved)
    }

    @Test fun `cancellation after write starts still reads back and then propagates`() = runBlocking {
        val channel = Fake()
        try {
            coroutineScope {
                val parent = currentCoroutineContext()
                channel.cancelWrite = { parent.cancel() }
                ConfigWriteTransaction.write(channel, old, replacement)
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(listOf("prepare", "read", "backup", "read", "write", "read"), channel.events)
        assertArrayEquals(old, channel.saved)
    }
}
