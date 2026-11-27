package com.catsmoker.app.screens.gaming.display

/**
 * One way of moving a display's rate.
 *
 * These are mechanisms, not features, and several of them can be present on the same
 * phone. The engine tries the ones the device actually supports and reports which one
 * did the work, because "it worked" is not the useful fact — "it worked through the
 * composer, and the settings key was ignored" is.
 */
enum class DisplayBackend {
    /** AOSP `system:min_refresh_rate` / `peak_refresh_rate`. Every device, app or shell UID. */
    STANDARD_SETTINGS,

    /** A vendor settings key that replaces or overrides the AOSP pair on that skin. */
    OEM_SETTINGS,

    /**
     * `SurfaceControl.setDesiredDisplayModeSpecs` through the composer.
     *
     * The public-in-shape route to a *mode* (not just a preference), gated on
     * `ACCESS_SURFACE_FLINGER`, which the shell UID holds and an ordinary app does not.
     */
    DISPLAY_MODE_SPECS,

    /**
     * Samsung's `forcePacesetter` / `resetForcedPacesetter`.
     *
     * Not in AOSP: Samsung adds it to `android.gui.ISurfaceComposer`, so the transaction
     * numbers exist only on its firmware and are discovered rather than assumed.
     */
    SAMSUNG_PACESETTER,

    /** Samsung DeX's `enableHighResolutionsForExternalDesktop` timing filter. */
    SAMSUNG_DEX_HIGH_RES,

    /**
     * Qualcomm's `vendor.qti.hardware.display.config.IDisplayConfig`.
     *
     * Can start an external display in another of its own modes. Snapdragon only, and the
     * service name and interface version differ between vendor branches.
     */
    QTI_DISPLAY_CONFIG
}

/**
 * Whether one backend can do anything on this device, and why not when it cannot.
 *
 * @param reason a fact the user can act on ("Shizuku is not granted", "this firmware has
 *   no pacesetter calls"), never a bare false. A capability that is merely absent because
 *   the app lacks a grant must not read the same as one the hardware does not have.
 */
data class DisplayCapability(
    val backend: DisplayBackend,
    val available: Boolean,
    val reason: String? = null
)

/**
 * The UID compatibility matrix, as the mechanism table the UI shows advanced users.
 *
 * Each row is a mechanism and which identities can drive it. The values are the result of
 * reading the platform's own checks rather than trial and error:
 *
 * - `min_refresh_rate`/`peak_refresh_rate` are `Settings.System` rows: the app needs
 *   `WRITE_SETTINGS`, or the shell UID writes them through `settings put`.
 * - `setDesiredDisplayModeSpecs` is gated on `ACCESS_SURFACE_FLINGER`
 *   (`SurfaceComposerAIDL::checkAccessPermission`). That permission is declared by
 *   `com.android.shell` and by the platform, so `shell`, `system` and `root` hold it; an
 *   ordinary app does not.
 * - The SurfaceFlinger debug backdoor (codes 1000+ — force composite, mode set, and the
 *   "show refresh rate" overlay) is gated on `HARDWARE_TEST`, which is `signature` only.
 *   The shell UID does **not** hold it, so that family is genuinely out of reach without
 *   root/system, however hidden the API is.
 * - Samsung's pacesetter calls ride the same `ACCESS_SURFACE_FLINGER` check as the rest of
 *   the composer interface: reachable from `shell` where the firmware has them.
 */
object UidMatrix {

    data class Row(
        val mechanism: String,
        val app: Boolean,
        val shell: Boolean,
        val system: Boolean,
        val root: Boolean,
        val note: String
    )

    val rows: List<Row> = listOf(
        Row(
            mechanism = "min_refresh_rate / peak_refresh_rate",
            app = true, shell = true, system = true, root = true,
            note = "WRITE_SETTINGS for the app; `settings put` otherwise"
        ),
        Row(
            mechanism = "setDesiredDisplayModeSpecs",
            app = false, shell = true, system = true, root = true,
            note = "ACCESS_SURFACE_FLINGER; held by com.android.shell"
        ),
        Row(
            mechanism = "forcePacesetter (Samsung)",
            app = false, shell = true, system = true, root = true,
            note = "same composer access; firmware must define the call"
        ),
        Row(
            mechanism = "enableHighResolutionsForExternalDesktop",
            app = false, shell = true, system = true, root = true,
            note = "Samsung multi-tasking service; DeX only"
        ),
        Row(
            mechanism = "SurfaceFlinger debug backdoor (1000+)",
            app = false, shell = false, system = true, root = true,
            note = "HARDWARE_TEST is signature-only; includes the refresh overlay"
        ),
        Row(
            mechanism = "sysfs display nodes",
            app = false, shell = false, system = false, root = true,
            note = "SELinux never grants these to shell"
        )
    )
}
