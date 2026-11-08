package com.catsmoker.app.features.editgamefiles.service

import com.catsmoker.app.IFileService
import com.catsmoker.app.shizuku.CommandResult
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Runs inside the Shizuku user-service process (shell UID). Every method must drain both
 * stdout and stderr, otherwise a chatty command fills the 64 KB pipe buffer and blocks
 * forever, taking the binder thread with it.
 */
// Shizuku user-service entry point, instantiated by the Shizuku framework via reflection.
class FileService : IFileService.Stub {

    constructor() : super()

    override fun destroy() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun executeCommand(command: Array<out String>?): Int {
        if (command == null) return -1
        return try {
            val result = runProcess(command)
            result.exitCode
        } catch (_: Exception) {
            -1
        }
    }

    override fun executeAndGetOutput(command: Array<out String>?): MutableList<String> {
        if (command == null) return mutableListOf()
        val result = executeForResult(command)
        val text = result.output.ifBlank { "" }
        return if (text.isEmpty()) mutableListOf() else text.lines().toMutableList()
    }

    override fun executeForResult(command: Array<out String>?): CommandResult {
        val result = CommandResult()
        result.output = ""
        result.error = ""
        result.exitCode = -1
        if (command == null) return result

        return try {
            runProcess(command)
        } catch (e: Exception) {
            result.error = e.message ?: e.javaClass.simpleName
            result
        }
    }

    private fun runProcess(command: Array<out String>): CommandResult {
        val result = CommandResult()
        result.output = ""
        result.error = ""
        result.exitCode = -1
        val process = ProcessBuilder(*command).start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val stdoutReader = Thread {
            try {
                process.inputStream.bufferedReader().forEachLine { stdout.append(it).append('\n') }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val stderrReader = Thread {
            try {
                process.errorStream.bufferedReader().forEachLine { stderr.append(it).append('\n') }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            stdoutReader.join(DRAIN_JOIN_MILLIS)
            stderrReader.join(DRAIN_JOIN_MILLIS)
            result.exitCode = -1
            result.output = stdout.toString().trimEnd('\n')
            result.error = stderr.toString().trimEnd('\n')
            return result
        }

        stdoutReader.join(DRAIN_JOIN_MILLIS)
        stderrReader.join(DRAIN_JOIN_MILLIS)
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        result.output = stdout.toString().trimEnd('\n')
        result.error = stderr.toString().trimEnd('\n')
        result.exitCode = process.exitValue()
        return result
    }

    override fun readSysfsThermal(): String {
        val root = File("/sys/class/thermal")
        val zones = root.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") }
            ?: return ""
        val sb = StringBuilder()
        for (zone in zones.sortedBy { it.name }) {
            val type = readTrimmed(File(zone, "type")) ?: continue
            val lower = type.lowercase()
            if (lower.contains("trip") || lower.contains("threshold") || lower.contains("shutdown") || lower.contains("limit")) {
                continue
            }
            val temp = readTrimmed(File(zone, "temp")) ?: continue
            sb.append(type).append(':').append(temp).append('\n')
        }
        return sb.toString()
    }

    override fun readProcStat(): String = try {
        File("/proc/stat").readText()
    } catch (_: Exception) {
        ""
    }

    /**
     * Reads a whole file in one privileged process. Null means "not there / not readable" —
     * the caller distinguishes that from an empty file, which comes back as a zero-length
     * array. The file channel is opened inside this process (shell UID), so a save the game
     * wrote under its own ownership reads where the app's own uid cannot.
     */
    override fun readFile(path: String?): ByteArray? {
        if (path.isNullOrEmpty()) return null
        return try {
            File(path).takeIf { it.isFile }?.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Writes [data] over [path] in one privileged process. Every byte is flushed to storage
     * inside this call — getFD().sync() before close — so the binder reply is the report,
     * per the house rule that a write reports what storage holds, not what was sent.
     */
    override fun writeFile(path: String?, data: ByteArray?): Boolean {
        if (path.isNullOrEmpty() || data == null) return false
        return try {
            val file = File(path)
            file.parentFile?.let { parent -> if (!parent.isDirectory) parent.mkdirs() }
            FileOutputStream(file).use { out ->
                out.write(data)
                out.flush()
                out.fd.sync()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readTrimmed(file: File): String? = try {
        if (file.canRead()) file.readText().trim().ifEmpty { null } else null
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 60L
        const val DRAIN_JOIN_MILLIS = 1000L
    }
}
