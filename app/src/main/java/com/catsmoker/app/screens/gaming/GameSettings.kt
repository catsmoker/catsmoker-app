package com.catsmoker.app.screens.gaming

import com.catsmoker.app.screens.gaming.graphics.GameInterventions
import com.catsmoker.app.screens.gaming.mode.PointerSpeed
import org.json.JSONObject

/** Desired inputs, not a claim about the device. Resolution still needs its existing Apply step. */
data class GameSettings(
    val downscale: Float? = null,
    val pointerSpeed: Int? = null,
    val resolutionId: String? = null,
    val width: String = "",
    val height: String = "",
    val dpi: String = "",
    val cachePreload: Boolean = false,
    val cacheBudgetMiB: Int = 32,
    val cacheDocuments: List<String> = emptyList()
) {
    fun encode(): String = JSONObject().apply {
        put("version", 1)
        put("downscale", downscale ?: JSONObject.NULL)
        put("pointerSpeed", pointerSpeed ?: JSONObject.NULL)
        put("resolutionId", resolutionId ?: JSONObject.NULL)
        put("width", width)
        put("height", height)
        put("dpi", dpi)
        put("cachePreload", cachePreload)
        put("cacheBudgetMiB", cacheBudgetMiB)
        put("cacheDocuments", org.json.JSONArray(cacheDocuments))
    }.toString()

    companion object {
        fun decode(raw: String?, fallback: GameSettings = GameSettings()): GameSettings {
            if (raw == null) return fallback
            return runCatching {
                val json = JSONObject(raw)
                require(json.getInt("version") == 1)
                GameSettings(
                    downscale = GameInterventions.sanitizeDownscale(
                        if (json.isNull("downscale")) null else json.getDouble("downscale").toFloat()
                    ),
                    pointerSpeed = PointerSpeed.sanitize(
                        if (json.isNull("pointerSpeed")) null else json.getInt("pointerSpeed")
                    ),
                    resolutionId = if (json.isNull("resolutionId")) null else json.getString("resolutionId"),
                    width = json.optString("width", "").take(16),
                    height = json.optString("height", "").take(16),
                    dpi = json.optString("dpi", "").take(16),
                    cachePreload = json.optBoolean("cachePreload", false),
                    cacheBudgetMiB = json.optInt("cacheBudgetMiB", 32).coerceIn(8, 64),
                    cacheDocuments = json.optJSONArray("cacheDocuments")?.let { array ->
                        (0 until minOf(array.length(), 8)).mapNotNull { index ->
                            array.optString(index).takeIf { it.startsWith("content://") && it.length <= 2048 }
                        }
                    }.orEmpty()
                )
            }.getOrDefault(fallback)
        }

        fun validPackage(pkg: String): Boolean =
            pkg.length <= 255 && Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(pkg)
    }
}
