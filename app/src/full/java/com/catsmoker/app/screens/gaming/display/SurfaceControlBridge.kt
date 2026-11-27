package com.catsmoker.app.screens.gaming.display

import android.os.IBinder
import java.lang.reflect.Method

/**
 * The composer's own display-mode policy, reached through the hidden `SurfaceControl` statics.
 *
 * `setDesiredDisplayModeSpecs` is the documented-behind-a-permission way to tell SurfaceFlinger
 * which modes a display may choose from, including the mode id to default to. Unlike the
 * settings keys — which are a *request* that the display manager may override — this is the
 * policy the composer actually votes with, which is why it is the only route that can hold an
 * external display on a specific mode.
 *
 * Everything here is looked up at runtime. Two Android releases have already changed the spec
 * struct's shape, so the code edits the fields it finds on a freshly read copy instead of
 * constructing a spec from a remembered signature.
 */
object SurfaceControlBridge {

    private const val SURFACE_CONTROL = "android.view.SurfaceControl"

    private val surfaceControl: Class<*>? by lazy {
        runCatching { Class.forName(SURFACE_CONTROL) }.getOrNull()
    }

    private val getPhysicalDisplayIds: Method? by lazy {
        surfaceControl?.method("getPhysicalDisplayIds")
    }

    private val getPhysicalDisplayToken: Method? by lazy {
        surfaceControl?.method("getPhysicalDisplayToken", java.lang.Long.TYPE)
    }

    private val getDesiredDisplayModeSpecs: Method? by lazy {
        surfaceControl?.methods?.firstOrNull {
            it.name == "getDesiredDisplayModeSpecs" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == IBinder::class.java
        }
    }

    private val setDesiredDisplayModeSpecs: Method? by lazy {
        surfaceControl?.methods?.firstOrNull {
            it.name == "setDesiredDisplayModeSpecs" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == IBinder::class.java
        }
    }

    /**
     * The parts of the read/modify/write triple this build exposes, by name.
     *
     * Reported one by one rather than as a single boolean because they are removed on
     * different releases: `getPhysicalDisplayIds`/`getPhysicalDisplayToken` were the only way
     * to name a display token and are absent from newer AOSP `SurfaceControl`, while the two
     * policy calls remain. Knowing which is missing is what tells a user whether a firmware
     * update took the composer path away or whether it never had it.
     */
    fun availability(): Map<String, Boolean> = mapOf(
        "displayIds" to (getPhysicalDisplayIds != null),
        "displayToken" to (getPhysicalDisplayToken != null),
        "getSpecs" to (getDesiredDisplayModeSpecs != null),
        "setSpecs" to (setDesiredDisplayModeSpecs != null)
    )

    /** Whether this build exposes the whole read/modify/write triple. */
    fun isAvailable(): Boolean = availability().values.all { it }

    /** The ids of the physical displays the composer manages. */
    fun physicalDisplayIds(): LongArray? = runCatching {
        getPhysicalDisplayIds?.invoke(null) as? LongArray
    }.getOrNull()

    /**
     * Pins [displayId] to [modeId] with a [minHz]..[maxHz] range and returns a description of
     * what the composer reports afterwards.
     *
     * The range is the part that lifts a vendor's ceiling: the composer votes
     * `max(min, peak)`, so a minimum at the wanted rate is what holds it there. Passing the
     * same value twice requests exactly one rate.
     *
     * @return the read-back description, or null when the write could not be attempted or was
     *   refused. Null is a refusal, never a silent success.
     */
    fun applyModeSpecs(displayId: Long, modeId: Int, minHz: Float, maxHz: Float): String? {
        if (!isAvailable()) return null
        val token = runCatching {
            getPhysicalDisplayToken?.invoke(null, displayId) as? IBinder
        }.getOrNull() ?: return null

        // Edit a freshly read copy: fields this code does not know about keep their value.
        val specs = runCatching {
            getDesiredDisplayModeSpecs?.invoke(null, token)
        }.getOrNull() ?: return null

        setDefaultMode(specs, modeId)
        applyRange(specs, minHz, maxHz)

        runCatching { setDesiredDisplayModeSpecs?.invoke(null, token, specs) }.getOrNull()
            ?: return null
        return describe(specs)
    }

    /** The composer's current policy for [displayId], as a description, or null. */
    fun currentSpecs(displayId: Long): String? {
        if (getDesiredDisplayModeSpecs == null) return null
        val token = runCatching {
            getPhysicalDisplayToken?.invoke(null, displayId) as? IBinder
        }.getOrNull() ?: return null
        val specs = runCatching { getDesiredDisplayModeSpecs?.invoke(null, token) }.getOrNull()
            ?: return null
        return describe(specs)
    }

    // ------------------------------------------------------------------ field editing

    /** Sets the default mode id on [specs]; a no-op when this build names the field differently. */
    private fun setDefaultMode(specs: Any, modeId: Int) {
        for (name in DEFAULT_MODE_NAMES) {
            val field = runCatching { specs.javaClass.getDeclaredField(name) }.getOrNull() ?: continue
            field.isAccessible = true
            when {
                field.type == java.lang.Integer.TYPE -> runCatching { field.setInt(specs, modeId) }
                // A wrapper (DisplayModeId in newer builds): reuse the instance and set its value.
                field.type == java.lang.Long.TYPE -> runCatching { field.setLong(specs, modeId.toLong()) }
                else -> {
                    val inner = runCatching { field.get(specs) }.getOrNull() ?: continue
                    runCatching {
                        inner.javaClass.methods.firstOrNull {
                            it.parameterTypes.size == 1 && it.parameterTypes[0] == java.lang.Integer.TYPE
                        }?.invoke(inner, modeId)
                    }
                }
            }
        }
    }

    /**
     * Clamps the request and primary ranges to [minHz]..[maxHz].
     *
     * Both are set: the primary range is what the composer may choose from on its own, and
     * the app-request range bounds what an application may ask for. Setting only one leaves
     * the other free to override the choice.
     */
    private fun applyRange(specs: Any, minHz: Float, maxHz: Float) {
        for (name in RANGE_FIELD_NAMES) {
            val field = runCatching { specs.javaClass.getDeclaredField(name) }.getOrNull() ?: continue
            field.isAccessible = true
            val range = runCatching { field.get(specs) }.getOrNull() ?: continue
            setFps(range, MIN_FIELD_NAMES, minHz)
            setFps(range, MAX_FIELD_NAMES, maxHz)
        }
    }

    /** Writes one end of a range, whether it is stored as a float or as an `Fps` wrapper. */
    private fun setFps(range: Any, names: List<String>, value: Float) {
        for (name in names) {
            val field = runCatching { range.javaClass.getDeclaredField(name) }.getOrNull() ?: continue
            field.isAccessible = true
            if (field.type == java.lang.Float.TYPE) {
                runCatching { field.setFloat(range, value) }.let { return }
            }
            val inner = runCatching { field.get(range) }.getOrNull() ?: continue
            // An immutable wrapper: replace it with one built from the value.
            val replacement = runCatching {
                inner.javaClass.methods.firstOrNull {
                    it.name == "fromValue" && it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == java.lang.Float.TYPE
                }?.invoke(null, value)
            }.getOrNull()
            if (replacement != null) {
                runCatching { field.set(range, replacement) }.let { return }
            }
            runCatching {
                inner.javaClass.methods.firstOrNull {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == java.lang.Float.TYPE
                }?.invoke(inner, value)
            }.let { return }
        }
    }

    /**
     * Reads the range ends back out of [specs], so the caller can report what the composer is
     * actually holding rather than what was requested.
     */
    private fun describe(specs: Any): String? {
        val range = RANGE_FIELD_NAMES.firstNotNullOfOrNull { name ->
            runCatching { specs.javaClass.getDeclaredField(name) }
                .getOrNull()?.apply { isAccessible = true }
                ?.let { runCatching { it.get(specs) }.getOrNull() }
        } ?: return null
        val low = readFps(range, MIN_FIELD_NAMES)
        val high = readFps(range, MAX_FIELD_NAMES)
        if (low == null && high == null) return null
        return "${low ?: "?"}..${high ?: "?"} Hz"
    }

    private fun readFps(range: Any, names: List<String>): String? {
        for (name in names) {
            val field = runCatching { range.javaClass.getDeclaredField(name) }.getOrNull() ?: continue
            field.isAccessible = true
            if (field.type == java.lang.Float.TYPE) {
                return runCatching { field.getFloat(range).toString() }.getOrNull()
            }
            val inner = runCatching { field.get(range) }.getOrNull() ?: continue
            val value = runCatching { inner.toString() }.getOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun Class<*>.method(name: String, vararg parameters: Class<*>): Method? =
        runCatching { getMethod(name, *parameters) }.getOrNull()

    /** The mode-id field's name across the releases seen so far. */
    private val DEFAULT_MODE_NAMES = listOf("defaultMode", "mDefaultMode")
    /** The two range fields of a display-mode policy. */
    private val RANGE_FIELD_NAMES = listOf("primaryRanges", "appRequestRanges")
    private val MIN_FIELD_NAMES = listOf("min", "mMin")
    private val MAX_FIELD_NAMES = listOf("max", "mMax")
}
