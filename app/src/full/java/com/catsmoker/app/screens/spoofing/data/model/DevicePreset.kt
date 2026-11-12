package com.catsmoker.app.screens.spoofing.data.model



data class DevicePreset(
    val id: String,
    val brandLabel: String,
    val modelLabel: String,
    val summary: String,
    val profile: DeviceProfile
) {
    val displayName: String get() = "$brandLabel $modelLabel"
}
