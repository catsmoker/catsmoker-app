package com.catsmoker.app.screens.editgamefiles.games.roblox

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface RobloxFiles {
    suspend fun read(): RobloxFile
    suspend fun replace(expected: RobloxFile, desired: RobloxFile)
}

internal interface RobloxBackups {
    fun load(): RobloxBackup?
    fun save(backup: RobloxBackup)
}

internal data class RobloxBackup(
    val original: RobloxFile,
    val last: RobloxFile,
    val pending: RobloxFile? = null,
    val version: Int = 1
) {
    fun validate() {
        require(version == 1)
        requireNotNull(original).validate()
        requireNotNull(last).validate()
        pending?.validate()
    }

    fun owns(file: RobloxFile): Boolean = file == last || file == pending
}

internal data class RobloxStatus(
    val file: RobloxFile,
    val target: Int?,
    val hasBackup: Boolean,
    val canRestore: Boolean,
    val conflict: Boolean,
    val invalidConfig: Boolean
)

/** The journal precedes mutation, so a cancelled or interrupted write keeps both recovery states. */
internal class RobloxEditor(private val files: RobloxFiles, private val backups: RobloxBackups) {
    private val mutex = Mutex()

    suspend fun status(): RobloxStatus = mutex.withLock {
        val current = files.read()
        val backup = backups.load()
        val conflict = backup != null && !backup.owns(current)
        val target = try {
            RobloxConfig.target(current)
        } catch (_: RobloxFailure) {
            return@withLock RobloxStatus(current, null, backup != null,
                backup != null && !conflict && current != backup.original, conflict, true)
        }
        RobloxStatus(current, target, backup != null,
            backup != null && !conflict && current != backup.original, conflict, false)
    }

    suspend fun apply(fps: Int, expected: RobloxFile) = mutex.withLock {
        val current = files.read()
        if (current != expected) throw RobloxFailure(RobloxProblem.CONFLICT)
        val previous = backups.load()
        if (previous != null && !previous.owns(current)) throw RobloxFailure(RobloxProblem.CONFLICT)
        val desired = RobloxConfig.withTarget(current, fps)
        if (desired == current) return@withLock
        change(previous ?: RobloxBackup(current, current), current, desired)
    }

    suspend fun restore(expected: RobloxFile) = mutex.withLock {
        val current = files.read()
        val backup = backups.load() ?: throw RobloxFailure(RobloxProblem.BACKUP)
        if (current != expected || !backup.owns(current)) throw RobloxFailure(RobloxProblem.CONFLICT)
        if (current != backup.original) change(backup, current, backup.original)
    }

    private suspend fun change(backup: RobloxBackup, current: RobloxFile, desired: RobloxFile) {
        backups.save(backup.copy(last = current, pending = desired))
        files.replace(current, desired)
        if (files.read() != desired) throw RobloxFailure(RobloxProblem.VERIFY)
        backups.save(backup.copy(last = desired, pending = null))
    }
}
