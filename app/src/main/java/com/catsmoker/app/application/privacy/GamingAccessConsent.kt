package com.catsmoker.app.application.privacy

import android.content.Context
import androidx.core.content.edit

/** Feature-specific permission to access local app inventory/usage/notification keys. */
object GamingAccessConsent {
    const val PREFS = "gaming_access_consent"
    private const val VERSION = "choice_version"
    const val INVENTORY = "inventory"
    const val USAGE = "usage"
    const val NOTIFICATIONS = "notifications"

    fun answered(context: Context): Boolean = prefs(context).getInt(VERSION, 0) == PrivacyPolicy.VERSION
    fun allows(context: Context, key: String): Boolean = prefs(context).let {
        PrivacyPolicy.accessAllowed(it.getBoolean(key, false), it.getInt(VERSION, 0))
    }
    fun save(context: Context, inventory: Boolean, usage: Boolean, notifications: Boolean) {
        prefs(context).edit {
            putBoolean(INVENTORY, inventory)
            putBoolean(USAGE, usage)
            putBoolean(NOTIFICATIONS, notifications)
            putInt(VERSION, PrivacyPolicy.VERSION)
        }
    }
    fun setAllowed(context: Context, key: String, allowed: Boolean) {
        require(key in setOf(INVENTORY, USAGE, NOTIFICATIONS))
        save(context,
            if (key == INVENTORY) allowed else allows(context, INVENTORY),
            if (key == USAGE) allowed else allows(context, USAGE),
            if (key == NOTIFICATIONS) allowed else allows(context, NOTIFICATIONS))
    }
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
