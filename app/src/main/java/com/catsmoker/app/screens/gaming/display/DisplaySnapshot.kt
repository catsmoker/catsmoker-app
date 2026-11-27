package com.catsmoker.app.screens.gaming.display

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The display state that existed before CatSmoker changed anything.
 *
 * Every rate is the *raw stored text*, not a parsed number, so a restore puts back exactly what
 * was there — including "unset", which is not the same as 60 Hz on any of these rows.
 *
 * @param pendingConfirm true while a risky change is waiting for the user to keep it. A
 *   snapshot that still says true at startup means the process died (or the screen stayed
 *   black) before confirmation, and the next launch must roll it back instead of adopting it.
 * @param displayModes the modes switched, keyed by `DisplayManager` display id. Kept per
 *   display because an external monitor and the phone panel are restored independently.
 * @param forcedPacesetter the forced pacesetter display id that existed before, if any.
 * @param dexHighResolution DeX's timing-filter state before it was touched, or null when it
 *   was never touched — null and false are different answers and only one needs restoring.
 */
data class DisplaySnapshot(
    val minRefreshRate: String? = null,
    val peakRefreshRate: String? = null,
    val displayModes: Map<Int, StoredMode> = emptyMap(),
    val forcedPacesetter: Long? = null,
    val dexHighResolution: Boolean? = null,
    val pendingConfirm: Boolean = false,
    val recordedAt: Long = 0L
) {
    val isEmpty: Boolean
        get() = minRefreshRate == null && peakRefreshRate == null && displayModes.isEmpty() &&
            forcedPacesetter == null && dexHighResolution == null

    /** Nothing left to put back, so the record can be dropped. */
    fun cleared(pending: Boolean = false): DisplaySnapshot =
        DisplaySnapshot(pendingConfirm = pending, recordedAt = recordedAt)
}

/** A display mode remembered well enough to return to it: its id and the rate it runs at. */
data class StoredMode(val modeId: Int, val refreshRateHz: Float)

/**
 * Persists [DisplaySnapshot] outside the display engine.
 *
 * On disk rather than in memory on purpose: the failure this protects against — a black
 * monitor and a killed process — takes the in-memory copy with it, and recovery has to happen
 * on the *next* launch. Nothing here leaves the device.
 */
@Singleton
class DisplaySnapshotStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): DisplaySnapshot? {
        val json = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { gson.fromJson(json, DisplaySnapshot::class.java) }.getOrNull()
    }

    fun save(snapshot: DisplaySnapshot) {
        prefs.edit { putString(KEY_SNAPSHOT, gson.toJson(snapshot)) }
    }

    fun clear() {
        prefs.edit { remove(KEY_SNAPSHOT) }
    }

    companion object {
        private const val PREFS_NAME = "display_snapshot"
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
