package com.duq.android.network.duq

import com.duq.android.logging.Logger

/**
 * Android phone-control: пока деградация (как iOS). Нативные команды бота (камера/гео/
 * экран/голос) требуют CameraCapture/ScreenRecorder/LocationDataSource + FGS из
 * исходного `app/`, которые в shared ещё НЕ перенесены (отдельная фаза, как whisper JNI).
 *
 * Это рабочий путь, НЕ заглушка-болванка: [DuqNodeClient] с этим executor'ом всё равно
 * ПОДКЛЮЧАЕТ WS `/duq/ws` → presence + чат/reasoning-стрим (TEXT_DELTA) работают; деградируют
 * только device-команды (`{supported:false}`). Именно отсутствие этого бинда на Android
 * оставляло WS неподключённым → ответы чата не приходили (watchdog 90с).
 */
class AndroidPhoneCommandExecutor(
    private val logger: Logger
) : PhoneCommandExecutor {
    override suspend fun execute(command: String, params: Map<*, *>): Map<String, Any?> {
        logger.w(TAG, "phone-control пока недоступен (camera/location не перенесены) — команда '$command' отклонена")
        return mapOf(
            "supported" to false,
            "command" to command,
            "reason" to "phone-control not yet migrated to shared",
        )
    }

    private companion object { const val TAG = "PhoneCmdAndroid" }
}
