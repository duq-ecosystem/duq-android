package com.duq.android.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Android-реализация [BeepPlayer] — системный бип через ToneGenerator.
 * Вынесено из VoiceCommandProcessor ради SRP.
 */
class DefaultBeepPlayer : BeepPlayer {

    companion object {
        private const val TAG = "BeepPlayer"
        private const val BEEP_VOLUME = 80
        private const val BEEP_DURATION_MS = 150
        private const val BEEP_DELAY_MS = 200L
    }

    override suspend fun playListeningBeep() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, BEEP_VOLUME)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
            delay(BEEP_DELAY_MS)
            toneGenerator.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play beep", e)
        }
    }
}
