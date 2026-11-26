package com.catsmoker.app.screens.gaming.cache

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import com.catsmoker.app.screens.gaming.GameSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Independent, app-built experiment using the same page cache as vmtouch -t.
 * Bounded sequential reads replace native pointer touching; no downloaded executables, mlock or -e.
 * Only regular, directly readable files are admitted: privilege never widens this sandbox.
 */
@Singleton
class GameCachePreloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: GameSettingsStore
) {
    private val prefs = context.getSharedPreferences("game_cache_experiment", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(runCatching {
        JSONObject(prefs.getString("last", null) ?: """{"result":"not_run"}""").apply {
            if (optString("result") == "running") put("result", "interrupted")
        }.toString()
    }.getOrDefault("""{"result":"unknown"}"""))
    val status = _status.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var active: Job? = null

    fun cancel() { active?.cancel() }

    suspend fun preload(pkg: String): String = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext """{"result":"busy"}"""
        try {
            preloadLocked(pkg)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            publish(JSONObject().put("result", "failed").put("game", pkg))
        } finally {
            active = null
            mutex.unlock()
        }
    }

    private suspend fun preloadLocked(pkg: String): String {
        val desired = settings.read(pkg)
        if (!desired.cachePreload) return publish(JSONObject().put("result", "disabled").put("game", pkg))
        val job = currentCoroutineContext()[Job]
        active = job
        val begin = SystemClock.elapsedRealtime()
        val cpuBegin = Process.getElapsedCpuTime()
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        fun available(): Long { manager.getMemoryInfo(memory); return memory.availMem }
        val beforeRam = available()
        val budget = CacheReadBudget(
            desired.cacheBudgetMiB.coerceIn(8, 64) * 1024L * 1024,
            begin + 3000, SystemClock::elapsedRealtime,
            { available(); !memory.lowMemory && memory.availMem > maxOf(256L * 1024 * 1024, memory.totalMem / 10) },
            { job?.ensureActive() }
        )
        val files = JSONArray()
        var failures = 0
        return try {
            publish(JSONObject().put("result", "running").put("game", pkg))
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            // Selected asset documents go first, then APKs. No arbitrary path/executable from an Intent.
            val candidates = desired.cacheDocuments.map(Uri::parse).toMutableList()
            candidates += listOfNotNull(info.sourceDir).plus(info.splitSourceDirs.orEmpty()).map { Uri.fromFile(File(it)) }
            val obb = File(android.os.Environment.getExternalStorageDirectory(), "Android/obb/$pkg")
            if (budget.canStartIo()) {
                val obbs = obb.listFiles()?.filter { it.isFile && it.extension.equals("obb", true) }.orEmpty()
                candidates += obbs.take(8).map(Uri::fromFile)
                if (obbs.isEmpty()) files.put(JSONObject().put("source", "OBB").put("result", "absent_or_inaccessible"))
            }
            for (uri in candidates.distinct().take(24)) {
                if (!budget.canStartIo()) break
                val row = JSONObject().put("source", if (uri.scheme == "content") "selected_document" else File(uri.path.orEmpty()).name)
                try {
                    val pfd = if (uri.scheme == "file") ParcelFileDescriptor.open(File(requireNotNull(uri.path)), ParcelFileDescriptor.MODE_READ_ONLY)
                        else context.contentResolver.openFileDescriptor(uri, "r")
                    requireNotNull(pfd).use { descriptor ->
                        if (!budget.canStartIo()) {
                            row.put("result", budget.stopReason)
                            return@use
                        }
                        val stat = Os.fstat(descriptor.fileDescriptor)
                        require(OsConstants.S_ISREG(stat.st_mode) && stat.st_size > 0) { "not_regular_file" }
                        val count = minOf(stat.st_size, budget.maxBytes - budget.bytesRead)
                        if (count <= 0) return@use
                        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE)
                        val pages = if (pageSize > 0) ((count + pageSize - 1) / pageSize).toInt() else null
                        // mincore can report synthetic all-resident pages for files we don't own.
                        // Mapping is optional instrumentation, never a prerequisite to warming.
                        val address = if (pages != null && stat.st_uid == Process.myUid()) runCatching {
                            Os.mmap(0, count, OsConstants.PROT_READ, OsConstants.MAP_SHARED, descriptor.fileDescriptor, 0)
                        }.getOrNull() else null
                        try {
                            fun residency(): Int? = if (address == null || pages == null) null else runCatching {
                                val vector = ByteArray(pages)
                                Os.mincore(address, count, vector)
                                vector.count { it.toInt() and 1 != 0 }
                            }.getOrNull()
                            val before = residency()
                            val read = ParcelFileDescriptor.AutoCloseInputStream(
                                ParcelFileDescriptor.dup(descriptor.fileDescriptor)
                            ).use { budget.read(it, count) }
                            val after = residency()
                            row.put("bytes", read).put("pages", pages ?: JSONObject.NULL)
                                .put("residentBefore", before ?: JSONObject.NULL)
                                .put("residentAfter", after ?: JSONObject.NULL)
                                .put("residencyReliable", before != null && after != null)
                                .put("result", if (read == count) "read" else budget.stopReason ?: "short_read")
                        } finally { if (address != null) Os.munmap(address, count) }
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) {
                    failures++
                    row.put("result", "inaccessible_or_unsupported")
                }
                files.put(row)
            }
            publish(JSONObject().put("result", budget.stopReason ?: if (budget.bytesRead == 0L) "no_readable_files" else if (failures > 0) "partial" else "completed")
                .put("game", pkg).put("bytesRead", budget.bytesRead).put("elapsedMs", SystemClock.elapsedRealtime() - begin)
                .put("cpuMs", Process.getElapsedCpuTime() - cpuBegin).put("availableBefore", beforeRam)
                .put("availableAfter", available()).put("files", files))
        } catch (cancelled: CancellationException) {
            publish(JSONObject().put("result", "cancelled").put("game", pkg).put("bytesRead", budget.bytesRead))
            // The cache operation has no system writes to roll back. Do not cancel mode restoration.
            throw cancelled
        } catch (_: Exception) {
            publish(JSONObject().put("result", "failed").put("game", pkg))
        } finally {
            active = null
        }
    }

    private fun publish(value: JSONObject): String = value.toString().also {
        _status.value = it
        prefs.edit().putString("last", it).apply()
    }
}
