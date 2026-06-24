package com.duq.android

import android.app.Application

/**
 * Android entry-point. Koin-инициализация подключается на фазе DI (Hilt→Koin):
 * startKoin { androidContext(this@DuqApplication); modules(sharedModules) }.
 */
class DuqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
