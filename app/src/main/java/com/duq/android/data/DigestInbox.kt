package com.duq.android.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Лента дайджестов (📰) — СОВЕРШЕННО отдельная сущность от истории уведомлений
 * ([NotificationInbox]). Дайджесты (финансовые/новостные сводки от агента) живут
 * в своём хранилище и своей ленте; очистка уведомлений их НЕ трогает и наоборот.
 *
 * Системное push-уведомление «пришёл дайджест» при этом показывается стандартно
 * (см. DuqNotificationManager) — но сам дайджест сюда, а не в историю уведомлений.
 *
 * Своё SharedPreferences-хранилище (JSON), пишется и из non-Hilt мест через [record].
 */
@Singleton
class DigestInbox @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // @Keep: сериализуется Gson через рефлексию; класс вне data.model, package
    // keep-rule его не покрывает — без @Keep R8 переименует поля и лента пустеет.
    @androidx.annotation.Keep
    data class Item(
        val id: Long,
        val title: String,
        val text: String,
        val timestampMs: Long
    )

    private val _items = MutableStateFlow(load(context))
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    fun refresh() = synchronized(LOCK) { _items.value = load(context) }

    fun clear() = synchronized(LOCK) {
        prefs(context).edit().remove(KEY).commit()
        _items.value = emptyList()
    }

    companion object {
        private const val PREFS = "duq_digest"
        private const val KEY = "items"
        private const val MAX = 100
        private val gson = Gson()
        private val LOCK = Any()
        private val seq = java.util.concurrent.atomic.AtomicLong(0)

        private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun load(c: Context): List<Item> = try {
            val json = prefs(c).getString(KEY, null) ?: return emptyList()
            gson.fromJson(json, object : TypeToken<List<Item>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }

        /** Append a digest from ANY call site. Newest first, capped. */
        fun record(context: Context, title: String, text: String, timestampMs: Long) =
            synchronized(LOCK) {
                val current = load(context)
                val id = timestampMs * 1000 + (seq.getAndIncrement() % 1000)
                val updated = (listOf(Item(id, title, text, timestampMs)) + current).take(MAX)
                val ok = prefs(context).edit().putString(KEY, gson.toJson(updated)).commit()
                com.duq.android.logging.FileLogger(context).i("DigestInbox",
                    "record ok=$ok now=${updated.size} title=$title")
            }
    }
}
