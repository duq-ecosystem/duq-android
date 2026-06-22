package com.duq.android.di

import android.content.Context
import com.duq.android.audio.AudioRecorder
import com.duq.android.audio.AudioRecorderInterface
import com.duq.android.audio.VoiceActivityDetector
import com.duq.android.audio.VoiceActivityDetectorInterface
import com.duq.android.data.SettingsRepository
import com.duq.android.audio.BeepPlayer
import com.duq.android.audio.DefaultBeepPlayer
import com.duq.android.location.FusedLocationDataSource
import com.duq.android.location.LocationDataSource
import com.duq.android.logging.FileLogger
import com.duq.android.logging.Logger
import com.duq.android.service.DefaultErrorMapper
import com.duq.android.service.DuqNotificationManager
import com.duq.android.service.ErrorMapper
import com.duq.android.wakeword.DefaultWakeWordManagerFactory
import com.duq.android.wakeword.WakeWordManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideLocationDataSource(@ApplicationContext context: Context): LocationDataSource =
        FusedLocationDataSource(context)

    // Singleton: one mic owner shared by the wake-word service flow and the
    // push-to-talk ViewModel flow, so they can't double-open AudioRecord.
    @Provides
    @Singleton
    fun provideAudioRecorder(
        @ApplicationContext context: Context,
        vad: VoiceActivityDetectorInterface
    ): AudioRecorderInterface = AudioRecorder(context, vad)

    @Provides
    fun provideVoiceActivityDetector(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): VoiceActivityDetectorInterface = VoiceActivityDetector(
        context = context,
        silenceTimeoutMs = settingsRepository.getSilenceTimeoutMsSync()
    )

    @Provides
    @Singleton
    fun provideWakeWordManagerFactory(): WakeWordManagerFactory = DefaultWakeWordManagerFactory()

    @Provides
    @Singleton
    fun provideLogger(@ApplicationContext context: Context): Logger = FileLogger(context)

    @Provides
    @Singleton
    fun provideBeepPlayer(): BeepPlayer = DefaultBeepPlayer()

    @Provides
    @Singleton
    fun provideErrorMapper(): ErrorMapper = DefaultErrorMapper()
}
