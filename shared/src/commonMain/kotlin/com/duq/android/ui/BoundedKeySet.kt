package com.duq.android.ui

/**
 * Маленький ограниченный LRU-набор ключей (по порядку ВСТАВКИ — eldest-first eviction).
 * Замена JVM-only `object : LinkedHashMap(cap, lf, accessOrder=false){ removeEldestEntry }`
 * из android-референса: `accessOrder`-конструктор и `removeEldestEntry` есть только в
 * `java.util.LinkedHashMap`, в commonMain их нет. Здесь — общий код KMP на kotlin-stdlib.
 *
 * Семантика 1:1 с референсом (accessOrder=false): при превышении [maxSize] вытесняется
 * самый старый по вставке ключ. Доступ к существующему ключу порядок НЕ меняет.
 */
internal class BoundedKeySet(private val maxSize: Int) {
    // LinkedHashMap (без accessOrder) держит порядок вставки — итерация даёт eldest первым.
    private val map = LinkedHashMap<String, Boolean>()

    /** Уже содержит [key]? (для guard'ов «уже видели/финализировали»). */
    fun contains(key: String): Boolean = map.containsKey(key)

    /**
     * Добавляет [key]. Возвращает true, если ключ был НОВЫМ (вставлен), false — если уже
     * присутствовал. Эквивалент `set.add(key)` / `map.put(key,true) == null`.
     */
    fun add(key: String): Boolean {
        if (map.containsKey(key)) return false
        map[key] = true
        evictIfNeeded()
        return true
    }

    private fun evictIfNeeded() {
        while (map.size > maxSize) {
            val eldest = map.keys.firstOrNull() ?: break
            map.remove(eldest)
        }
    }
}
