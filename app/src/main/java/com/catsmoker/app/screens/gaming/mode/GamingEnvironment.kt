package com.catsmoker.app.screens.gaming.mode

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import com.catsmoker.app.screens.gaming.apps.SuspendSweep

/** Runtime evidence, not a Samsung model list. Does not request or modify input permissions. */
object GamingEnvironment {
    fun hasExternalDisplay(context: Context): Boolean =
        context.getSystemService(DisplayManager::class.java).displays.any {
            it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF
        }

    fun protectedPackages(context: Context): Set<String> {
        val resolver = context.contentResolver
        val ime = Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
        val accessibility = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').map { it.substringBefore('/') }.filter { it.isNotBlank() }
        return SuspendSweep.companions + accessibility + listOfNotNull(ime)
    }
}
