package com.catsmoker.app.screens.editgamefiles.storage

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The same pinned channel is used throughout; a failed write never falls through to another file. */
object ConfigWriteTransaction {
    interface Channel {
        suspend fun prepare()
        suspend fun read(): ByteArray
        suspend fun backup(bytes: ByteArray): Boolean
        suspend fun write(bytes: ByteArray): Boolean
    }

    suspend fun write(channel: Channel, expected: ByteArray, replacement: ByteArray): Boolean {
        channel.prepare()
        val current = channel.read()
        require(current.contentEquals(expected)) { "The file changed. Reload before applying or restoring." }
        check(channel.backup(current)) { "Backup failed; no write was attempted." }
        check(channel.read().contentEquals(current)) { "The file changed during backup; no write was attempted." }
        currentCoroutineContext().ensureActive()
        // Once a write starts, finish its verification even if the panel is disposed.
        val verified = withContext(NonCancellable) {
            check(channel.write(replacement)) { "Write failed. The pre-write backup is available." }
            channel.read().contentEquals(replacement)
        }
        currentCoroutineContext().ensureActive()
        return verified
    }
}
