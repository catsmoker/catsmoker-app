package com.catsmoker.app.shared.data.model

data class SettingValue(val value: String, val existed: Boolean) {
    /**
     * Whether a read-back after a restore matches this recorded value.
     *
     * A setting that did not exist is restored by deleting it, so the only match is another
     * absent value. A stored value is compared numerically when both sides parse as numbers:
     * some providers normalise `120` to `120.0`, and treating that as a refusal would show a
     * false failure. Non-numeric values are compared exactly.
     */
    fun matchesAfterRestore(readBack: SettingValue): Boolean {
        if (!existed || value.isBlank()) return !readBack.existed
        if (!readBack.existed) return false
        val wanted = value.toFloatOrNull()
        val actual = readBack.value.toFloatOrNull()
        return if (wanted != null && actual != null) wanted == actual else readBack.value == value
    }

    companion object {
        fun fromCommandOutput(output: String): SettingValue {
            val trimmed = output.trim()
            return if (trimmed == "null" || trimmed.isEmpty()) {
                SettingValue("", false)
            } else {
                SettingValue(trimmed, true)
            }
        }
    }
}
