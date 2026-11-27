package com.catsmoker.app.screens.gaming.display

import java.lang.reflect.Method

/**
 * Samsung DeX's external-desktop timing filter.
 *
 * DeX hands an app the monitor's vsync through a filter that, on the builds this was
 * researched on, caps it at 60 Hz regardless of what the monitor lists. Samsung's
 * multi-tasking service exposes one switch that lifts it,
 * `enableHighResolutionsForExternalDesktop(boolean)`; this class finds that call on the
 * live binder and does nothing else.
 *
 * The call is looked up, never assumed: the service is Samsung's own addition to
 * `ActivityTaskManager`, its name has changed across One UI releases, and a build without
 * it must report "unsupported" instead of failing at the first request.
 */
object DexBridge {

    /** A resolved call: the service object and the boolean method found on it. */
    data class Call(val service: Any, val method: Method)

    private const val MULTI_TASKING_GETTER = "getMultiTaskingBinder"
    private const val HIGH_RESOLUTION_METHOD = "enableHighResolutionsForExternalDesktop"

    @Volatile
    private var cached: Call? = null

    @Volatile
    private var attempted = false

    /** The switch on this build, or null when the build does not have it. */
    fun highResolutionCall(): Call? {
        cached?.let { return it }
        if (attempted) return null
        attempted = true
        val service = multiTaskingService() ?: return null
        val method = runCatching {
            service.javaClass.methods.firstOrNull {
                it.name == HIGH_RESOLUTION_METHOD &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == java.lang.Boolean.TYPE
            }
        }.getOrNull() ?: return null
        return Call(service, method).also { cached = it }
    }

    /**
     * Invokes the switch.
     *
     * @return true when the service accepted the call, null when it refused or threw. The
     *   caller re-reads the monitor's mode afterwards, because a returned `true` here only
     *   says the request was accepted, not that the panel moved.
     */
    fun invoke(call: Call, enabled: Boolean): Boolean? = runCatching {
        call.method.invoke(call.service, enabled)
        true
    }.getOrNull()

    private fun multiTaskingService(): Any? = runCatching {
        val activityTaskManager = Class.forName("android.app.ActivityTaskManager")
        val getService = activityTaskManager.getMethod("getService").invoke(null)
        val getter = getService.javaClass.methods.firstOrNull {
            it.name == MULTI_TASKING_GETTER && it.parameterTypes.isEmpty()
        } ?: return null
        getter.invoke(getService)
    }.getOrNull()
}
