package com.catsmoker.app.screens.gaming.display

/**
 * What the privileged half of the display engine found on this device.
 *
 * @param shellProcess true when a `shell`-UID process can be started at all — the channel
 *   every hidden interface is reached through. Everything else is only meaningful when this
 *   is true, so a false here is reported as one missing prerequisite, not five.
 * @param reason the first prerequisite that failed, in words the user can act on.
 */
data class DisplayProbe(
    val shellProcess: Boolean = false,
    val modeSpecs: Boolean = false,
    val pacesetter: Boolean = false,
    val dexHighResolution: Boolean = false,
    val pacesetterReadback: Boolean = false,
    val qtiDisplayConfig: Boolean = false,
    val uid: Int? = null,
    val reason: String? = null
) {
    /** Backends that can actually do something here, in the order the engine prefers them. */
    val availableBackends: List<DisplayBackend>
        get() = buildList {
            if (modeSpecs) add(DisplayBackend.DISPLAY_MODE_SPECS)
            if (pacesetter) add(DisplayBackend.SAMSUNG_PACESETTER)
            if (dexHighResolution) add(DisplayBackend.SAMSUNG_DEX_HIGH_RES)
        }
}

/**
 * The result of one privileged attempt.
 *
 * @param detail what happened, in the terms of the mechanism used ("policy now 24.0..120.0 Hz").
 * @param readBack the mechanism's own report of the new state, when it has one. A null
 *   read-back on an [ok] result still means the call was accepted, not that it took effect —
 *   the engine re-reads the display itself before telling the user anything landed.
 */
data class BackendOutcome(
    val ok: Boolean,
    val detail: String? = null,
    val readBack: String? = null
) {
    companion object {
        fun refused(reason: String) = BackendOutcome(ok = false, detail = reason)
    }
}
