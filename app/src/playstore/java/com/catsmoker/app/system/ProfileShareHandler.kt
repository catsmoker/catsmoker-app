package com.catsmoker.app.system

import android.content.Intent
import androidx.activity.ComponentActivity

/**
 * Play Store variant: device-profile shares are not supported.
 *
 * Same FQN as the full variant's [ProfileShareHandler] so [MainActivity]
 * links without referencing spoof classes (absent from this variant).
 */
object ProfileShareHandler {
    fun handleIntent(activity: ComponentActivity, intent: Intent?): Boolean = false
}
