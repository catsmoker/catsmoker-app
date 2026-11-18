package com.catsmoker.app.screens.gaming.mode

import javax.inject.Inject
import javax.inject.Singleton

/** Passive queries must not construct an engine whose startup can reapply manual device settings. */
@Singleton
class GamingModeObservation @Inject constructor() {
    data class Snapshot(
        val mode: String,
        val sessionOpen: Boolean,
        val recoveryRequired: Boolean,
        val unavailableCount: Int
    )

    @Volatile private var source: (() -> Snapshot)? = null

    internal fun observe(source: () -> Snapshot) {
        this.source = source
    }

    fun current(): Snapshot? = source?.invoke()
}
