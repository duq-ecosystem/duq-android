package com.duq.android.di

import com.duq.android.audio.AudioPlaybackManager
import com.duq.android.audio.AudioRecorderInterface
import com.duq.android.audio.BeepPlayer
import com.duq.android.audio.ChatAudioPlaybackManager
import com.duq.android.audio.DefaultBeepPlayer
import com.duq.android.audio.IosAudioRecorder
import com.duq.android.audio.IosVoiceActivityDetector
import com.duq.android.audio.LocalStt
import com.duq.android.audio.LocalTts
import com.duq.android.audio.StreamingTts
import com.duq.android.audio.StreamingTtsController
import com.duq.android.audio.TtsLocal
import com.duq.android.audio.VoiceActivityDetectorInterface
import com.duq.android.audio.WhisperLocal
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS audio-граф: деградации привязаны к общим интерфейсам. Зависят только от Logger
 * (из platformModule); VAD/Beep — no-op без зависимостей.
 */
actual val audioModule: Module = module {
    single<VoiceActivityDetectorInterface> { IosVoiceActivityDetector() }
    single<AudioRecorderInterface> { IosAudioRecorder(get()) }
    single<LocalStt> { WhisperLocal(get()) }
    single<LocalTts> { TtsLocal(get()) }
    single<AudioPlaybackManager> { ChatAudioPlaybackManager(get()) }
    single<BeepPlayer> { DefaultBeepPlayer(get()) }
    single<StreamingTtsController> { StreamingTts(get()) }
}
