package com.catsmoker.app.features.gamingtools.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.catsmoker.app.features.gamingtools.tools.graphics.GameDeveloperOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the Gaming Mode toggle on a job that outlives the screen that asked for it.
 *
 * Activation is a long write: it snapshots every lever, suspends about a hundred apps and reads
 * each one back, which took ~90 s on the lab Nothing Phone 2a. It used to run on the ViewModel's
 * scope, so a phone that finished the activity mid-run — screen off, or an OEM that closes
 * activities aggressively (`always_finish_activities`) — cancelled the job halfway. The engine
 * then reported the cancellation as "Activation failed" while some levers were already written,
 * and the holding foreground service had not started yet (it starts only once the engine reports
 * Active, which is what keeps an "active" notification from sitting over an idle engine).
 *
 * The toggle therefore belongs to the app process, like the dexopt run that moved to
 * [com.catsmoker.app.features.gamingtools.tools.booster.AppBoosterService] for the same reason.
 * Reverting runs here too: a revert cancelled halfway is how settings stay applied with nothing
 * left running to undo them. A process kill is still covered — the engine's persisted session
 * record is what the recovery card reads on the next launch.
 *
 * Deliberately not a foreground service of its own: the auto-session monitor already runs one
 * while it drives this path, and a second notification for the few seconds between the request
 * and the engine's own service would add a row to the shade that says nothing new.
 */
@Singleton
class GamingModeJobRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gamingEngine: GamingEngine,
    private val gameDeveloperOptions: GameDeveloperOptions
) {
    /**
     * Owned by this singleton, with no parent job: a caller cancelling its own scope (the
     * ViewModel clearing on [onCleared][androidx.lifecycle.ViewModel.onCleared]) cannot take a
     * half-written activation with it.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun activate() {
        scope.launch {
            gamingEngine.toggleGamingMode(true)
            // Start the holder only when the engine actually reached Active: an activation
            // refused by the recovery guard must not leave an "active" notification over an
            // idle engine. The service is what keeps the process alive while the session holds
            // system settings, and its onTaskRemoved is the revert of last resort.
            if (gamingEngine.state.value is GamingModeState.Active) {
                ContextCompat.startForegroundService(context, Intent(context, GamingModeService::class.java))
            }
            // Gaming Mode writes the refresh-rate keys itself, so the standalone switch has to
            // follow the device rather than keep showing what it read before activation.
            gameDeveloperOptions.refresh()
        }
    }

    fun deactivate() {
        scope.launch {
            gamingEngine.toggleGamingMode(false)
            context.stopService(Intent(context, GamingModeService::class.java))
            gameDeveloperOptions.refresh()
        }
    }
}
