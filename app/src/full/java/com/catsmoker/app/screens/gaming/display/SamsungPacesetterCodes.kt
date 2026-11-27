package com.catsmoker.app.screens.gaming.display

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Discovers Samsung's `forcePacesetter` / `resetForcedPacesetter` transaction numbers on the
 * firmware that is actually running.
 *
 * These calls are not in AOSP — Samsung adds them to `android.gui.ISurfaceComposer`, so their
 * codes are their declaration index in *its* interface, and that index moves whenever the
 * vendor branch adds or removes a method. A hardcoded number would therefore be reassigned to
 * an unrelated method after a firmware update, which is the failure mode this class exists to
 * prevent: the number is read out of the client stub in `libgui.so` instead.
 *
 * Two shape checks decide whether the result is trustworthy enough to send a real transaction:
 * each stub must load exactly one transaction constant, and the two must be consecutive, as
 * AIDL lays out methods declared next to each other. Anything else is reported as "this
 * firmware has no pacesetter control" rather than guessed at.
 */
object SamsungPacesetterCodes {

    /** @param force transaction number for `forcePacesetter(long)`; `reset` for the nullary one. */
    data class Codes(val force: Int, val reset: Int)

    private const val LIBRARY_PATH = "/system/lib64/libgui.so"
    private const val FORCE_SYMBOL = "forcePacesetter"
    private const val RESET_SYMBOL = "resetForcedPacesetter"
    private const val PROXY_PREFIX = "BpSurfaceComposer"

    @Volatile
    private var cached: Codes? = null

    @Volatile
    private var attempted = false

    /** The verified pair, or null when this firmware does not have both calls. */
    fun resolve(): Codes? {
        cached?.let { return it }
        if (attempted) return null
        attempted = true
        val codes = scan() ?: return null
        cached = codes
        return codes
    }

    private fun scan(): Codes? {
        val data = runCatching {
            RandomAccessFile(LIBRARY_PATH, "r").use { file ->
                file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.channel.size())
            }
        }.getOrNull() ?: return null

        val force = data.stubCode(FORCE_SYMBOL) ?: return null
        val reset = data.stubCode(RESET_SYMBOL) ?: return null

        // AIDL numbers consecutive methods consecutively; a pair that misses by anything else
        // means at least one of the two was misread.
        if (reset != force + 1) return null
        return Codes(force, reset)
    }

    /**
     * The single transaction constant the proxy stub for [method] loads into `w1`.
     *
     * The proxy class is matched by name fragment rather than an exact mangled name because a
     * vendor branch may change an unused parameter's type, which changes the mangling but not
     * the code that is loaded before `transact()`.
     */
    private fun ByteBuffer.stubCode(method: String): Int? {
        val location = AArch64Movz.findFunctionMatching(this) { name ->
            name.contains(PROXY_PREFIX) && name.contains(method)
        } ?: return null
        return AArch64Movz.uniqueTransactionCode(this, location)
    }
}
