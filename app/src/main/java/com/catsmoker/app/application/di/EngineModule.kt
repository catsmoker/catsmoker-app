package com.catsmoker.app.application.di

import android.content.Context
import com.catsmoker.app.platform.display.DisplayRefreshRateProvider
import com.catsmoker.app.platform.shell.ShellRunner
import com.catsmoker.app.screens.gaming.apps.SuspendExemptStore
import com.catsmoker.app.screens.gaming.apps.SuspendListStore
import com.catsmoker.app.screens.gaming.graphics.GameInterventions
import com.catsmoker.app.screens.gaming.mode.GamingEngine
import com.catsmoker.app.screens.gaming.mode.RefreshRateController
import com.catsmoker.app.screens.gaming.network.BackgroundDataRestrictor
import com.catsmoker.app.telemetry.MetricsEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideMetricsEngine(
        @ApplicationContext context: Context,
        shellRunner: ShellRunner,
        refreshRates: DisplayRefreshRateProvider
    ): MetricsEngine = MetricsEngine(context, shellRunner, refreshRates)

    @Provides
    @Singleton
    fun provideGamingEngine(
        @ApplicationContext context: Context,
        shellRunner: ShellRunner,
        backgroundDataRestrictor: BackgroundDataRestrictor,
        gameInterventions: GameInterventions,
        suspendListStore: SuspendListStore,
        suspendExemptStore: SuspendExemptStore,
        refreshController: RefreshRateController
    ): GamingEngine = GamingEngine(
        context, shellRunner, backgroundDataRestrictor, gameInterventions,
        suspendListStore, suspendExemptStore, refreshController
    )
}
