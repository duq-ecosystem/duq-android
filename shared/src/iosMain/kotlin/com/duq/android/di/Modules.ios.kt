package com.duq.android.di

import com.duq.android.data.SettingsRepository
import com.duq.android.logging.IosLogger
import com.duq.android.logging.Logger
import com.duq.android.network.duq.DuqNodeClient
import com.duq.android.network.duq.IosPhoneCommandExecutor
import com.duq.android.network.duq.PhoneCommandExecutor
import com.duq.android.ui.AppUpdateController
import com.duq.android.ui.AudioFileCache
import com.duq.android.ui.CoreUpdateNotifier
import com.duq.android.ui.IosAppUpdateController
import com.duq.android.ui.IosAudioFileCache
import com.duq.android.ui.IosCoreUpdateNotifier
import com.duq.android.ui.IosNotificationInbox
import com.duq.android.ui.NotificationInbox
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformModule: Module = module {
    single<Logger> { IosLogger() }
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single { SettingsRepository(get()) }

    // Платформенные UI-мосты (iOS-деградации: in-memory inbox, no-op self-update).
    single<NotificationInbox> { IosNotificationInbox(get()) }
    single<AudioFileCache> { IosAudioFileCache(get()) }
    single<AppUpdateController> { IosAppUpdateController(get()) }
    single<CoreUpdateNotifier> { IosCoreUpdateNotifier(get()) }

    // Phone-control (bot → телефон) — iOS-only в графе: IosPhoneCommandExecutor —
    // деградация (на Android реализация живёт в app/, в shared её нет → там не биндим).
    // DuqNodeClient(executor, chatClient, http, logger): chatClient/http/logger — из графа.
    single<PhoneCommandExecutor> { IosPhoneCommandExecutor(get()) }
    single { DuqNodeClient(get(), get(), get(), get()) }
}

/**
 * iOS-точка инициализации Koin. Swift-сторона (iOSApp) вызывает её на старте до показа
 * UI. AppSecrets (edge-токен/GitHub-токен) на iOS пока не настроены (нет Info.plist-
 * проводки) — остаются пустыми, серверные заголовки/self-update в этом случае выключены.
 */
fun initKoinIos() {
    // Когда появится iOS-проводка секретов: AppSecrets.serverToken/githubReleaseToken
    // заполнить из Info.plist здесь, до startKoin.
    val koinApp = startKoin { modules(sharedModules()) }
    koinApp.koin.get<com.duq.android.network.duq.DuqNodeClient>().start()
}
