package com.duq.android.screen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Invisible activity that fires the system MediaProjection consent dialog and
 * relays the result back to [ScreenCaptureManager]. No UI of its own; finishes
 * immediately after the dialog resolves.
 */
class ScreenConsentActivity : Activity() {

    private val launcher = 0x5C

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), launcher)
    }

    @Deprecated("startActivityForResult is fine for a one-shot system dialog")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == launcher && resultCode == RESULT_OK && data != null) {
            ScreenCaptureManager.deliverConsent(ScreenCaptureManager.Consent(resultCode, data))
        } else {
            ScreenCaptureManager.deliverConsent(null)
        }
        finish()
    }
}
