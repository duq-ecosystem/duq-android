package com.jarvis.android.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisAccessibility"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected - listener starts when app opens")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }
}
