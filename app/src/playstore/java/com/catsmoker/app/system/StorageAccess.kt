package com.catsmoker.app.system

import android.content.Context
import android.content.Intent

/** No all-files permission query or settings route is compiled into Play. */
object StorageAccess {
    fun hasModernAccess(): Boolean = false
    fun settingsIntent(@Suppress("UNUSED_PARAMETER") context: Context): Intent? = null
}
