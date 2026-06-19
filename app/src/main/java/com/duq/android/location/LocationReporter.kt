package com.duq.android.location

import android.location.Location
import com.duq.android.data.SettingsRepository
import com.duq.android.logging.Logger
import com.duq.android.network.openclaw.OpenClawGatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Use case: reports significant location changes to the DUQ agent via gateway.
 * Single Responsibility: formatting + sending only. Does NOT access GPS directly.
 *
 * Format: DUQ receives lat/lng and resolves city/timezone itself using web tools.
 *
 * Dedup: the fused provider emits the current fix immediately on subscribe, so a
 * naive reporter fires on every cold start. We persist the last reported point and
 * skip anything within MIN_REPORT_DISTANCE_M of it, so DUQ is only pinged on a real
 * move (no LLM turn per app launch).
 */
class LocationReporter(
    private val locationDataSource: LocationDataSource,
    private val gatewayClient: OpenClawGatewayClient,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "LocationReporter"
        private const val MIN_REPORT_DISTANCE_M = 25_000f // 25 km — below this it's "the same place"
    }

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            // Immediate check on start: the fused stream gates at 50 km + 24 h interval
            // and gives NO initial fix on subscribe, so a city move was only detected on
            // the provider's next cycle (up to a day+ late — arrived Astana Jun 11, tz
            // updated Jun 13). Read the current fix now and report if we actually moved
            // (dedup handles "same place"), so a relocation is caught the first time the
            // service starts after the move, not 24 h later.
            locationDataSource.getLastLocation()?.let { report(it.latitude, it.longitude) }
            // Then keep reporting on significant background changes.
            // On-demand reports come via reportNow() from PhoneCommand.RequestLocation
            locationDataSource.significantLocationUpdates().collect { location ->
                report(location.latitude, location.longitude)
            }
        }
        logger.d(TAG, "LocationReporter started")
    }

    fun stop() {
        job?.cancel()
        job = null
        logger.d(TAG, "LocationReporter stopped")
    }

    /** One-shot report on demand (e.g. from PhoneCommand.RequestLocation). Bypasses dedup. */
    suspend fun reportNow() {
        locationDataSource.getLastLocation()?.let { report(it.latitude, it.longitude, force = true) }
    }

    private suspend fun report(lat: Double, lng: Double, force: Boolean = false) {
        if (!force) {
            val last = settingsRepository.getLastReportedLocation()
            if (last != null) {
                val results = FloatArray(1)
                Location.distanceBetween(last.first, last.second, lat, lng, results)
                if (results[0] < MIN_REPORT_DISTANCE_M) {
                    logger.d(TAG, "Location unchanged (${results[0].toInt()}m < ${MIN_REPORT_DISTANCE_M.toInt()}m) — skip report")
                    return
                }
            }
        }
        // Location update → DUQ updates context via a BACKGROUND agent turn (not the
        // main chat, not the heartbeat). DUQ resolves the city, writes USER.md «Локация»
        // AND logs the move into today's daily note section «🦆 Заметки DUQ». It does NOT
        // message Денис here — proactive outreach is the heartbeat's job (reads the daily note).
        val msg = "📍 Обновление локации Дениса (фоновое, НЕ сообщение от Дениса): " +
            "lat=%.5f, lng=%.5f.\n".format(lat, lng) +
            "1) Определи город/страну, ОБНОВИ строку «Локация» в USER.md на актуальную (таймзону НЕ трогай).\n" +
            "2) Запиши в СЕГОДНЯШНЮЮ дейли-заметку Дениса (Daily/<DD.MM.YYYY>.md) строку под секцию «## 🦆 Заметки DUQ» через vault__edit_note (oldString=«## 🦆 Заметки DUQ», newString=«## 🦆 Заметки DUQ\\n- 📍 переехал в <город> — не обсудили»). НЕ vault__append_note — он дублирует заголовок в конце файла. Если секции «## 🦆 Заметки DUQ» в заметке ещё нет — сперва добавь её с этой строкой.\n" +
            "Денису СЕЙЧАС НЕ пиши — проактив сделает heartbeat по этой заметке, если будет повод."
        logger.d(TAG, "Reporting location: $lat, $lng (force=$force)")
        gatewayClient.reportLocationToMainAgent(msg)
        settingsRepository.saveLastReportedLocation(lat, lng)
    }
}
