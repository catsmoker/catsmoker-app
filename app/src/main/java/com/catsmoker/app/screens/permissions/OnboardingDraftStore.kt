package com.catsmoker.app.screens.permissions

import android.content.Context
import androidx.core.content.edit

/** A locale change deliberately clears the task and exits, so SavedStateHandle is insufficient. */
internal class OnboardingDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("onboarding_draft", Context.MODE_PRIVATE)

    fun read(initial: OnboardingState): OnboardingState = initial.copy(
        step = OnboardingState.Step.entries.firstOrNull { it.name == prefs.getString("step", null) }
            ?: initial.step,
        agreed = prefs.getBoolean("agreed", initial.agreed),
        // Legacy drafts preselected analytics and required a second confirmation. Never
        // interpret that old default as an opt-in now that the switch is the only choice.
        analytics = prefs.getBoolean("analytics_opt_in", initial.analytics),
        inventory = prefs.getBoolean("inventory", initial.inventory),
        usage = prefs.getBoolean("usage", initial.usage),
        notifications = prefs.getBoolean("notifications", initial.notifications),
    )

    fun languageTag(fallback: String): String = prefs.getString("language", fallback) ?: fallback

    fun save(state: OnboardingState, languageTag: String, commit: Boolean = false) {
        prefs.edit(commit = commit) {
            putString("step", state.step.name)
            putBoolean("agreed", state.agreed)
            putBoolean("analytics_opt_in", state.analytics)
            putBoolean("inventory", state.inventory)
            putBoolean("usage", state.usage)
            putBoolean("notifications", state.notifications)
            putString("language", languageTag)
        }
    }

    fun clear() = prefs.edit { clear() }
}
