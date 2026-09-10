package nl.vdzon.hkh.topicsearch

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Kortstondige in-memory TTL-cache, geen structurele database-opslag. Naar het patroon van
 * `PlaceSearchCache`; eigen kopie omdat deze module `allowedDependencies = {}` heeft. Gebruikt
 * voor reeds gevalideerde Europeana-records; Europeana blijft altijd de bron van waarheid (de
 * getoonde `checkedAt` is nooit een verwijzing naar deze cache zelf).
 */
class TopicSearchCache<K : Any, V : Any>(private val ttl: Duration, private val clock: Clock = Clock.systemUTC()) {
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
