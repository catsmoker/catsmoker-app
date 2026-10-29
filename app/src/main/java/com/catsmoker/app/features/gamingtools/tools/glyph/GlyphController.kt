package com.catsmoker.app.features.gamingtools.tools.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Which Glyph hardware one Nothing model carries, resolved from `Build.MODEL`.
 *
 * The device ids and channel indices come from the GDK itself (`Glyph.DEVICE_*`,
 * `Glyph.Code_*`), never from a table copied out of a ROM: `displayProgress` validates the
 * channel it is handed against the registered device, and a hand-written index would light
 * the wrong segment (or throw) after a firmware reshuffle.
 */
internal sealed interface GlyphHardware {

    /** The id the GDK registers the session with. */
    val deviceId: String

    /**
     * A Glyph strip model. [channel] is the single segment the loading bar drives — the SDK
     * requires exactly one of D1 (Phone 1/2), C (Phone 2a/3a) or A (Phone 4a/4b).
     */
    data class Strip(
        override val deviceId: String,
        val channel: Int,
        /**
         * Whether `displayProgress` understands this model.
         *
         * The GDK has a branch for every strip except the 2a Plus: registering `DEVICE_23113`
         * leaves its internal segment index at 0 and paints nothing (no throw, no bar). Lighting
         * the channel for the length of the run is the honest fallback there; there is nothing
         * wrong with the hardware, the SDK simply has no progress path for it in 2.0.
         */
        val supportsProgress: Boolean = true
    ) : GlyphHardware

    /** A Glyph Matrix model (no strip): [size] is the matrix edge in LEDs (25 or 13). */
    data class Matrix(override val deviceId: String, val size: Int) : GlyphHardware

    companion object {
        /**
         * Model codes are the ones the GDK's own `Common.is20xxx()` checks compare, so this
         * resolver cannot drift from what `register()` accepts:
         * A063 = Phone (1), A065/AIN065 = Phone (2), A142 = Phone (2a),
         * A142P = Phone (2a) Plus, A059… = Phone (3a)/(3a) Pro, A024… = Phone (3),
         * A069 = Phone (4a), A069P = Phone (4a) Pro, A009P… = Phone (4b).
         *
         * Pure and null-tolerant so the mapping is unit-testable without a Nothing device.
         */
        fun forModel(model: String?): GlyphHardware? {
            val name = model.orEmpty()
            if (name.isEmpty()) return null
            return when {
                name == Glyph.DEVICE_20111 ->
                    Strip(Glyph.DEVICE_20111, Glyph.Code_20111.D1_1)
                name == Glyph.DEVICE_22111 || name == Glyph.DEVICE_22111I ->
                    Strip(Glyph.DEVICE_22111, Glyph.Code_22111.C1_1)
                name == Glyph.DEVICE_23111 ->
                    Strip(Glyph.DEVICE_23111, Glyph.Code_23111.C_1)
                // Phone (2a) Plus registers under its own id (the GDK's `is23113()` only accepts
                // that one) but lights the shared C array; `displayProgress` has no 23113 branch.
                name == Glyph.DEVICE_23113 ->
                    Strip(Glyph.DEVICE_23113, Glyph.Code_23111.C_1, supportsProgress = false)
                name.contains(Glyph.DEVICE_24111) ->
                    Strip(Glyph.DEVICE_24111, Glyph.Code_24111.C_1)
                name.contains(Glyph.DEVICE_23112) ->
                    Matrix(Glyph.DEVICE_23112, Glyph.DEVICE_23112_MATRIX_LENGTH)
                name == Glyph.DEVICE_25111 ->
                    Strip(Glyph.DEVICE_25111, Glyph.Code_25111.A_1)
                name == Glyph.DEVICE_25111p ->
                    Matrix(Glyph.DEVICE_25111p, Glyph.DEVICE_25111p_MATRIX_LENGTH)
                name.contains(Glyph.DEVICE_25131) ->
                    Strip(Glyph.DEVICE_25131, Glyph.Code_25131.A_1)
                else -> null
            }
        }
    }
}

/**
 * The Gaming Mode loading bar, mirrored onto the Glyph Interface through Nothing's Glyph
 * Developer Kit (GDK).
 *
 * This is a *display* feature only: it never changes what Gaming Mode applies, and every call
 * is a silent no-op on a phone that is not a Nothing model the GDK knows — the app must not
 * crash, request the Nothing service, or hold a binding on a Xiaomi/Samsung/Pixel.
 *
 * It mirrors both directions: a fill while Gaming Mode enables, and a timed drain back to 0 %
 * while it disables. The drain re-opens the session when the ON run already closed it, and it
 * is driven by elapsed time rather than by the engine, which reports `Disabling` without a
 * progress value.
 *
 * Contract, in the GDK's own words: `init()` binds the proxy service, `register(device)` grants
 * this app (or refuses it, when the `NothingKey` meta-data is missing and the debug switch is
 * off), and a control session must be `openSession()`ed before any frame is shown and
 * `closeSession()`ed afterwards. `unInit()` unbinds — and the AAR unbinds unconditionally, so
 * it is only ever called after a successful [ensureInitialized], inside a try/catch.
 *
 * Main-thread only: the ViewModel drives it from `viewModelScope` (Main) and the GDK calls its
 * callbacks on the main looper too.
 */
@Singleton
class GlyphController @Inject constructor(@ApplicationContext context: Context) {

    private val appContext: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /**
     * Resolved once: `Build.MODEL` is immutable for the process. The manufacturer/API gate is
     * checked *before* the GDK classes are touched, so a non-Nothing phone never even evaluates
     * the device table.
     */
    private val hardware: GlyphHardware? =
        if (isNothingBuild() && Build.VERSION.SDK_INT >= GDK_MIN_SDK) {
            GlyphHardware.forModel(Build.MODEL)
        } else {
            null
        }

    /** Whether the "Sync Gaming Mode with Glyph" control should be offered at all. */
    val isSupported: Boolean get() = hardware != null

    /**
     * True while the shutdown drain owns the lights.
     *
     * The engine's `Disabling` is routinely shorter than the 1.5 s tween (a session with nothing
     * suspended reverts almost at once). The screen keeps the drain running for its own length so
     * the OFF animation always reads as a complete unwind rather than being cut at 60 %.
     */
    val isDraining: Boolean get() = phase == Phase.DRAINING

    private enum class Phase { IDLE, FILLING, DRAINING }

    private var phase = Phase.IDLE
    private var percent = 0
    private var initialized = false
    private var connected = false
    private var sessionOpen = false
    /** True while the no-progress fallback model has its segment held on. */
    private var lit = false
    /** Uptime and length of the current drain, so its ticks stay on wall time, not tick count. */
    private var drainStartedAt = 0L
    private var drainDurationMs = 0L
    private var strip: GlyphManager? = null
    private var matrix: GlyphMatrixManager? = null

    /**
     * One drain step: paints the percentage the elapsed fraction says is left, then either
     * schedules the next step or closes the session at zero.
     *
     * Posted on [main], so it is cancelled by the same `removeCallbacksAndMessages(null)` that
     * every other transition uses — a fill, a cancel or a teardown cannot leave a tick behind.
     */
    private val drainTick = object : Runnable {
        override fun run() {
            if (phase != Phase.DRAINING) return
            percent = drainedPercent(SystemClock.uptimeMillis() - drainStartedAt, drainDurationMs)
            render()
            if (percent <= 0) finishDrain() else main.postDelayed(this, DRAIN_TICK_MS)
        }
    }

    private val stripCallback = object : GlyphManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            connected = true
            try {
                val hw = hardware as? GlyphHardware.Strip ?: return
                val manager = strip ?: return
                if (!manager.register(hw.deviceId)) {
                    // The GDK did not authorise this app: wrong/missing NothingKey, debug switch
                    // off, or the service itself refused. Nothing can be shown, so drop the
                    // binding instead of pretending a session exists.
                    Log.w(TAG, "GDK refused registration for ${hw.deviceId}")
                    teardown()
                    return
                }
                manager.openSession()
                sessionOpen = true
                render()
            } catch (t: Throwable) {
                Log.w(TAG, "Glyph strip session failed", t)
                teardown()
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            // The proxy process went away. Keep the binding (the system reconnects and calls
            // onServiceConnected again, which re-registers); only the live session is gone.
            connected = false
            sessionOpen = false
        }
    }

    private val matrixCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            connected = true
            try {
                val hw = hardware as? GlyphHardware.Matrix ?: return
                val manager = matrix ?: return
                if (!manager.register(hw.deviceId)) {
                    Log.w(TAG, "GDK refused matrix registration for ${hw.deviceId}")
                    teardown()
                    return
                }
                render()
            } catch (t: Throwable) {
                Log.w(TAG, "Glyph matrix session failed", t)
                teardown()
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            connected = false
        }
    }

    /**
     * Starts mirroring a loading run. Idempotent, so the ViewModel may call it on every
     * `Enabling` emission without re-binding the service.
     *
     * A fill deliberately takes over a drain: the engine reports `Enabling` only after the user
     * asked for a new session, and the LEDs must follow that request rather than finish the
     * unwind of the previous one.
     */
    fun start() {
        if (!isSupported || phase == Phase.FILLING) return
        // A previous completion may still have its blink queued; this run owns the lights now.
        main.removeCallbacksAndMessages(null)
        phase = Phase.FILLING
        percent = 0
        ensureInitialized()
        render()
    }

    /**
     * Fills the strip (or prints the percentage on the matrix) to [percent]. Values outside
     * 0…100 are clamped: the GDK multiplies the number into a brightness ladder and does not
     * clamp it itself.
     */
    fun setProgress(percent: Int) {
        if (!isSupported || phase != Phase.FILLING) return
        this.percent = percent.coerceIn(0, 100)
        render()
    }

    /**
     * Finishes a run: a short two-step blink at 100 %, then the session is closed. The blink is
     * posted, not slept on, so the caller (the engine's coroutine) is never blocked.
     */
    fun complete() {
        if (!isSupported || phase != Phase.FILLING) return
        phase = Phase.IDLE
        percent = 100
        if (!initialized) return
        render()
        main.postDelayed({ turnOff() }, COMPLETION_BLINK_MS)
        main.postDelayed({ render() }, COMPLETION_BLINK_MS * 2)
        main.postDelayed({ teardown() }, COMPLETION_BLINK_MS * 3)
    }

    /**
     * The reverse of [start]: drains the lights from 100 % to 0 % while Gaming Mode disables.
     *
     * [durationMs] is supplied by the caller so the LEDs run on the same clock as the card's own
     * reverse tween (`GamingModeProgress.REVERSE_DURATION_MS`) instead of on a second, drifting
     * constant. The engine reports `Disabling` without a progress value, which is exactly why
     * this is a timed drain rather than a per-step mapping.
     *
     * Everything is wiped first — a queued completion blink, a fill tick, a previous drain — and
     * the session is re-opened when it was closed after the ON run: the GDK allows one session
     * at a time, so a drain that assumed one was still open would paint nothing.
     */
    fun startReverse(durationMs: Long = DEFAULT_DRAIN_MS) {
        if (!isSupported || phase == Phase.DRAINING) return
        main.removeCallbacksAndMessages(null)
        phase = Phase.DRAINING
        percent = 100
        drainStartedAt = SystemClock.uptimeMillis()
        drainDurationMs = durationMs.coerceAtLeast(1L)
        ensureInitialized()
        render()
        main.postDelayed(drainTick, DRAIN_TICK_MS)
    }

    /** Aborts a run and clears the lights immediately (cancel, error, revert, screen stop). */
    fun cancel() {
        if (!isSupported) return
        phase = Phase.IDLE
        teardown()
    }

    /** Last call: closes the session, unbinds the GDK service and drops every queued frame. */
    fun release() = cancel()

    private fun ensureInitialized() {
        if (initialized) return
        val hw = hardware ?: return
        // Set before init(): the callback can fire before bindService returns on some builds.
        initialized = true
        try {
            when (hw) {
                is GlyphHardware.Strip -> {
                    val manager = GlyphManager.getInstance(appContext)
                    strip = manager
                    manager.init(stripCallback)
                }
                is GlyphHardware.Matrix -> {
                    val manager = GlyphMatrixManager.getInstance(appContext)
                    matrix = manager
                    manager.init(matrixCallback)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Glyph init failed", t)
            initialized = false
            strip = null
            matrix = null
        }
    }

    /** Paints the current percentage. Silently does nothing until the service is connected. */
    private fun render() {
        if (!initialized || !connected) return
        try {
            when (val hw = hardware) {
                is GlyphHardware.Strip -> {
                    val manager = strip ?: return
                    // Null when the session is not registered (the GDK returns null rather
                    // than throwing), which is the same "nothing to show" path.
                    val builder = manager.getGlyphFrameBuilder() ?: return
                    val frame = builder.buildChannel(hw.channel).build()
                    if (hw.supportsProgress) {
                        manager.displayProgress(frame, percent)
                    } else if (!lit) {
                        // No progress path for this model: hold the channel on for the run. Once
                        // per run, because toggle() shows one frame *instead of* the previous one
                        // and re-toggling the same frame can just blink it.
                        manager.toggle(frame)
                        lit = true
                    }
                }
                is GlyphHardware.Matrix -> {
                    val manager = matrix ?: return
                    // Text rendering keeps the percentage readable on the 25×25 and 13×13
                    // matrices; the SDK bundles the Nothing NType font and scales it itself.
                    val label = GlyphMatrixObject.Builder().setText("$percent%").build()
                    val frame = GlyphMatrixFrame.Builder().addMid(label).build(appContext)
                    manager.setMatrixFrame(frame)
                }
                null -> Unit
            }
        } catch (t: Throwable) {
            // Frame rejected (session closed by another app, channel mismatch on an unknown
            // firmware): the loading bar must keep working, so this stays a display-only loss.
            Log.w(TAG, "Glyph frame skipped", t)
        }
    }

    /**
     * Ends a drain at 0 %: the session is closed and the service unbound, exactly as a finished
     * run leaves it. The last frame is already empty, so `turnOff()` only confirms the dark state
     * rather than cutting a lit bar short.
     */
    private fun finishDrain() {
        phase = Phase.IDLE
        percent = 0
        teardown()
    }

    private fun turnOff() {
        lit = false
        try {
            strip?.turnOff()
        } catch (_: Throwable) {
        }
        try {
            matrix?.turnOff()
        } catch (_: Throwable) {
        }
    }

    /**
     * Closes the session and unbinds. Every call is guarded: the GDK's `unInit()` unbinds
     * without checking whether `bindService` ever succeeded, and `closeSession()` throws when
     * the service is already gone.
     */
    private fun teardown() {
        main.removeCallbacksAndMessages(null)
        turnOff()
        if (sessionOpen) {
            try {
                strip?.closeSession()
            } catch (_: Throwable) {
            }
            sessionOpen = false
        }
        if (initialized) {
            try {
                strip?.unInit()
            } catch (_: Throwable) {
            }
            try {
                matrix?.unInit()
            } catch (_: Throwable) {
            }
        }
        strip = null
        matrix = null
        initialized = false
        connected = false
    }

    private companion object {
        const val TAG = "GlyphController"

        /**
         * The AAR itself declares `minSdkVersion 33` (its manifest is merged with
         * `tools:overrideLibrary`), and Nothing documents the SDK as Android 14+. Holding the
         * line at 34 means an Android 12 Nothing device never enters the SDK at all.
         */
        const val GDK_MIN_SDK = 34

        /** One blink step: on for 180 ms, off 180 ms, then the session is closed. */
        const val COMPLETION_BLINK_MS = 180L

        /**
         * Frame interval of the drain. ~16 fps over 1.5 s is smooth on an LED strip and cheap
         * enough that a 60 ms handler tick costs nothing next to the engine's own work.
         */
        const val DRAIN_TICK_MS = 60L

        /** Fallback length when a caller does not pass the card's reverse-tween duration. */
        const val DEFAULT_DRAIN_MS = 1_500L

        fun isNothingBuild(): Boolean =
            "Nothing".equals(Build.MANUFACTURER, ignoreCase = true) ||
                "Nothing".equals(Build.BRAND, ignoreCase = true)
    }
}

/**
 * What the shutdown drain shows after [elapsedMs] of [durationMs]: 100 at the start, 0 at the end.
 *
 * Kept as a pure file-level function (not a member) so the ramp is unit-testable without a
 * Handler, a Looper or a Nothing phone. It is computed from elapsed wall time rather than by
 * subtracting a fixed step per tick: a tick-count ramp drifts with handler latency and freezes
 * for good if one tick is dropped on a busy main thread, leaving a lit bar behind.
 */
internal fun drainedPercent(elapsedMs: Long, durationMs: Long): Int {
    if (durationMs <= 0L) return 0
    val fraction = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    return (100f * (1f - fraction)).roundToInt().coerceIn(0, 100)
}
