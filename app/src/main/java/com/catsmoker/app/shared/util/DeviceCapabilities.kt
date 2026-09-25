package com.catsmoker.app.shared.util

/**
 * Static device facts in, supported-mechanism flags out — pure logic, no Context,
 * so every branch is unit-provable ([DeviceCapabilitiesTest]).
 *
 * This is the capability-detection pattern, not a registry: callers pass what the
 * platform reports (`Build.MANUFACTURER`, `Build.HARDWARE`, `Build.VERSION.SDK_INT`)
 * and get back which mechanisms that device can honor. Unsupported mechanisms are
 * never attempted — the UI hides or disables them instead. Privilege state (root /
 * Shizuku) stays with `ShellRunner`; this answers only what the hardware and the
 * OS level allow.
 */
object DeviceCapabilities {

    data class DeviceInfo(
        val manufacturer: String = "",
        val hardware: String = "",
        val board: String = "",
        val sdkInt: Int = 0
    )

    data class Flags(
        val isVivo: Boolean = false,
        val isSamsung: Boolean = false,
        val isXiaomi: Boolean = false,
        val isQualcomm: Boolean = false,
        val isMediaTek: Boolean = false,
        /** `cmd power set-fixed-performance-mode-enabled` exists from Android 11 (R). */
        val supportsFixedPerformanceMode: Boolean = false,
        /** `PowerManager.getThermalHeadroom` exists from API 30. */
        val supportsThermalHeadroom: Boolean = false,
        /** `device_config game_overlay` interventions need Android 12 (S). */
        val supportsGameInterventions: Boolean = false
    )

    fun detect(info: DeviceInfo): Flags {
        val m = info.manufacturer.trim().lowercase()
        val hw = info.hardware.trim().lowercase()
        val board = info.board.trim().lowercase()
        val silicon = "$hw $board"
        return Flags(
            isVivo = m == "vivo" || m == "iqoo",
            isSamsung = m == "samsung",
            isXiaomi = m == "xiaomi" || m == "redmi" || m == "poco" || m == "black shark",
            isQualcomm = "qcom" in silicon || QUALCOMM_BOARDS.any { it in silicon } ||
                Regex("""\bsm\d{4}\b""").containsMatchIn(silicon),
            isMediaTek = "mediatek" in silicon || "dimensity" in silicon || "helio" in silicon ||
                Regex("""\bmt\d{4}\b""").containsMatchIn(silicon),
            supportsFixedPerformanceMode = info.sdkInt >= 30,
            supportsThermalHeadroom = info.sdkInt >= 30,
            supportsGameInterventions = info.sdkInt >= 31
        )
    }

    /**
     * Snapdragon board names worth matching when `hardware` is unhelpful (some OEM
     * builds report the silicon only through `board`). Deliberately short — a miss
     * only hides a Qualcomm-gated lever, never misreports one.
     */
    private val QUALCOMM_BOARDS = listOf(
        "sun", "pineapple", "kalama", "waipio", "taro", "lahaina", "lito", "bengal", "parrot"
    )
}
