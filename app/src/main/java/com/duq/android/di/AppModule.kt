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
import com.duq.android.data.ConversationRepository
import com.duq.android.data.SettingsRepository
import com.duq.android.data.local.DuqDatabase
import com.duq.android.data.local.Migrations
import com.duq.android.data.local.dao.ConversationDao
import com.duq.android.data.local.dao.MessageDao
import com.duq.android.network.DuqApiClient
import com.duq.android.network.VoiceApiClientInterface
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
    fun provideVoiceApiClient(): VoiceApiClientInterface {
        return DuqApiClient()
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
    fun provideDuqApiClient(): DuqApiClient {
        return DuqApiClient()
    }

    @Provides
    @Singleton
    fun provideConversationRepository(
        apiClient: DuqApiClient,
        conversationDao: ConversationDao,
        messageDao: MessageDao
    ): ConversationRepository {
        return ConversationRepository(apiClient, conversationDao, messageDao)
    }
}
