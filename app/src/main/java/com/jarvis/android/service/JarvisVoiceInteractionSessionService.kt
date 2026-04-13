package com.jarvis.android.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

class JarvisVoiceInteractionSessionService : VoiceInteractionSessionService() {

    companion object {
        private const val TAG = "JarvisSessionService"
    }

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.d(TAG, "New voice interaction session")
        return JarvisVoiceInteractionSession(this)
    }
}

class JarvisVoiceInteractionSession(context: android.content.Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "JarvisSession"
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "Voice session shown")
    }

    override fun onHide() {
        super.onHide()
        Log.d(TAG, "Voice session hidden")
    }
}
