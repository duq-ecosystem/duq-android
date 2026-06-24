package com.duq.android.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playback state for audio messages in chat
 */
enum class PlaybackState {
    IDLE,       // Not playing anything
    LOADING,    // Downloading/buffering audio
    PLAYING,    // Currently playing
    PAUSED      // Paused mid-playback
}

/**
 * Current playback info for UI
 */
data class PlaybackInfo(
    val messageId: String? = null,
    val state: PlaybackState = PlaybackState.IDLE,
    val progress: Float = 0f,        // 0.0 - 1.0
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0
)

/**
 * Manages audio playback for chat messages.
 * Handles downloading, caching, and playing audio with state tracking.
 */
@Singleton
class ChatAudioPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ChatAudioPlayback"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 100L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Use volatile for thread-safe access from multiple threads
    @Volatile
    private var exoPlayer: ExoPlayer? = null

    @Volatile
    private var currentListener: Player.Listener? = null

    private var progressJob: Job? = null

    // SupervisorJob ensures child coroutines don't cancel the parent on failure
    // This scope is tied to application lifecycle (singleton)
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + supervisorJob)

    // Track if manager is released to prevent use-after-release
    @Volatile
    private var isReleased = false

    // Audio file cache directory
    private val audioCacheDir: File by lazy {
        File(context.cacheDir, "audio_messages").also { it.mkdirs() }
    }

    // Playback state exposed to UI
    private val _playbackInfo = MutableStateFlow(PlaybackInfo())
    val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

    // --- Стрим-TTS: очередь сегментов-фраз (догон озвучки по мере стрима текста) ---
    // Доступ только из main thread (через mainHandler) — без доп. синхронизации.
    private val segmentQueue = ArrayDeque<File>()
    @Volatile
    private var streamingMsgId: String? = null
    private var playingSegment = false

    /**
     * Initialize the player (call on app start)
     */
    fun initialize() {
        if (isReleased) {
            Log.w(TAG, "Cannot initialize - manager is released")
            return
        }
        mainHandler.post {
            if (exoPlayer == null && !isReleased) {
                exoPlayer = ExoPlayer.Builder(context).build()
                Log.d(TAG, "ExoPlayer initialized")
            }
        }
    }

    /**
     * Play audio for a message. If same message is already playing, toggle pause/resume.
     */
    fun playOrToggle(messageId: String) {
        val currentInfo = _playbackInfo.value

        when {
            // Same message - toggle play/pause
            currentInfo.messageId == messageId -> {
                when (currentInfo.state) {
                    PlaybackState.PLAYING -> pause()
                    PlaybackState.PAUSED -> resume()
                    PlaybackState.LOADING -> {} // Do nothing while loading
                    PlaybackState.IDLE -> loadAndPlay(messageId)
                }
            }
            // Different message - start new playback
            else -> {
                stop()
                loadAndPlay(messageId)
            }
        }
    }

    /**
     * Play a ready audio file directly (e.g. a freshly synthesized TTS reply).
     * Initializes the player if needed and replaces any current playback.
     */
    fun play(messageId: String, audioFile: File) {
        if (isReleased || !audioFile.exists()) return
        // Кэшируем синтез под messageId, иначе кнопка play (loadAndPlay → getCachedAudioFile)
        // его не найдёт после авто-проигрывания: TTS пишет в cacheDir/tts_local_*, а кнопка
        // ищет в audio_messages/msg_<id>.mp3. ExoPlayer играет WAV по контенту (расширение неважно).
        // Копирование (блокирующий файловый IO) — на Dispatchers.IO, не на main; проигрывание
        // (playFile через mainHandler) — после копии.
        scope.launch(Dispatchers.IO) {
            val cached = getCachedAudioFile(messageId)
            if (audioFile.absolutePath != cached.absolutePath) {
                try { audioFile.copyTo(cached, overwrite = true) } catch (e: Exception) {
                    Log.w(TAG, "cache copy failed: ${e.message}")
                }
            }
            if (isReleased) return@launch
            stop()
            playFile(messageId, if (cached.exists()) cached else audioFile)
        }
    }

    /**
     * Стрим-TTS: добавить синтезированный сегмент-фразу в очередь воспроизведения.
     * Первый сегмент стартует сессию (keyed by messageId), последующие доигрываются
     * по порядку. Для «догона» озвучки по мере стрима текста ответа.
     */
    fun enqueueSegment(messageId: String, audioFile: File) {
        if (isReleased || !audioFile.exists()) return
        mainHandler.post {
            if (isReleased) return@post
            if (streamingMsgId != messageId) { // новая стрим-сессия
                stopInternal() // снимает прошлый listener, чистит очередь
                streamingMsgId = messageId
                // Listener ставим ОДИН раз на сессию (не на каждый сегмент) — иначе гонка
                // remove/add вокруг setMediaItem может потерять или продублировать STATE_ENDED.
                val player = exoPlayer ?: ExoPlayer.Builder(context).build().also { exoPlayer = it }
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) playNextSegment()
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "segment playback error: ${error.message}")
                        playNextSegment() // не застреваем — следующий сегмент
                    }
                }
                currentListener = listener
                player.addListener(listener)
                _playbackInfo.value = PlaybackInfo(messageId = messageId, state = PlaybackState.PLAYING)
            }
            segmentQueue.addLast(audioFile)
            if (!playingSegment) playNextSegment()
        }
    }

    /** Проиграть следующий сегмент из очереди (main thread). Listener сессии уже стоит. */
    private fun playNextSegment() {
        if (isReleased) return
        val file = segmentQueue.removeFirstOrNull() ?: run {
            playingSegment = false
            // Очередь пуста: стрим завершён → сброс; иначе ждём следующий сегмент.
            if (streamingMsgId == null) _playbackInfo.value = PlaybackInfo()
            return
        }
        playingSegment = true
        val player = exoPlayer ?: run { playingSegment = false; return }
        try {
            player.clearMediaItems() // прошлый item (его STATE_ENDED уже обработан) → IDLE
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "playNextSegment failed: ${e.message}")
            playingSegment = false
        }
    }

    /** Стрим завершён: новых сегментов не будет. Когда очередь доиграет — сброс в IDLE. */
    fun finishStream(messageId: String) {
        mainHandler.post {
            if (streamingMsgId != messageId) return@post
            streamingMsgId = null
            if (!playingSegment && segmentQueue.isEmpty()) _playbackInfo.value = PlaybackInfo()
        }
    }

    /** Прервать стрим-озвучку (новый тёрн/abort): стоп + очистка очереди. */
    fun cancelStream() {
        mainHandler.post { stopInternal() }
    }

    /**
     * Load audio from cache and play
     */
    private fun loadAndPlay(messageId: String) {
        scope.launch {
            try {
                _playbackInfo.value = PlaybackInfo(
                    messageId = messageId,
                    state = PlaybackState.LOADING
                )

                // Check cache only (no remote download)
                val cachedFile = getCachedAudioFile(messageId)
                if (cachedFile.exists()) {
                    Log.d(TAG, "Using cached audio for message $messageId")
                    playFile(messageId, cachedFile)
                } else {
                    Log.w(TAG, "No cached audio for message $messageId")
                    _playbackInfo.value = PlaybackInfo()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading audio: ${e.message}", e)
                _playbackInfo.value = PlaybackInfo()
            }
        }
    }

    /**
     * Play audio from file
     */
    private fun playFile(messageId: String, audioFile: File) {
        if (isReleased) {
            Log.w(TAG, "Cannot play - manager is released")
            return
        }
        mainHandler.post {
            if (isReleased) return@post
            try {
                // Don't create new player if released or if player already exists
                val player = exoPlayer ?: run {
                    if (isReleased) return@post
                    ExoPlayer.Builder(context).build().also { exoPlayer = it }
                }

                // Remove previous listener
                currentListener?.let { player.removeListener(it) }

                // Create new listener
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                val duration = player.duration
                                _playbackInfo.value = _playbackInfo.value.copy(
                                    state = PlaybackState.PLAYING,
                                    durationMs = duration
                                )
                                startProgressUpdates()
                                Log.d(TAG, "Playback started, duration: ${duration}ms")
                            }
                            Player.STATE_ENDED -> {
                                Log.d(TAG, "Playback ended")
                                stopProgressUpdates()
                                _playbackInfo.value = PlaybackInfo()
                            }
                            Player.STATE_BUFFERING -> {
                                Log.d(TAG, "Buffering")
                            }
                            Player.STATE_IDLE -> {
                                Log.d(TAG, "Player idle")
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isPlaying && _playbackInfo.value.state == PlaybackState.PLAYING) {
                            // Externally paused (e.g., audio focus lost)
                            _playbackInfo.value = _playbackInfo.value.copy(state = PlaybackState.PAUSED)
                            stopProgressUpdates()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "Playback error: ${error.message}", error)
                        stopProgressUpdates()
                        _playbackInfo.value = PlaybackInfo()
                    }
                }

                currentListener = listener
                player.addListener(listener)

                val mediaItem = MediaItem.fromUri(Uri.fromFile(audioFile))
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()

                Log.d(TAG, "Started playback for message $messageId")

            } catch (e: Exception) {
                Log.e(TAG, "Error playing file: ${e.message}", e)
                _playbackInfo.value = PlaybackInfo()
            }
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        mainHandler.post {
            exoPlayer?.pause()
            if (_playbackInfo.value.state == PlaybackState.PLAYING) {
                _playbackInfo.value = _playbackInfo.value.copy(state = PlaybackState.PAUSED)
                stopProgressUpdates()
                Log.d(TAG, "Playback paused")
            }
        }
    }

    /**
     * Resume playback
     */
    fun resume() {
        mainHandler.post {
            exoPlayer?.play()
            if (_playbackInfo.value.state == PlaybackState.PAUSED) {
                _playbackInfo.value = _playbackInfo.value.copy(state = PlaybackState.PLAYING)
                startProgressUpdates()
                Log.d(TAG, "Playback resumed")
            }
        }
    }

    /**
     * Stop playback and reset state
     */
    fun stop() {
        mainHandler.post { stopInternal() }
    }

    /** Останов + сброс (main thread). Чистит и стрим-очередь сегментов. */
    private fun stopInternal() {
        stopProgressUpdates()
        streamingMsgId = null
        segmentQueue.clear()
        playingSegment = false
        currentListener?.let { l -> exoPlayer?.removeListener(l) }
        currentListener = null
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _playbackInfo.value = PlaybackInfo()
        Log.d(TAG, "Playback stopped")
    }

    /**
     * Seek to position
     */
    fun seekTo(positionMs: Long) {
        mainHandler.post {
            exoPlayer?.seekTo(positionMs)
            updateProgress()
        }
    }

    /**
     * Release resources (call on app destroy)
     * Thread-safe: can be called from any thread
     */
    fun release() {
        if (isReleased) return
        isReleased = true

        // Cancel all coroutines first
        supervisorJob.cancel()
        stopProgressUpdates()

        // Release player on main thread
        mainHandler.post {
            try {
                currentListener?.let { listener ->
                    exoPlayer?.removeListener(listener)
                }
                currentListener = null
                exoPlayer?.release()
                exoPlayer = null
                _playbackInfo.value = PlaybackInfo()
                Log.d(TAG, "ExoPlayer released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing player: ${e.message}", e)
            }
        }
    }

    /**
     * Start periodic progress updates
     */
    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (true) {
                updateProgress()
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop progress updates
     */
    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * Update progress from player
     */
    private fun updateProgress() {
        val player = exoPlayer ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        val position = player.currentPosition
        val progress = (position.toFloat() / duration).coerceIn(0f, 1f)

        _playbackInfo.value = _playbackInfo.value.copy(
            currentPositionMs = position,
            durationMs = duration,
            progress = progress
        )
    }

    /**
     * Get cached audio file path for a message
     */
    private fun getCachedAudioFile(messageId: String): File {
        return File(audioCacheDir, "msg_${messageId}.mp3")
    }

    /**
     * Clear audio cache
     */
    fun clearCache() {
        audioCacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Audio cache cleared")
    }

    /**
     * Check if audio is cached for a message
     */
    fun isCached(messageId: String): Boolean {
        return getCachedAudioFile(messageId).exists()
    }

    /**
     * Перепривязать кэш озвучки с одного id на другой. Нужен при reconcile: стрим-пузырь
     * синтезировал озвучку под runId, потом усыновил серверный messageId — без переноса
     * кэша replay по серверному id не находит файл и РЕ-СИНТЕЗИРУЕТ (долго). Переносим
     * msg_<old>.mp3 → msg_<new>.mp3, чтобы replay был мгновенным.
     */
    fun renameCache(oldId: String, newId: String) {
        if (oldId == newId) return
        val old = getCachedAudioFile(oldId)
        if (!old.exists()) return
        val dest = getCachedAudioFile(newId)
        // renameTo — атомарный syscall на том же volume (cacheDir), без побайтовой копии
        // (она блокировала бы Main → ANR). Fallback на copy только при cross-volume (редко).
        if (old.renameTo(dest)) return
        try {
            old.copyTo(dest, overwrite = true)
            old.delete()
        } catch (e: Exception) {
            Log.w(TAG, "renameCache failed: ${e.message}")
        }
    }

    /**
     * Склеить WAV-сегменты догона в ОДИН файл кэша (replay + длительность). Догон играет
     * пофразно и единого файла не оставляет → replay ре-синтезировал бы (долго) и
     * длительности не было бы. Сегменты — один формат (одна Piper-модель), поэтому берём
     * 44-байтный заголовок первого и патчим в нём суммарные размеры PCM. На Dispatchers.IO.
     */
    fun cacheConcatenated(messageId: String, segments: List<File>) {
        if (isReleased || segments.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                val datas = segments.filter { it.exists() && it.length() > 44L }.map { it.readBytes() }
                if (datas.isEmpty()) return@launch
                // Склейка валидна ТОЛЬКО при едином формате сегментов. При fallback часть фраз
                // может прийти с сервера (Silero, другой sampleRate) — склеив их со sherpa-
                // заголовком, получим неверный темп. Сверяем fmt (bytes 22..35: channels/
                // sampleRate/byteRate/blockAlign/bits); при расхождении НЕ клеим (replay
                // ре-синтезирует корректно цельным текстом).
                val fmt0 = datas[0].copyOfRange(22, 36)
                if (datas.any { !it.copyOfRange(22, 36).contentEquals(fmt0) }) {
                    Log.w(TAG, "cacheConcatenated: разный формат сегментов — пропуск склейки")
                    return@launch
                }
                val pcmTotal = datas.sumOf { (it.size - 44).toLong() }  // Long: без переполнения Int
                val header = datas[0].copyOfRange(0, 44)
                writeLe32(header, 4, 36L + pcmTotal)   // RIFF chunk size (UINT32)
                writeLe32(header, 40, pcmTotal)         // data subchunk size (UINT32)
                getCachedAudioFile(messageId).outputStream().use { os ->
                    os.write(header)
                    for (d in datas) os.write(d, 44, d.size - 44)
                }
            } catch (e: Exception) {
                Log.w(TAG, "cacheConcatenated failed: ${e.message}")
            }
        }
    }

    /** Записать UINT32 little-endian (WAV-поля размера ограничены 4 GB → Long маскируем). */
    private fun writeLe32(b: ByteArray, off: Int, v: Long) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }
}
