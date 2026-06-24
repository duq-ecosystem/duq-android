package com.duq.android.audio

import com.duq.android.logging.Logger

/**
 * iOS-реализация [BeepPlayer] — деградация-no-op на старте.
 *
 * Бип-фидбэк («слушаю») привязан к on-device голосовому флоу, который на iOS на старте идёт
 * через сервер. Системный бип (AudioServicesPlaySystemSound) — отдельная итерация; пока тихо.
 */
class DefaultBeepPlayer(
    private val logger: Logger,
) : BeepPlayer {
    override suspend fun playListeningBeep() {
        logger.d(TAG, "listening beep не озвучен на iOS (старт) — no-op")
    }

    private companion object {
        const val TAG = "BeepPlayer"
    }
}
