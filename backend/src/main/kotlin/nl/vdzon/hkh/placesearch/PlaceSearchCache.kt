package nl.vdzon.hkh.placesearch

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Kortstondige in-memory TTL-cache, geen structurele database-opslag. Gebruikt voor opgehaalde
 * Wikidata-records en Commons-imageinfo-responses; Wikidata/Commons blijven altijd de bron van
 * waarheid (zichtbare `checkedAt` op het scherm, QID-link resp. bestandspaginalink).
 */
class PlaceSearchCache<K : Any, V : Any>(private val ttl: Duration, private val clock: Clock = Clock.systemUTC()) {
    private data class Entry<V>(val value: V, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<K, Entry<V>>()

    /** Retourneert `null` zonder te cachen wanneer [loader] zelf `null` oplevert (mislukte raadpleging). */
    fun getOrPut(key: K, loader: () -> V?): V? {
        val now = Instant.now(clock)
        val cached = entries[key]
        if (cached != null && cached.expiresAt.isAfter(now)) return cached.value
        val value = loader() ?: return null
        entries[key] = Entry(value, now.plus(ttl))
        return value
    }
}
