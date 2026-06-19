package com.duq.android.service

import com.duq.android.DuqState
import com.duq.android.error.DuqError
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for voice service control.
 * Decouples UI from concrete DuqListenerService implementation.
 *
 * This allows:
 * - Testing UI without running actual service
 * - Swapping service implementations
 * - Proper dependency inversion
 */
interface VoiceServiceController {
    /**
     * Current state of voice processing.
     */
    val state: StateFlow<DuqState>

    /**
     * Current error, if any.
     */
    val error: StateFlow<DuqError?>

    /**
     * Start voice listening.
     */
    fun startListening()

    /**
     * Stop voice listening.
     */
    fun stopListening()

    /**
     * Clear current error.
     */
    fun clearError()
}
