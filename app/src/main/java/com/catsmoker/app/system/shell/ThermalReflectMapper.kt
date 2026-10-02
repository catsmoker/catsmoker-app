package com.catsmoker.app.system.shell

/**
 * Maps thermal-binder temperature objects to the `Temperature{mValue=, mType=, mName=}` text
 * lines the dumpsys parser already understands — the re-emit half of the reflection probe
 * (see `ShellRunner` thermal strategies), after the reference
 * `CommandRunnerService#parseTemperatureObjects` (read before this file was written).
 *
 * Getters first (`getName`/`getValue`/`getType`), then `mName`/`mValue`/`mType` fields;
 * anything else maps to null so an unrecognized object is skipped, never fabricated into
 * a reading. Pure (reflection over the given instance only) so the rule is JVM-pinnable.
 */
object ThermalReflectMapper {

    /**
     * One temperature object to one parser-ready line, or null when the object carries no
     * readable name/value/type triple.
     */
    fun mapToLine(obj: Any?): String? {
        if (obj == null) return null
        val cls = obj.javaClass
        val name = readString(obj, cls, "getName", "mName") ?: return null
        val value = readFloat(obj, cls, "getValue", "mValue") ?: return null
        val type = readInt(obj, cls, "getType", "mType") ?: return null
        return "Temperature{mValue=$value, mType=$type, mName=$name}"
    }

    /** Maps a batch, skipping foreign objects (see [mapToLine]). */
    fun mapAll(objects: Iterable<Any?>): List<String> = objects.mapNotNull { mapToLine(it) }

    private fun readString(obj: Any, cls: Class<*>, getter: String, field: String): String? {
        runCatching {
            cls.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
                ?.invoke(obj) as? String
        }.getOrNull()?.let { return it }
        return runCatching {
            cls.fields.firstOrNull { it.name == field }?.apply { isAccessible = true }?.get(obj) as? String
        }.getOrNull()
    }

    private fun readFloat(obj: Any, cls: Class<*>, getter: String, field: String): Float? {
        runCatching {
            when (val v = cls.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }?.invoke(obj)) {
                is Float -> v
                is Double -> v.toFloat()
                is Number -> v.toFloat()
                else -> null
            }
        }.getOrNull()?.let { return it }
        return runCatching {
            when (val v = cls.fields.firstOrNull { it.name == field }?.apply { isAccessible = true }?.get(obj)) {
                is Float -> v
                is Double -> v.toFloat()
                is Number -> v.toFloat()
                else -> null
            }
        }.getOrNull()
    }

    private fun readInt(obj: Any, cls: Class<*>, getter: String, field: String): Int? {
        runCatching {
            (cls.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }?.invoke(obj) as? Number)?.toInt()
        }.getOrNull()?.let { return it }
        return runCatching {
            (cls.fields.firstOrNull { it.name == field }?.apply { isAccessible = true }?.get(obj) as? Number)?.toInt()
        }.getOrNull()
    }
}
