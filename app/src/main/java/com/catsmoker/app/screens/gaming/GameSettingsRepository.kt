package com.catsmoker.app.screens.gaming

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Serializes package-addressed edits; an asynchronous completion never writes to the current pick. */
class GameSettingsRepository(
    private val readValue: (String) -> String?,
    private val writeValue: (String, String?) -> Unit,
    legacyDefaults: GameSettings
) {
    data class Selection(val packageName: String?, val settings: GameSettings)

    private val defaults = GameSettings.decode(readValue("defaults_v1"), legacyDefaults)
    private val _selection = MutableStateFlow(
        Selection(readValue("selected_package")?.takeIf(GameSettings::validPackage), defaults)
    )
    val selection = _selection.asStateFlow()

    init {
        // Freeze upgrade defaults once. Editing A must not become the default for a new game B.
        if (readValue("defaults_v1") == null) writeValue("defaults_v1", defaults.encode())
        // A missing value is an upgrade/first-open state, never permission to pick a discovered game.
        if (readValue("selection_initialized") == null) {
            writeValue("selected_package", _selection.value.packageName ?: GLOBAL)
            writeValue("selection_initialized", "true")
        }
        _selection.value.packageName?.let { _selection.value = Selection(it, read(it)) }
    }

    @Synchronized
    fun read(pkg: String): GameSettings {
        require(GameSettings.validPackage(pkg))
        return GameSettings.decode(readValue("game:$pkg"), defaults)
    }

    @Synchronized
    fun select(pkg: String?) {
        require(pkg == null || GameSettings.validPackage(pkg))
        if (pkg == _selection.value.packageName) return
        writeValue("selected_package", pkg ?: GLOBAL)
        if (pkg != null) {
            writeValue("recent_packages", JSONArray((listOf(pkg) + recentPackages()).distinct().take(24)).toString())
        }
        _selection.value = Selection(pkg, pkg?.let(::read) ?: defaults)
    }

    @Synchronized
    fun recentPackages(): List<String> = runCatching {
        val array = JSONArray(readValue("recent_packages") ?: "[]")
        (0 until minOf(array.length(), 24)).map { array.optString(it) }
            .filter(GameSettings::validPackage).distinct()
    }.getOrDefault(emptyList())

    @Synchronized
    fun toggle(pkg: String) {
        require(GameSettings.validPackage(pkg))
        select(if (_selection.value.packageName == pkg) null else pkg)
    }

    /** Reconcile only after discovery completes; an intentional Global never falls back to a game. */
    @Synchronized
    fun reconcile(available: Set<String>) {
        if (_selection.value.packageName?.let { it !in available } == true) select(null)
    }

    @Synchronized
    fun update(pkg: String, change: (GameSettings) -> GameSettings) {
        val old = read(pkg)
        val updated = change(old)
        if (old == updated) return
        writeValue("game:$pkg", updated.encode())
        if (_selection.value.packageName == pkg) _selection.value = Selection(pkg, updated)
    }

    private companion object {
        const val GLOBAL = "@global"
    }
}
