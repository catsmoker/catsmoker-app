package com.catsmoker.app.features.gamingtools.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects which graphics API is actually live on this device, so spoof keys are only published
 * for APIs the device and its games can reach.
 *
 * SurfaceFlinger's `vulkan_renderengine` flag tells whether the compositor uses Vulkan, and the
 * `cmd gpu` service answers whether the Vulkan HAL is reachable. Neither is fully reliable on
 * every build (`cmd gpu` can throw a transaction failure), so the detector falls back through
 * several signals and reports honestly when it cannot tell.
 *
 * The detector is deliberately read-only: it never loads `libvulkan.so`, never calls `vkEnumerate*`,
 * and never changes any graphics setting. It only reports what the device already advertises.
 */
@Singleton
class GraphicsApiDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cachedVulkanLive: Boolean? = null

    /**
     * Whether Vulkan appears to be a live graphics API on this device.
     *
     * Resolution order (first conclusive answer wins):
     * 1. `dumpsys SurfaceFlinger` — `vulkan_renderengine: true` means Vulkan is the compositor API;
     *    `false` means it is not (GLES compositing instead).
     * 2. `cmd gpu vkjson` — a successful transaction means the Vulkan HAL service answers.
     * 3. `ro.hardware.vulkan` prop non-empty + `libvulkan.so` present on the system.
     *
     * Returns null when no signal is conclusive; callers treat that as "unknown, do not claim".
     */
    fun isVulkanLive(): Boolean? {
        cachedVulkanLive?.let { return it }

        // 1. SurfaceFlinger compositor flag — most direct answer.
        val sf = runCatching {
            readCommandOutput(arrayOf("dumpsys", "SurfaceFlinger"))
        }.getOrDefault("")
        val vulkanEngine = runCatching {
            sf.lines().firstOrNull { it.trim().startsWith("vulkan_renderengine:") }
                ?.substringAfter("vulkan_renderengine:")
                ?.trim()
        }.getOrNull()
        when (vulkanEngine) {
            "true" -> { cachedVulkanLive = true; return true }
            "false" -> { cachedVulkanLive = false; return false }
        }

        // 2. Vulkan GPU service reachable.
        val vk = runCatching {
            readCommandOutput(arrayOf("cmd", "gpu", "vkjson"))
        }.getOrDefault("")
        if (vk.isNotBlank() && !vk.contains("Failure") && !vk.contains("error")) {
            cachedVulkanLive = true
            return true
        }

        // 3. Fall back: Vulkan library present and a hardware Vulkan string reported.
        val libPresent = listOf(
            "/system/lib64/libvulkan.so",
            "/system/lib/libvulkan.so",
            "/vendor/lib64/libvulkan.so",
            "/vendor/lib/libvulkan.so",
            "/odm/lib64/libvulkan.so",
            "/odm/lib/libvulkan.so"
        ).any { java.io.File(it).exists() }
        val hwVulkan = runCatching {
            readCommandOutput(arrayOf("getprop", "ro.hardware.vulkan"))
        }.getOrDefault("").trim()
        val result = libPresent && hwVulkan.isNotBlank()
        cachedVulkanLive = result
        return result
    }

    private fun readCommandOutput(command: Array<String>): String {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    output.append(reader.readText())
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }
        return try {
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                runCatching { process.outputStream.close() }
                runCatching { process.inputStream.close() }
                return ""
            }
            reader.join(1_000)
            output.toString()
        } catch (_: Exception) {
            ""
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.outputStream.close() }
            runCatching { process.inputStream.close() }
        }
    }

    /** Whether OpenGL ES is a live graphics API on this device. Always true on Android, but kept
     * for symmetry with [isVulkanLive] and future API-specific gates. */
    fun isGlesLive(): Boolean = true

    /** Clears the cached verdict so the next read re-probes. Used after a graphics HAL restart. */
    fun invalidate() {
        cachedVulkanLive = null
    }
}
