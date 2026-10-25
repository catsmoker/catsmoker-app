package com.catsmoker.app.features.editgamefiles.pubg

/**
 * Pure presentation/selection rules for the PUBG save editor.
 *
 * The screen used to select chips from the pending-edit map alone, so a freshly read save
 * showed nothing selected even though the game had values for every field. This object keeps
 * the three states separate: the file's saved values, the user's pending changes, and what a
 * chip should show (the pending value when one exists, otherwise the saved value). It also
 * keeps a selection that equals the saved value out of the edit map, so "Apply" cannot light
 * up for a no-op and the unsaved-changes line only appears for a real difference.
 */
object PubgSaveSelection {

    private val FPS_FIELDS = setOf(
        PubgSavePatcher.FIELD_BATTLE_FPS,
        PubgSavePatcher.FIELD_LOBBY_FPS,
        PubgSavePatcher.FIELD_FPS_LEVEL
    )

    private val RENDER_FIELDS = setOf(
        PubgSavePatcher.FIELD_BATTLE_RENDER,
        PubgSavePatcher.FIELD_LOBBY_RENDER
    )

    private val VIEW_FIELDS = PubgSavePatcher.VIEW_FIELDS.toSet()

    /** The value the editor should display: the pending edit first, else the file's own. */
    fun effectiveValue(
        read: PubgSavePatcher.ReadResult?,
        edits: Map<String, Int>,
        name: String
    ): Int? = edits[name] ?: savedValue(read, name)

    fun savedValue(read: PubgSavePatcher.ReadResult?, name: String): Int? =
        (read?.fields?.get(name) as? PubgSavePatcher.FieldOutcome.Value)?.value

    fun supports(read: PubgSavePatcher.ReadResult?, fields: Set<String>): Boolean =
        read != null && read.isGvas && fields.all {
            read.fields[it] is PubgSavePatcher.FieldOutcome.Value
        }

    val fpsFields: Set<String> get() = FPS_FIELDS
    val renderFields: Set<String> get() = RENDER_FIELDS
    val viewFields: Set<String> get() = VIEW_FIELDS

    fun fpsEditable(read: PubgSavePatcher.ReadResult?): Boolean = supports(read, FPS_FIELDS)

    fun renderEditable(read: PubgSavePatcher.ReadResult?): Boolean = supports(read, RENDER_FIELDS)

    fun viewEditable(read: PubgSavePatcher.ReadResult?): Boolean = supports(read, VIEW_FIELDS)

    fun selectedFps(
        read: PubgSavePatcher.ReadResult?,
        edits: Map<String, Int>
    ): Int? = PubgSavePatcher.FPS_TIERS.firstOrNull { tier ->
        effectiveValue(read, edits, PubgSavePatcher.FIELD_BATTLE_FPS) == tier.battleFps &&
            effectiveValue(read, edits, PubgSavePatcher.FIELD_LOBBY_FPS) == tier.lobbyFps &&
            effectiveValue(read, edits, PubgSavePatcher.FIELD_FPS_LEVEL) == tier.fpsLevel
    }?.fps

    fun selectedRender(
        read: PubgSavePatcher.ReadResult?,
        edits: Map<String, Int>
    ): Int? = PubgSavePatcher.RENDER_TIERS.firstOrNull { tier ->
        effectiveValue(read, edits, PubgSavePatcher.FIELD_BATTLE_RENDER) == tier.value &&
            effectiveValue(read, edits, PubgSavePatcher.FIELD_LOBBY_RENDER) == tier.value
    }?.value

    fun selectedView(
        read: PubgSavePatcher.ReadResult?,
        edits: Map<String, Int>
    ): PubgSavePatcher.ViewPreset? = PubgSavePatcher.VIEW_PRESETS.firstOrNull { preset ->
        effectiveValue(read, edits, PubgSavePatcher.FIELD_TP_VIEW) == preset.tpView &&
            effectiveValue(read, edits, PubgSavePatcher.FIELD_FP_VIEW) == preset.fpView
    }

    fun toggleFps(
        read: PubgSavePatcher.ReadResult?,
        current: Map<String, Int>,
        fps: Int
    ): Map<String, Int> {
        if (selectedFps(read, current) == fps) return current - FPS_FIELDS
        return applySet(current, FPS_FIELDS, PubgSavePatcher.fpsEdits(fps), read)
    }

    fun toggleRender(
        read: PubgSavePatcher.ReadResult?,
        current: Map<String, Int>,
        value: Int
    ): Map<String, Int> {
        if (selectedRender(read, current) == value) return current - RENDER_FIELDS
        return applySet(current, RENDER_FIELDS, PubgSavePatcher.renderEdits(value), read)
    }

    fun toggleView(
        read: PubgSavePatcher.ReadResult?,
        current: Map<String, Int>,
        preset: PubgSavePatcher.ViewPreset
    ): Map<String, Int> {
        if (selectedView(read, current) == preset) return current - VIEW_FIELDS
        return applySet(
            current,
            VIEW_FIELDS,
            PubgSavePatcher.viewEdits(preset.tpView, preset.fpView),
            read
        )
    }

    /** Clearing one anchor clears its paired fields, so no half-set ever survives. */
    fun clear(current: Map<String, Int>, name: String): Map<String, Int> {
        val paired = when (name) {
            PubgSavePatcher.FIELD_BATTLE_FPS -> FPS_FIELDS
            PubgSavePatcher.FIELD_BATTLE_RENDER -> RENDER_FIELDS
            PubgSavePatcher.FIELD_TP_VIEW -> VIEW_FIELDS
            else -> setOf(name)
        }
        return current - paired
    }

    private fun applySet(
        current: Map<String, Int>,
        fields: Set<String>,
        desired: Map<String, Int>,
        read: PubgSavePatcher.ReadResult?
    ): Map<String, Int> {
        if (desired.isEmpty()) return current
        val next = (current - fields).toMutableMap()
        // Keep only genuine differences from the file. When a field has no saved value the
        // edit stays pending so Apply will report the patch refusal instead of silently
        // pretending the value is already there.
        desired.forEach { (name, value) ->
            if (savedValue(read, name) != value) next[name] = value
        }
        return next
    }
}
