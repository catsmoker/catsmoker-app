package com.catsmoker.app.screens.editgamefiles.storage

import java.io.File
import java.security.MessageDigest

/** Legacy backups have no source identity and cannot safely be imported into this store. */
internal class ScopedConfigBackups(filesDir: File) {
    private val store = ConfigBackupStore(File(filesDir, "config_backups_scoped_v1"))

    data class Target(
        val packageName: String,
        val fileName: String,
        val source: Location
    ) {
        sealed interface Location {
            data class Direct(val path: String) : Location
            data class Document(val uri: String) : Location
        }

        fun key(): String {
            val (channel, address) = when (val location = source) {
                is Location.Direct -> "direct" to location.path
                is Location.Document -> "document" to location.uri
            }
            // Length-prefix the fields, and keep document identifiers out of backup paths.
            val identity = listOf(packageName, fileName, channel, address).joinToString("") { "${it.length}:$it" }
            return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    fun latest(target: Target): ConfigBackupStore.Entry? {
        val name = target.backupFileName()
        return store.list(target.packageName).firstOrNull { it.file.name.substringAfter('_') == name }
    }

    fun read(entry: ConfigBackupStore.Entry): ByteArray? = store.readBytes(entry)

    fun save(target: Target, bytes: ByteArray): Boolean =
        store.save(target.packageName, target.backupFileName(), bytes) != null

    private fun Target.backupFileName(): String = "${key()}_$fileName"
}
