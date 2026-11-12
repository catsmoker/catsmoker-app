package com.catsmoker.app.application.di

import android.content.Context
import com.catsmoker.app.screens.gaming.audio.AudioBoostController
import com.catsmoker.app.screens.logs.LogcatStreamer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideAudioBoostController(@ApplicationContext context: Context): AudioBoostController =
        AudioBoostController(context)

    /**
     * The single reader behind [com.catsmoker.app.screens.logs.AppLogCollector]:
     * provided (not constructed inline) so tests can substitute a fake process.
     */
    @Provides
    @Singleton
    fun provideLogcatStreamer(): LogcatStreamer = LogcatStreamer()
}
