package com.duq.android.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

/**
 * Provides device location data.
 * Single Responsibility: raw location access only, no formatting/sending.
 */
interface LocationDataSource {
    /** One-shot: returns last known location, null if unavailable or permission denied. */
    suspend fun getLastLocation(): Location?

    /** Emits significant location changes (city-level threshold ~1km). */
    fun significantLocationUpdates(): Flow<Location>
}
