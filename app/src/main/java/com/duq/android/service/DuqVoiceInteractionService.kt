package com.duq.android.service

import android.service.voice.VoiceInteractionService
import android.util.Log

class DuqVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "DuqVoiceService"
    }

    override fun onReady() {
        super.onReady()
        Log.d(TAG, "Voice interaction service ready")
        // Don't auto-start listener - only works when app is open
    }
}
