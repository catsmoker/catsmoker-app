package com.catsmoker.app.screens.editgamefiles.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.catsmoker.app.platform.shell.ShellRunner
import com.catsmoker.app.screens.editgamefiles.model.ConfigEditorSpec
import com.catsmoker.app.screens.editgamefiles.model.EditableConfigFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AdditionalConfigStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner
) {
    sealed interface Source {
        data class Root(val path: String) : Source
        data class Shizuku(val path: String) : Source
        data class Document(val uri: Uri) : Source
    }

    data class Loaded(val source: Source, val bytes: ByteArray, val document: EditableConfigDocument)

    private val backups = ConfigBackupStore(context)
    private val mutex = Mutex()

    suspend fun load(file: EditableConfigFile, uri: Uri? = null): Loaded = withContext(Dispatchers.IO) {
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            require(name == file.name) { "Select the existing ${file.name}, not a different config or save file." }
        }
        val source = when {
            uri != null -> Source.Document(uri)
            file.paths.isEmpty() -> error("Select an existing document; this game's storage path varies by version.")
            shellRunner.isRootAvailable() -> Source.Root(file.paths.single())
            file.rootOnly -> error("Direct access to this game's private data requires root, not shell-UID Shizuku.")
            else -> Source.Shizuku(file.paths.single())
        }
        val bytes = read(source)
        Loaded(source, bytes, EditableConfigDocument.parse(bytes, file))
    }

    suspend fun save(
        spec: ConfigEditorSpec,
        file: EditableConfigFile,
        loaded: Loaded,
        edits: Map<String, String>?,
        gameClosed: Boolean
    ): Loaded = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(gameClosed) { "Close the game and confirm before writing." }
            val replacement = if (edits != null) {
                require(edits.all { (id, value) -> file.fields.singleOrNull { it.id == id }?.acceptsEdit(value) == true }) {
                    "This control has no established value mapping or FPS-unlock semantics; no write was attempted."
                }
                loaded.document.patch(edits)
            } else {
                val entry = backups.list(spec.packageName).firstOrNull {
                    it.file.name.substringAfter('_') == file.name
                } ?: error("No backup for this file yet.")
                val bytes = backups.readBytes(entry) ?: error("Could not read the backup.")
                EditableConfigDocument.parse(bytes, file)
                bytes
            }
            val channel = object : ConfigWriteTransaction.Channel {
                override suspend fun prepare() {
                    if (loaded.source !is Source.Document) {
                        check(shellRunner.execSafeResult("am", "force-stop", spec.packageName).isSuccess) {
                            "Could not stop the game; no write was attempted."
                        }
                    }
                }

                override suspend fun read() = this@AdditionalConfigStorage.read(loaded.source)
                override suspend fun backup(bytes: ByteArray) = backups.save(spec.packageName, file.name, bytes) != null
                override suspend fun write(bytes: ByteArray) = this@AdditionalConfigStorage.write(loaded.source, bytes)
            }
            check(ConfigWriteTransaction.write(channel, loaded.bytes, replacement)) {
                "Read-back did not match. The pre-write backup is available; reload before retrying."
            }
            Loaded(loaded.source, replacement, EditableConfigDocument.parse(replacement, file))
        }
    }

    private suspend fun read(source: Source): ByteArray {
        val bytes = when (source) {
            is Source.Document -> context.contentResolver.openInputStream(source.uri)?.use { stream ->
                stream.readBytesBounded()
            } ?: error("Could not open the selected document.")
            is Source.Root -> {
                val size = rootCommand("stat", "-c", "%s", source.path)
                check(size.isSuccess && size.stdout.trim().toLongOrNull() in 1L..EditableConfigDocument.MAX_BYTES.toLong()) {
                    "Game config is missing, unreadable or too large. Launch the game once, then close it."
                }
                val encoded = rootCommand("base64", source.path)
                check(encoded.isSuccess) { "Could not read the game config with root." }
                Base64.getMimeDecoder().decode(encoded.stdout)
            }
            is Source.Shizuku -> shellRunner.readFileDirect(source.path)
                ?: error("Shizuku access is unavailable. Grant access or select a document copy.")
        }
        require(bytes.isNotEmpty() && bytes.size <= EditableConfigDocument.MAX_BYTES) { "Config is empty or too large." }
        return bytes
    }

    private suspend fun write(source: Source, bytes: ByteArray): Boolean = when (source) {
        is Source.Document -> {
            context.contentResolver.openOutputStream(source.uri, "wt")?.use { it.write(bytes) } != null
        }
        is Source.Shizuku -> shellRunner.writeFileDirect(source.path, bytes) == true
        is Source.Root -> {
            val staged = File.createTempFile("game-config-", ".tmp", context.cacheDir)
            try {
                staged.writeBytes(bytes)
                check(rootCommand("test", "-f", source.path).isSuccess) { "Target disappeared; refusing to create a new config." }
                // Overwrite the existing inode: don't replace its owner/mode with app-cache metadata.
                rootCommand("cp", staged.absolutePath, source.path).isSuccess
            } finally {
                staged.delete()
            }
        }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(out.size() + count <= EditableConfigDocument.MAX_BYTES) { "Config is larger than 1 MiB." }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    /** A lost root session must not silently downgrade private-data operations to shell UID. */
    private suspend fun rootCommand(vararg args: String): ShellRunner.ExecResult {
        check(shellRunner.isRootAvailable()) { "Root access was lost; reload before retrying." }
        return shellRunner.execSafeResult("sh", "-c", "[ \"\$(id -u)\" = 0 ] || exit 1; exec \"\$@\"", "catsmoker", *args)
    }
}
