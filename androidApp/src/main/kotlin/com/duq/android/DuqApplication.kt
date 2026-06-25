package com.duq.android

import android.app.Application
import com.duq.android.config.AppSecrets
import com.duq.android.di.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Android entry-point. Кладёт секреты сборки (edge-токен/GitHub-токен) из BuildConfig в
 * [AppSecrets] (shared не видит BuildConfig напрямую) и стартует Koin со всем графом
 * shared (network/audio/viewModel/platform). androidContext() даёт Context реализациям,
 * которым он нужен (audio, UI-мосты, updater).
 */
class DuqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSecrets.serverToken = BuildConfig.SERVER_TOKEN
        AppSecrets.githubReleaseToken = BuildConfig.GH_RELEASE_TOKEN
        startKoin {
            androidContext(this@DuqApplication)
            modules(sharedModules())
        }
    }
}
