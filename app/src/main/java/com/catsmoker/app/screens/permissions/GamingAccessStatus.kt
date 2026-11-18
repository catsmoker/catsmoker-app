package com.catsmoker.app.screens.permissions

internal enum class GamingAccessStatus {
    NOT_GRANTED,
    ANDROID_REQUIRED,
    PENDING_SAVE,
    GRANTED;

    companion object {
        /** A checked draft does not grant access; installed apps only need saved consent. */
        fun of(selected: Boolean, confirmed: Boolean, androidGranted: Boolean = true): GamingAccessStatus =
            when {
                !selected -> NOT_GRANTED
                !androidGranted -> ANDROID_REQUIRED
                !confirmed -> PENDING_SAVE
                else -> GRANTED
            }
    }
}
