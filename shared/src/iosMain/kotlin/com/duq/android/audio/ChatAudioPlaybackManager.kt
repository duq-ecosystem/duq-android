package com.duq.android.audio

import com.duq.android.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS-реализация [AudioPlaybackManager] — деградация на старте.
 *
 * Проигрывание аудио-сообщений (озвучка ответов) на iOS на старте не реализовано: нативный
 * AVPlayer-плеер — отдельная итерация. Стейт остаётся IDLE, методы проигрывания логируют
 * деградацию и не запускают звук. Текстовый чат и серверный голос при этом работают штатно.
 */
class ChatAudioPlaybackManager(
    private val logger: Logger,
) : AudioPlaybackManager {

    private val _playbackInfo = MutableStateFlow(PlaybackInfo())
    override val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

    override fun initialize() {}

    override fun playOrToggle(messageId: String) {
        logger.d(TAG, "проигрывание озвучки не поддерживается на iOS (старт) — no-op")
    }

    override fun play(messageId: String, audioPath: String) {
        logger.d(TAG, "проигрывание озвучки не поддерживается на iOS (старт) — no-op")
    }

    override fun cacheStreamedAudio(messageId: String, pcmChunks: List<ShortArray>, sampleRate: Int) {
        // Догон на iOS не стартует (LocalTts.isReady=false), кэшировать нечего — no-op.
    }

    override fun pause() {}
    override fun resume() {}
    override fun stop() {}
    override fun seekTo(positionMs: Long) {}
    override fun release() {}

    override fun isCached(messageId: String): Boolean = false
    override fun cachedDurationMs(messageId: String): Int = 0
    override fun clearCache() {}
    override fun renameCache(oldId: String, newId: String) {}

    private companion object {
        const val TAG = "ChatAudioPlayback"
    }
}
