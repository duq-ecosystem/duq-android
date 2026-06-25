package com.duq.android.di

import com.duq.android.audio.AudioPlaybackManager
import com.duq.android.audio.AudioRecorder
import com.duq.android.audio.AudioRecorderInterface
import com.duq.android.audio.BeepPlayer
import com.duq.android.audio.ChatAudioPlaybackManager
import com.duq.android.audio.DefaultBeepPlayer
import com.duq.android.audio.LocalStt
import com.duq.android.audio.LocalTts
import com.duq.android.audio.StreamingTts
import com.duq.android.audio.StreamingTtsController
import com.duq.android.audio.TtsLocal
import com.duq.android.audio.VoiceActivityDetector
import com.duq.android.audio.VoiceActivityDetectorInterface
import com.duq.android.audio.WhisperLocal
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android audio-граф: нативные реализации привязаны к общим интерфейсам. Context даёт
 * androidContext(); Logger/прочее — из platformModule. VoiceActivityDetector использует
 * дефолты таймаутов из AppConfig (не хардкод в DI).
 */
actual val audioModule: Module = module {
    single<VoiceActivityDetectorInterface> { VoiceActivityDetector(androidContext()) }
    single<AudioRecorderInterface> { AudioRecorder(androidContext(), get()) }
    single<LocalStt> { WhisperLocal(androidContext()) }
    single<LocalTts> { TtsLocal(androidContext()) }
    single<AudioPlaybackManager> { ChatAudioPlaybackManager(androidContext(), get()) }
    single<BeepPlayer> { DefaultBeepPlayer() }
    single<StreamingTtsController> { StreamingTts(get(), get(), get()) }
}
