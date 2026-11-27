package com.catsmoker.app.screens.gaming.display

import android.os.IBinder
import android.os.Parcel
import org.json.JSONObject

/**
 * The privileged display operations, run inside a `shell`-UID process.
 *
 * Why a separate entry point instead of an in-app call: SurfaceFlinger gates mode changes on
 * `ACCESS_SURFACE_FLINGER` (`SurfaceComposerAIDL::checkAccessPermission`). That permission is
 * `signature` level, held by the platform and by `com.android.shell` — so the app UID can
 * never hold it, while the `shell` identity Shizuku provides already does. The same identity
 * also gets *unrestricted* hidden-API reflection here, because classes loaded from the
 * process classpath sit in the boot domain rather than the app domain.
 *
 * This class therefore has two hard rules:
 *  1. It never assumes a transaction code or a method signature. Every entry point discovers
 *     what this firmware has and reports `unavailable` rather than guessing.
 *  2. It prints one JSON object on stdout and nothing else. The caller parses it, so a
 *     half-completed operation can never be mistaken for success by scraping prose.
 *
 * Invoked through `Shizuku.newProcess` as
 * `CLASSPATH=<base.apk> app_process /system/bin com.catsmoker.app.screens.gaming.display.DisplayShellMain <command> …`.
 */
object DisplayShellMain {

    private const val SURFACE_FLINGER_AIDL = "SurfaceFlingerAIDL"
    private const val SURFACE_FLINGER_LEGACY = "SurfaceFlinger"

    @JvmStatic
    fun main(args: Array<String>) {
        val reply = if (args.isEmpty()) {
            report("error", "no command")
        } else {
            runCatching { dispatch(args) }
                .getOrElse { report("error", it.javaClass.simpleName + ": " + (it.message ?: "")) }
        }
        println(reply.toString())
        System.out.flush()
        Runtime.getRuntime().halt(0)
    }

    private fun dispatch(args: Array<String>): JSONObject = when (args[0]) {
        "probe" -> probe()
        "mode-specs" -> modeSpecs(args)
        "pacesetter" -> pacesetter(args)
        "dex-highres" -> dexHighRes(args)
        else -> report("error", "unknown command ${args[0]}")
    }

    // ------------------------------------------------------------------ probe

    /**
     * Which mechanisms exist on this firmware and whether this identity can use them.
     *
     * Each answer is a lookup that either returns a callable thing or nothing; nothing here
     * proves a *write* will be accepted, which is why every write is separately read back.
     */
    private fun probe(): JSONObject {
        val out = report("ok", null)
        out.put("uid", android.os.Process.myUid())
        out.put("modeSpecs", SurfaceControlBridge.isAvailable())
        SurfaceControlBridge.availability().forEach { (name, present) -> out.put(name, present) }

        val codes = SamsungPacesetterCodes.resolve()
        out.put("pacesetter", codes != null)
        out.put("pacesetterForceCode", codes?.force ?: -1)
        out.put("pacesetterResetCode", codes?.reset ?: -1)

        out.put("dexHighRes", DexBridge.highResolutionCall() != null)
        out.put("qtiDisplayConfig", serviceManager(QTI_SERVICE) != null)
        out.put("schedulerDump", schedulerForcedPacesetter() != null)
        return out
    }

    // ------------------------------------------------------------------ mode specs

    /**
     * `setDesiredDisplayModeSpecs` on one display, pinning the primary range to
     * [args] `1` = mode id, `2` = minimal Hz, `3` = maximal Hz.
     *
     * The policy is edited on a freshly read copy and only the range fields it declares are
     * touched, because those structs gain and lose members between releases.
     */
    private fun modeSpecs(args: Array<String>): JSONObject {
        if (args.size < 4) return report("error", "usage: mode-specs <displayId> <modeId> <minHz> <maxHz>")
        val displayId = args[1].toLongOrNull() ?: return report("error", "bad display id")
        val modeId = args[2].toIntOrNull() ?: return report("error", "bad mode id")
        val minHz = args[3].toFloatOrNull() ?: return report("error", "bad min hz")
        val maxHz = args.getOrNull(4)?.toFloatOrNull() ?: minHz

        val specs = SurfaceControlBridge.applyModeSpecs(displayId, modeId, minHz, maxHz)
            ?: return report("error", "SurfaceControl.setDesiredDisplayModeSpecs is unavailable")
        return report("ok", null).put("mode", modeId).put("minHz", minHz).put("maxHz", maxHz)
            .put("readBack", specs)
    }

    // ------------------------------------------------------------------ pacesetter

    private fun pacesetter(args: Array<String>): JSONObject {
        val codes = SamsungPacesetterCodes.resolve()
            ?: return report("error", "this firmware has no forcePacesetter call")
        val reset = args[1] == "reset"
        val displayId = if (reset) 0L else {
            args.getOrNull(2)?.toLongOrNull() ?: return report("error", "usage: pacesetter force <displayId>|reset")
        }
        val code = if (reset) codes.reset else codes.force
        transactSurfaceFlinger(code, if (reset) null else displayId)
            ?: return report("error", "SurfaceFlinger refused the timing request")

        // SurfaceFlinger is the only authority on whether the request took: read its own record
        // back rather than trusting the transact return value alone.
        val forced = schedulerForcedPacesetter()
        return report("ok", null)
            .put("requested", if (reset) "reset" else displayId.toString())
            .put("forcedPacesetter", forced?.displayId?.toString() ?: "none")
            .put("verified", if (reset) forced?.displayId == null else forced?.displayId == displayId)
    }

    /**
     * Sends [code] to SurfaceFlinger's own binder.
     *
     * The descriptor is read from the live binder instead of being pasted in, so a renamed or
     * versioned interface still matches what the server registered.
     */
    private fun transactSurfaceFlinger(code: Int, displayId: Long?): Boolean? {
        val binder = surfaceFlinger() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(binder.interfaceDescriptor ?: return null)
            if (displayId != null) data.writeLong(displayId)
            if (!binder.transact(code, data, reply, 0)) return null
            reply.readException()
            true
        } catch (e: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun surfaceFlinger(): IBinder? =
        serviceManager(if (hasSurfaceFlingerAidl()) SURFACE_FLINGER_AIDL else SURFACE_FLINGER_LEGACY)

    private fun hasSurfaceFlingerAidl(): Boolean =
        serviceManager(SURFACE_FLINGER_AIDL) != null

    private fun serviceManager(name: String): IBinder? = runCatching {
        val manager = Class.forName("android.os.ServiceManager")
        manager.getMethod("getService", String::class.java).invoke(null, name) as? IBinder
    }.getOrNull()

    /**
     * SurfaceFlinger's own record of a forced pacesetter, from its scheduler dump.
     *
     * Null when this build's dump has no such field — which is also how the pacesetter calls
     * are known to be untrustworthy here, since there would be no way to verify them.
     */
    internal fun schedulerForcedPacesetter(): ForcedPacesetter? {
        val dump = runCatching {
            val process = ProcessBuilder("/system/bin/dumpsys", "SurfaceFlinger", "--scheduler")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().also { process.waitFor() }
        }.getOrNull() ?: return null
        for (line in dump.lineSequence()) {
            val text = line.trim()
            if (!text.startsWith(SCHEDULER_FORCED_KEY)) continue
            val value = text.substring(SCHEDULER_FORCED_KEY.length).trim()
            val id = value.toLongOrNull()
            return if (id == null) ForcedPacesetter(supported = true, displayId = null)
            else ForcedPacesetter(supported = true, displayId = id)
        }
        return null
    }

    /** @param displayId null when SurfaceFlinger reports nothing forced. */
    data class ForcedPacesetter(val supported: Boolean, val displayId: Long?)

    // ------------------------------------------------------------------ DeX

    private fun dexHighRes(args: Array<String>): JSONObject {
        val enabled = when (args.getOrNull(1)) {
            "1", "on", "true" -> true
            "0", "off", "false" -> false
            else -> return report("error", "usage: dex-highres <0|1>")
        }
        val call = DexBridge.highResolutionCall()
            ?: return report("error", "this build has no DeX high-resolution call")
        DexBridge.invoke(call, enabled)
            ?: return report("error", "the multi-tasking service refused the request")
        return report("ok", null).put("enabled", enabled)
    }

    // ------------------------------------------------------------------ output

    private fun report(result: String, reason: String?): JSONObject = JSONObject()
        .put("result", result)
        .apply { if (reason != null) put("reason", reason) }

    private const val SCHEDULER_FORCED_KEY = "forcedPacesetterDisplayId="
    /** Qualcomm's display-config service, probed for presence only. */
    private const val QTI_SERVICE = "vendor.qti.hardware.display.config.IDisplayConfig/default"
}
