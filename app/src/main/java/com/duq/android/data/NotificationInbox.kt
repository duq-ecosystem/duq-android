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
 * In-app notification history. Every notification DUQ raises (bot messages,
 * update prompts, etc.) is also recorded here so the user can browse past
 * notifications inside the app, not only in the system shade.
 *
 * Backed by SharedPreferences (JSON) so it survives restarts and can be written
 * from non-Hilt call sites (AppUpdater) via [record].
 */
@Singleton
class NotificationInbox @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // @Keep: serialized with Gson via reflection. This class lives in
    // com.duq.android.data (not data.model), so the package keep-rule doesn't cover
    // it — without @Keep, R8 renames the fields in release and the inbox loads empty.
    @androidx.annotation.Keep
    data class Item(
        val id: Long,
        val title: String,
        val text: String,
        val timestampMs: Long,
        val type: String // "message" | "update" | "system"
    )

    private val _items = MutableStateFlow(load(context))
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    // refresh()/clear() and the static record() must share ONE monitor — an instance
    // @Synchronized locks `this`, a companion @Synchronized locks the companion, so
    // they wouldn't serialize against each other. Lock on the shared LOCK object.
    fun refresh() = synchronized(LOCK) { _items.value = load(context) }

    fun clear() = synchronized(LOCK) {
        prefs(context).edit().remove(KEY).commit() // commit: durable + ordered vs record()
        _items.value = emptyList()
    }

    companion object {
        private const val PREFS = "duq_inbox"
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

        /**
         * Append an item from ANY call site (Hilt or not). Newest first, capped.
         * Safe to call from background threads.
         */
        fun record(context: Context, title: String, text: String, type: String, timestampMs: Long) =
            synchronized(LOCK) {
                val current = load(context)
                // id must be UNIQUE (it's the LazyColumn key) — two notifications in the
                // same millisecond would collide on timestampMs and crash Compose with
                // "duplicate keys". Pack a monotonic sequence into the low bits.
                val id = timestampMs * 1000 + (seq.getAndIncrement() % 1000)
                val item = Item(id, title, text, timestampMs, type)
                val updated = (listOf(item) + current).take(MAX)
                val ok = prefs(context).edit().putString(KEY, gson.toJson(updated)).commit()
                com.duq.android.logging.FileLogger(context).i("NotificationInbox",
                    "record ok=$ok now=${updated.size} title=$title")
            }
    }
}
