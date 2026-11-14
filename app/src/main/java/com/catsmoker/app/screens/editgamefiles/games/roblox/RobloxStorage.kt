package com.catsmoker.app.screens.editgamefiles.games.roblox

import android.content.Context
import android.util.AtomicFile
import com.catsmoker.app.platform.shell.ShellRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kept in noBackupFilesDir: recovery ownership must not migrate to a different device. */
internal class RobloxJournal(context: Context) : RobloxBackups {
    private val file = AtomicFile(File(context.noBackupFilesDir, "roblox_config_recovery.json"))

    override fun load(): RobloxBackup? = try {
        val bytes = file.openRead().use { input ->
            val bytes = input.readBytesBounded(100_000)
            require(bytes.size <= 100_000)
            bytes
        }
        val json = RobloxConfig.parseObject(bytes.toString(Charsets.UTF_8))
        RobloxConfig.gson.fromJson(json, RobloxBackup::class.java).also { it.validate() }
    } catch (error: FileNotFoundException) {
        if (file.baseFile.exists()) throw RobloxFailure(RobloxProblem.BACKUP, error)
        null
    } catch (error: Exception) {
        throw RobloxFailure(RobloxProblem.BACKUP, error)
    }

    override fun save(backup: RobloxBackup) {
        try {
            backup.validate()
            val stream = file.startWrite()
            try {
                stream.write(RobloxConfig.gson.toJson(backup).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
            if (load() != backup) throw RobloxFailure(RobloxProblem.BACKUP)
        } catch (error: Exception) {
            throw RobloxFailure(RobloxProblem.BACKUP, error)
        }
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (output.size() <= limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

internal class RobloxShellFiles(private val shell: ShellRunner) : RobloxFiles {
    override suspend fun read(): RobloxFile {
        requirePrivilege()
        return parse(run("$snapshotFunction\nsnapshot").stdout)
    }

    override suspend fun replace(expected: RobloxFile, desired: RobloxFile) {
        requirePrivilege()
        run(replaceScript(expected, desired))
    }

    private fun requirePrivilege() {
        if (!shell.hasPrivilege()) throw RobloxFailure(RobloxProblem.NO_PRIVILEGE)
    }

    private suspend fun run(script: String): ShellRunner.ExecResult {
        // libsu may reuse a shell: isolate exit/traps/pipefail, and never log JSON payloads.
        val result = shell.execResult("(\n$script\n)", redactCommand = true)
        if (!result.isSuccess) throw RobloxFailure(when (result.exitCode) {
            70 -> RobloxProblem.UNSAFE_FILE
            71 -> RobloxProblem.CONFLICT
            else -> RobloxProblem.IO
        })
        return result
    }

    companion object {
        internal fun parse(output: String): RobloxFile = try {
            if (output.trimEnd('\n', '\r') == "ABSENT") RobloxFile() else {
                val parts = output.trimEnd('\n', '\r').split('\n')
                require(parts.size in 4..5 && parts[0] == "PRESENT")
                RobloxFile(parts.getOrElse(4) { "" }, parts[1], parts[2].toInt(), parts[3].toInt())
                    .also { it.validate() }
            }
        } catch (error: Exception) {
            throw RobloxFailure(RobloxProblem.UNSAFE_FILE, error)
        }

        private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        private fun protocol(file: RobloxFile): String {
            file.validate()
            return if (file.encoded == null) "ABSENT"
            else "PRESENT\n${file.mode}\n${file.uid}\n${file.gid}\n${file.encoded}".trimEnd('\n')
        }

        // A failed read is never absence. Bounds, regular-file/link checks and stable stat
        // evidence prevent treating an unreadable or concurrently changing file as our backup.
        internal val snapshotFunction = """
            set -o pipefail || exit 70
            p=${quote(RobloxConfig.PATH)}
            snapshot() {
                [ -d /data/local/tmp ] && [ -r /data/local/tmp ] && [ -x /data/local/tmp ] || return 70
                [ ! -L "${'$'}p" ] || return 70
                if [ ! -e "${'$'}p" ]; then printf 'ABSENT'; return; fi
                [ -f "${'$'}p" ] && [ -r "${'$'}p" ] || return 70
                before=$(stat -c '%d:%i:%f:%u:%g:%h:%s:%y:%z' "${'$'}p") || return 70
                metadata=$(stat -c '%a %u %g %h %s' "${'$'}p") || return 70
                set -- ${'$'}metadata
                [ "$#" -eq 5 ] && [ "$4" -eq 1 ] && [ "$5" -le ${RobloxConfig.MAX_BYTES} ] || return 70
                mode=$1; owner=$2; group=$3
                content=$(head -c ${RobloxConfig.MAX_BYTES + 1} "${'$'}p" | base64) || return 70
                content=$(printf '%s' "${'$'}content" | tr -d '\r\n') || return 70
                after=$(stat -c '%d:%i:%f:%u:%g:%h:%s:%y:%z' "${'$'}p") || return 70
                [ "${'$'}before" = "${'$'}after" ] && [ ! -L "${'$'}p" ] || return 71
                printf 'PRESENT\n%s\n%s\n%s\n%s' "${'$'}mode" "${'$'}owner" "${'$'}group" "${'$'}content"
            }
        """.trimIndent()

        internal fun replaceScript(expected: RobloxFile, desired: RobloxFile): String {
            val expectedProtocol = quote(protocol(expected))
            desired.validate()
            val check = """
                current=$(snapshot) || exit $?
                [ "${'$'}current" = $expectedProtocol ] || exit 71
            """.trimIndent()
            if (desired.encoded == null) {
                return """
                    $snapshotFunction
                    $check
                    rm -f -- "${'$'}p" || exit 72
                """.trimIndent()
            }
            return """
                $snapshotFunction
                $check
                tmp=$(mktemp /data/local/tmp/.catsmoker-roblox.XXXXXX) || exit 72
                trap 'rm -f -- "${'$'}tmp"' EXIT
                printf '%s' ${quote(desired.encoded)} | base64 -d > "${'$'}tmp" || exit 72
                chmod ${quote(desired.mode)} "${'$'}tmp" || exit 72
                chown ${desired.uid}:${desired.gid} "${'$'}tmp" || exit 72
                if [ -e "${'$'}p" ]; then
                    oldlabel=$(ls -Zd "${'$'}p") || exit 70
                    newlabel=$(ls -Zd "${'$'}tmp") || exit 70
                    [ "${'$'}{oldlabel%% *}" = "${'$'}{newlabel%% *}" ] || exit 70
                fi
                $check
                mv -fT -- "${'$'}tmp" "${'$'}p" || exit 72
            """.trimIndent()
        }
    }
}

@Singleton
class RobloxManager @Inject constructor(
    @ApplicationContext context: Context,
    private val shell: ShellRunner
) {
    private val editor = RobloxEditor(RobloxShellFiles(shell), RobloxJournal(context))

    internal suspend fun status(): RobloxStatus = withContext(Dispatchers.IO) { editor.status() }
    internal suspend fun apply(fps: Int, expected: RobloxFile) = withContext(Dispatchers.IO) {
        editor.apply(fps, expected)
    }
    internal suspend fun restore(expected: RobloxFile) = withContext(Dispatchers.IO) { editor.restore(expected) }

    internal suspend fun closeGame() = withContext(Dispatchers.IO) {
        if (!shell.hasPrivilege()) throw RobloxFailure(RobloxProblem.NO_PRIVILEGE)
        if (!shell.execSafeResult("am", "force-stop", RobloxConfig.PACKAGE).isSuccess) {
            throw RobloxFailure(RobloxProblem.IO)
        }
    }
}
