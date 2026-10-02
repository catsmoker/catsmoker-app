package com.catsmoker.app.system.shell

/**
 * The in-app native bridge: a JNI library (`libcatsmoker_bridge.so`) built from
 * `app/src/main/jni/` and packaged inside this APK.
 *
 * This is the spike for the ladder's NDK rung (approved 2026-09-27): reference analysis
 * (M152) proved the Unity `set_targetFrameRate` chain is native-only
 * (`xdl_open libil2cpp` → resolve icall → call), unreachable from a pure-Java LSPosed
 * module — so any future unlock needs a `.so` the module can load, and this is where that
 * `.so` will live. Today the library exposes exactly one function: a build-identity string.
 * No game behavior, no Unity calls, no frame-rate writes ship with this spike; claiming a
 * capability from a successful `loadLibrary` would be the exit-code fallacy in native form.
 *
 * Honesty rules the API lives by:
 * - [isAvailable] is a load attempt, not a capability probe — true means "the `.so` for
 *   this ABI packaged and loaded", nothing more.
 * - [version] is the build identity the library reports about itself, or null when the
 *   library is absent (JVM unit-test hosts) or the call throws. Never a guessed string.
 */
object NativeBridge {

    /** True when the packaged `.so` for this ABI loaded. A load fact, not a capability claim. */
    fun isAvailable(): Boolean = available

    /** Build identity from the library itself, or null when it is absent or the call throws. */
    fun version(): String? =
        if (!available) null else runCatching { nativeVersion() }.getOrNull()

    /**
     * M152 read probe: the Unity `Application.targetFrameRate` of *this* process, or a
     * negative stage code (never a guessed rate). No write of any kind.
     */
    fun unityTargetFrameRate(): Int =
        if (!available) NO_LIBRARY else runCatching { unityGetTargetFrameRate() }
            .getOrDefault(LIBRARY_CALL_FAILED)

    /**
     * M152 write half: invokes `Application.set_targetFrameRate(fps)` in *this* process.
     *
     * Returns 1 when the setter was invoked, else a negative stage code. Invocation is
     * all this reports — the effect is proven by the frame cadence afterwards.
     * Peak values only (a measured panel peak, never a cap); no opcode patching, no
     * resolution writes. Blocks up to ~10s: call off the main thread.
     */
    fun unitySetTargetFrameRate(fps: Int): Int =
        if (!available) NO_LIBRARY else runCatching { nativeSetTargetFrameRate(fps) }
            .getOrDefault(LIBRARY_CALL_FAILED)

    private val available: Boolean =
        runCatching {
            System.loadLibrary("catsmoker_bridge")
            true
        }.getOrDefault(false)

    private external fun nativeVersion(): String

    private external fun unityGetTargetFrameRate(): Int

    private external fun nativeSetTargetFrameRate(fps: Int): Int

    /** [unityTargetFrameRate] when this process has no bridge library (JVM hosts). */
    const val NO_LIBRARY = -4

    /** [unityTargetFrameRate] when the library is present but the call itself threw. */
    const val LIBRARY_CALL_FAILED = -5

    /** Native: libil2cpp.so never appeared — not a Unity/IL2CPP process, clean abort. */
    const val NO_IL2CPP = -1

    /** Native: il2cpp_resolve_icall missing — incompatible IL2CPP build, clean abort. */
    const val NO_RESOLVER = -2

    /** Native: get_targetFrameRate icall missing — clean abort. */
    const val NO_GETTER = -3
}
