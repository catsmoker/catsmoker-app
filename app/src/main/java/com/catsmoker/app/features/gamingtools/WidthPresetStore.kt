package com.catsmoker.app.features.gamingtools

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/**
 * Named smallest-width preset library: save a width in dp, list it, apply it, delete it.
 *
 * Ported from `reference/gamingtools/Custom-Animations/.../utils/WidthPresetManager.kt` (read
 * in full): UUID-keyed named presets holding a width, save / list / delete, corrupt storage
 * reading back as empty. Same two divergences as [AnimationPresetStore]: Gson in the store's
 * own file, and save-time validation — here the 72..1000 dp clamp `wm density` itself
 * enforces (see `DisplayMetricsProvider.densityForSmallestWidthDp`), so anything outside it
 * is refused (null) instead of stored. Applying a preset keeps the panel's native pixel size
 * and only moves the density, so the aspect ratio — the shape that letterboxes or crops the
 * system UI when wrong — cannot drift.
 */
class WidthPresetStore(private val storeFile: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    data class WidthPreset(
        val id: String,
        val name: String,
        val widthDp: Int
    )

    /**
     * Stores a preset, or null when [name] is blank or [widthDp] falls outside the 72..1000
     * dp clamp. A failed write is also null (see [AnimationPresetStore.savePreset]).
     */
    fun savePreset(name: String, widthDp: Int): WidthPreset? {
        if (name.isBlank()) return null
        if (widthDp < MIN_WIDTH_DP || widthDp > MAX_WIDTH_DP) return null
        val presets = loadLocked().toMutableList()
        val preset = WidthPreset(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            widthDp = widthDp
        )
        presets.add(preset)
        return if (saveLocked(presets)) preset else null
    }

    fun getAll(): List<WidthPreset> = loadLocked()

    /** True when an entry with [id] existed and is now gone. */
    fun delete(id: String): Boolean {
        val presets = loadLocked().toMutableList()
        val removed = presets.removeAll { it.id == id }
        return removed && saveLocked(presets)
    }

    /** A corrupt or absent file is an empty library, not a crash. */
    private fun loadLocked(): List<WidthPreset> {
        if (!storeFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<WidthPreset>>() {}.type
            gson.fromJson<List<WidthPreset>>(storeFile.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveLocked(presets: List<WidthPreset>): Boolean {
        return try {
            val tmp = File(storeFile.parentFile, "${storeFile.name}.tmp-${System.nanoTime()}")
            try {
                tmp.writeText(gson.toJson(presets))
                if (!tmp.renameTo(storeFile)) {
                    if (storeFile.exists() && !storeFile.delete()) return false
                    tmp.copyTo(storeFile, overwrite = true)
                    tmp.delete()
                }
            } finally {
                if (tmp.exists()) tmp.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private val gson = Gson()

    companion object {
        private const val FILE_NAME = "width_presets.json"

        /** The `wm density` clamp, mirrored so unstorables never reach it (see class KDoc). */
        const val MIN_WIDTH_DP = 72
        const val MAX_WIDTH_DP = 1000
    }
}
