package com.duq.android.audio

/**
 * Проигрывает короткий бип-фидбэк («слушаю») для голосового флоу.
 * Вынесен из VoiceCommandProcessor ради SRP.
 *
 * Интерфейс — общий код KMP (commonMain); реализация платформенная
 * (androidMain: ToneGenerator; iosMain: AudioServices/деградация-no-op).
 */
interface BeepPlayer {
    suspend fun playListeningBeep()
}
