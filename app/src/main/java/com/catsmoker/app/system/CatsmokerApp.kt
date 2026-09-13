package com.catsmoker.app.system

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.catsmoker.app.features.main.engine.MetricsEngine
import com.catsmoker.app.system.config.AppearanceStore
import com.catsmoker.app.system.config.LocaleHelper
import com.catsmoker.app.system.shell.ShellRunner
import com.google.android.gms.ads.MobileAds
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Also WorkManager's [Configuration.Provider]: the default initializer is removed in the manifest
 * (see there for why), so this is the only place WorkManager can learn how to construct
 * `@HiltWorker` workers. It deliberately constructs the [Configuration] lazily per read rather
 * than in onCreate — WorkManager may not be touched until the first scheduled-work enqueue, long
 * after startup, and the factory is field-injected by then either way.
 */
@HiltAndroidApp
class CatsmokerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var shellRunner: dagger.Lazy<ShellRunner>

    @Inject
    lateinit var metricsEngine: dagger.Lazy<MetricsEngine>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun attachBaseContext(base: Context) {
        // Saved language applies to app-context resources too (services, notifications),
        // and primes the theme flow before any UI reads it.
        val wrapped = LocaleHelper.wrap(base)
        super.attachBaseContext(wrapped)
        AppearanceStore.init(wrapped)
    }

    override fun onCreate() {
        super.onCreate()
        
        applicationScope.launch(Dispatchers.Default) {
            // Trigger pre-warming of heavy dependencies while splash is showing
            try {
                metricsEngine.get()
                shellRunner.get()
            } catch (_: Exception) {}
        }

        Shell.setDefaultBuilder(Shell.Builder.create()
            .setFlags(Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(30))
    }

    /**
     * Initializes heavy SDKs and privilege checks. 
     * Should be called when the UI is already visible and the main thread is idle.
     */
    fun initDeferredTasks() {
        applicationScope.launch(Dispatchers.IO) {
            // Wait for system to settle slightly but much shorter
            delay(500.milliseconds)

            // Pre-warm Hilt dependencies in the background
            metricsEngine.get()
            shellRunner.get()

            // Initialize the AdMob SDK on a background thread (per Google's quick-start).
            // The user's ads_enabled toggle is honoured at each ad surface instead: SDK init
            // itself fetches no ad until a banner/interstitial is explicitly loaded.
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("ads_enabled", true)) {
                MobileAds.initialize(this@CatsmokerApp) {}
            }
        }
    }
}
