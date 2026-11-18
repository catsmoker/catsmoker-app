package com.catsmoker.app.screens.gaming

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("game_settings_v1", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("gaming_engine_prefs", Context.MODE_PRIVATE)
    private val repository = GameSettingsRepository(
        readValue = { prefs.getString(it, null) },
        writeValue = { key, value -> prefs.edit { putString(key, value) } },
        legacyDefaults = GameSettings(
            downscale = com.catsmoker.app.screens.gaming.graphics.GameInterventions.sanitizeDownscale(
                legacy.getFloat("intervention_downscale", 0f)
            ),
            pointerSpeed = com.catsmoker.app.screens.gaming.mode.PointerSpeed.sanitize(
                legacy.getInt("pointer_speed_choice", 0)
            )
        )
    )
    val selection = repository.selection
    fun read(pkg: String): GameSettings = repository.read(pkg)
    fun select(pkg: String?) = repository.select(pkg)
    fun toggle(pkg: String) = repository.toggle(pkg)
    fun recentPackages(): List<String> = repository.recentPackages()
    fun reconcile(available: Set<String>) = repository.reconcile(available)
    fun update(pkg: String, change: (GameSettings) -> GameSettings) = repository.update(pkg, change)
}
