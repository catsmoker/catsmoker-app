package com.catsmoker.app.platform.storage

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri

/** Full-only Android 11+ all-files API. Legacy runtime permissions remain shared. */
object StorageAccess {
    fun hasModernAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Environment.isExternalStorageManager()

    fun settingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val app = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
        return if (app.resolveActivity(context.packageManager) != null) app
        else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
}
