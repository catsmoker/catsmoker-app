package com.catsmoker.app.platform.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One mode an attached display enumerates, as the display service reports it.
 *
 * @param id the platform's mode id, the only value any backend accepts when it asks
 *   the composer to switch modes. Never invented — it comes from the display itself.
 */
data class DisplayMode(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Float
)

/**
 * One attached display and the modes it genuinely enumerates.
 *
 * @param isExternal true for every display that is not the built-in panel, which is
 *   the presentation/hdmi/wifi family. Deliberately not a vendor model list: a
 *   plugged-in monitor is external because the display service says so.
 * @param activeMode the mode the composer has committed right now — the physical
 *   rate the panel or monitor is actually running at, not a requested rate.
 */
data class AttachedDisplay(
    val id: Int,
    val name: String,
    val isDefault: Boolean,
    val isExternal: Boolean,
    val state: Int,
    val modes: List<DisplayMode>,
    val activeMode: DisplayMode?
) {
    /** Distinct rates this display enumerates, ascending. */
    val rates: List<Float> get() = DisplayRateMath.distinctRates(modes)

    /** The rate the display is running at now, or null when it will not say. */
    val currentHz: Float? get() = activeMode?.refreshRateHz

    val isOn: Boolean get() = state != Display.STATE_OFF
}

/**
 * The attached displays and their enumerated modes — public API only, so it answers
 * with or without root/Shizuku.
 *
 * This is deliberately the *enumerated* list rather than a rate catalogue: the modes a
 * display reports are the only rates that can be selected without driving it beyond its
 * hardware, and they differ per connection (a monitor that lists 165 Hz on DisplayPort
 * may list 60 Hz through a two-lane hub). Nothing here is cached across calls, because
 * the answer changes on every hotplug.
 */
@Singleton
class DisplayModeCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Every attached display, the built-in panel first. */
    fun read(): List<AttachedDisplay> {
        val manager = runCatching {
            context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        }.getOrNull() ?: return emptyList()

        val displays = runCatching { manager.displays }.getOrNull() ?: return emptyList()
        return displays.sortedBy { it.displayId }.mapNotNull { it.toAttached() }
    }

    /** The built-in panel, or null when the display service will not enumerate it. */
    fun defaultDisplay(): AttachedDisplay? = read().firstOrNull { it.isDefault }

    /** The first external display that is switched on, or null. */
    fun externalDisplay(): AttachedDisplay? = read().firstOrNull { it.isExternal && it.isOn }

    /**
     * Whether any external display is attached and lit.
     *
     * The same runtime fact [com.catsmoker.app.screens.gaming.mode.GamingEnvironment] uses
     * to suppress phone-wide display overrides: while a monitor is driving an app, the
     * phone panel's rate is not that app's rate.
     */
    fun hasActiveExternalDisplay(): Boolean = externalDisplay() != null

    private fun Display.toAttached(): AttachedDisplay? {
        val modeList = runCatching { supportedModes }.getOrNull()
            ?.map { DisplayMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
            ?.distinctBy { it.id }
            .orEmpty()
        val active = runCatching { mode }.getOrNull()
            ?.let { DisplayMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
        val flags = runCatching { flags }.getOrNull() ?: 0
        val external = displayId != Display.DEFAULT_DISPLAY &&
            (flags and Display.FLAG_PRESENTATION) != 0

        // A display that enumerates nothing and reports no mode is a layer projection with
        // no physical panel behind it (an overlay display, a virtual consumer). It cannot be
        // rate-controlled, so reporting it would only offer a control that does nothing.
        if (modeList.isEmpty() && active == null) return null

        return AttachedDisplay(
            id = displayId,
            name = runCatching { name }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: if (displayId == Display.DEFAULT_DISPLAY) BUILT_IN else "display-$displayId",
            isDefault = displayId == Display.DEFAULT_DISPLAY,
            isExternal = external,
            state = runCatching { state }.getOrNull() ?: Display.STATE_UNKNOWN,
            modes = modeList,
            activeMode = active
        )
    }

    private companion object {
        const val BUILT_IN = "Built-in display"
    }
}
