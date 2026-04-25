package com.duq.android.di

import android.content.Context
import androidx.room.Room
import com.duq.android.audio.AudioPlayer
import com.duq.android.audio.AudioPlayerInterface
import com.duq.android.audio.AudioRecorder
import com.duq.android.audio.AudioRecorderInterface
import com.duq.android.audio.VoiceActivityDetector
import com.duq.android.audio.VoiceActivityDetectorInterface
import com.duq.android.auth.KeycloakAuthManager
import com.duq.android.auth.KeycloakTokenRefresher
import com.duq.android.auth.KeycloakUserInfoFetcher
import com.duq.android.auth.TokenRefresher
import com.duq.android.auth.UserInfoFetcher
import com.duq.android.data.ConversationRepository
import com.duq.android.data.SettingsRepository
import com.duq.android.data.local.DuqDatabase
import com.duq.android.data.local.Migrations
import com.duq.android.data.local.dao.ConversationDao
import com.duq.android.data.local.dao.MessageDao
import com.duq.android.audio.BeepPlayer
import com.duq.android.audio.DefaultBeepPlayer
import com.duq.android.logging.AndroidLogger
import com.duq.android.logging.Logger
import com.duq.android.network.ConversationApiClient
import com.duq.android.service.ConversationUpdater
import com.duq.android.service.DefaultConversationUpdater
import com.duq.android.service.DefaultErrorMapper
import com.duq.android.service.ErrorMapper
import com.duq.android.network.DefaultHttpClientFactory
import com.duq.android.network.DefaultRetryExecutor
import com.duq.android.network.DuqApiClient
import com.duq.android.network.DuqWebSocketClient
import com.duq.android.network.HttpClientFactory
import com.duq.android.network.RetryExecutor
import com.duq.android.network.TokenRefreshInterceptor
import com.duq.android.network.VoiceApiClientInterface
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
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideKeycloakAuthManager(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): KeycloakAuthManager {
        return KeycloakAuthManager(context, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideTokenRefreshInterceptor(
        settingsRepository: SettingsRepository
    ): TokenRefreshInterceptor {
        return TokenRefreshInterceptor(settingsRepository)
    }

    @Provides
    @Singleton
    fun provideVoiceApiClient(
        duqApiClient: DuqApiClient
    ): VoiceApiClientInterface {
        return duqApiClient
    }

    @Provides
    @Singleton
    fun provideConversationApiClient(
        duqApiClient: DuqApiClient
    ): ConversationApiClient {
        return duqApiClient
    }

    @Provides
    fun provideAudioRecorder(
        @ApplicationContext context: Context,
        vad: VoiceActivityDetectorInterface
    ): AudioRecorderInterface {
        return AudioRecorder(context, vad)
    }

    @Provides
    fun provideAudioPlayer(
        @ApplicationContext context: Context
    ): AudioPlayerInterface {
        return AudioPlayer(context)
    }

    @Provides
    fun provideVoiceActivityDetector(
        @ApplicationContext context: Context
    ): VoiceActivityDetectorInterface {
        return VoiceActivityDetector(context)
    }

    @Provides
    @Singleton
    fun provideDuqDatabase(
        @ApplicationContext context: Context
    ): DuqDatabase {
        return Room.databaseBuilder(
            context,
            DuqDatabase::class.java,
            "duq_database"
        )
            .addMigrations(*Migrations.ALL)
            .build()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: DuqDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: DuqDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideDuqApiClient(
        tokenRefreshInterceptor: TokenRefreshInterceptor
    ): DuqApiClient {
        return DuqApiClient(tokenRefreshInterceptor)
    }

    @Provides
    @Singleton
    fun provideConversationRepository(
        apiClient: ConversationApiClient,
        conversationDao: ConversationDao,
        messageDao: MessageDao
    ): ConversationRepository {
        return ConversationRepository(apiClient, conversationDao, messageDao)
    }

    @Provides
    @Singleton
    fun provideDuqWebSocketClient(
        settingsRepository: SettingsRepository
    ): DuqWebSocketClient {
        return DuqWebSocketClient(settingsRepository)
    }

    @Provides
    @Singleton
    fun provideWakeWordManagerFactory(): WakeWordManagerFactory {
        return DefaultWakeWordManagerFactory()
    }

    @Provides
    @Singleton
    fun provideLogger(): Logger {
        return AndroidLogger()
    }

    @Provides
    @Singleton
    fun provideBeepPlayer(): BeepPlayer {
        return DefaultBeepPlayer()
    }

    @Provides
    @Singleton
    fun provideConversationUpdater(
        conversationRepository: ConversationRepository
    ): ConversationUpdater {
        return DefaultConversationUpdater(conversationRepository)
    }

    @Provides
    @Singleton
    fun provideErrorMapper(): ErrorMapper {
        return DefaultErrorMapper()
    }

    @Provides
    @Singleton
    fun provideHttpClientFactory(): HttpClientFactory {
        return DefaultHttpClientFactory()
    }

    @Provides
    @Singleton
    fun provideRetryExecutor(): RetryExecutor {
        return DefaultRetryExecutor()
    }

    @Provides
    @Singleton
    fun provideTokenRefresher(): TokenRefresher {
        return KeycloakTokenRefresher()
    }

    @Provides
    @Singleton
    fun provideUserInfoFetcher(): UserInfoFetcher {
        return KeycloakUserInfoFetcher()
    }
}
