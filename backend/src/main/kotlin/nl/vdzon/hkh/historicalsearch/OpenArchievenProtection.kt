package nl.vdzon.hkh.historicalsearch

import jakarta.servlet.http.HttpServletRequest
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.Locale
import java.security.MessageDigest

/**
 * Rolling one-minute budget plus a token bucket. The two limits are intentional: the rolling
 * window caps sustained traffic at 60 requests, while the bucket caps an immediate burst at 10.
 */
class SlidingWindowHistoricalSearchRequestBudget(
    private val clock: Clock = Clock.systemUTC(),
    private val maxPerMinute: Int = 60,
    private val burstCapacity: Int = 10,
    private val refillPerSecond: Double = 1.0,
    private val maxClients: Int = 10_000,
) : HistoricalSearchRequestBudget {
    private data class ClientState(
        val attempts: ArrayDeque<Long> = ArrayDeque(),
        var tokens: Double,
        var lastRefillMillis: Long,
        var lastSeenMillis: Long,
    )

    private val states = LinkedHashMap<String, ClientState>(16, 0.75f, true)

    init {
        require(maxPerMinute > 0)
        require(burstCapacity > 0)
        require(refillPerSecond > 0)
        require(maxClients > 0)
    }

    override fun tryAcquire(clientIp: String): Boolean = synchronized(states) {
        val now = clock.millis()
        val state = states.getOrPut(clientIp) {
            ClientState(tokens = burstCapacity.toDouble(), lastRefillMillis = now, lastSeenMillis = now)
        }
        state.lastSeenMillis = now
        while (state.attempts.peekFirst()?.let { it <= now - 60_000L } == true) {
            state.attempts.removeFirst()
        }
        val elapsedSeconds = ((now - state.lastRefillMillis).coerceAtLeast(0L)) / 1_000.0
        state.tokens = (state.tokens + elapsedSeconds * refillPerSecond).coerceAtMost(burstCapacity.toDouble())
        state.lastRefillMillis = now
        val allowed = state.attempts.size < maxPerMinute && state.tokens >= 1.0
        if (allowed) {
            state.tokens -= 1.0
            state.attempts.addLast(now)
        }
        evictIfNeeded(now)
        allowed
    }

    private fun evictIfNeeded(now: Long) {
        while (states.size > maxClients) {
            val iterator = states.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        states.entries.removeIf { (_, value) ->
            value.attempts.isEmpty() && now - value.lastSeenMillis > 120_000L
        }
    }
}

/** Resolves a client IP only when the direct peer is an explicitly trusted proxy. */
class HistoricalClientIpResolver(
    trustedProxyAddresses: Set<String> = emptySet(),
    private val forwardedHeaderName: String = "X-Forwarded-For",
) {
    private val trusted = trustedProxyAddresses.mapNotNull(::parseTrustedNetwork)

    fun resolve(request: HttpServletRequest): String =
        if (normalizeAddress(request.remoteAddr)?.let { address -> trusted.any { it.contains(address) } } == true) {
            forwardedClientIp(request.getHeader(forwardedHeaderName)) ?: directIp(request.remoteAddr)
        } else {
            directIp(request.remoteAddr)
        }

    private fun directIp(value: String?): String = normalizeIp(value) ?: "unknown"

    private fun forwardedClientIp(value: String?): String? = value
        ?.split(',')
        ?.asSequence()
        ?.mapNotNull(::normalizeForwardedValue)
        ?.firstOrNull()

    private fun normalizeForwardedValue(raw: String): String? {
        val value = raw.trim().removePrefix("for=").trim().trim('"')
        val withoutPort = if (value.startsWith("[") && value.contains(']')) {
            value.substringAfter('[').substringBefore(']')
        } else {
            value
        }
        return normalizeIp(withoutPort)
    }

    private data class TrustedNetwork(
        val address: InetAddress,
        val prefixLength: Int,
    ) {
        fun contains(candidate: InetAddress): Boolean {
            val networkBytes = address.address
            val candidateBytes = candidate.address
            if (networkBytes.size != candidateBytes.size) return false
            val completeBytes = prefixLength / 8
            if (!networkBytes.copyOf(completeBytes).contentEquals(candidateBytes.copyOf(completeBytes))) return false
            val remainingBits = prefixLength % 8
            if (remainingBits == 0) return true
            val mask = (0xff shl (8 - remainingBits)) and 0xff
            return (networkBytes[completeBytes].toInt() and mask) ==
                (candidateBytes[completeBytes].toInt() and mask)
        }
    }

    private fun parseTrustedNetwork(raw: String): TrustedNetwork? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val slash = value.indexOf('/')
        val addressText = if (slash >= 0) value.substring(0, slash) else value
        val address = normalizeAddress(addressText) ?: return null
        val prefixLength = if (slash >= 0) {
            value.substring(slash + 1).toIntOrNull()?.takeIf { it in 0..address.address.size * 8 } ?: return null
        } else {
            address.address.size * 8
        }
        return TrustedNetwork(address, prefixLength)
    }

    private fun normalizeIp(value: String?): String? = normalizeAddress(value)?.hostAddress?.lowercase(Locale.ROOT)

    private fun normalizeAddress(value: String?): InetAddress? {
        val candidate = value?.trim()?.removePrefix("[")?.removeSuffix("]") ?: return null
        if (candidate.isEmpty() || candidate.any { it == '/' || it == '%' } ||
            !(candidate.all { it.isDigit() || it in ".:" || it in 'a'..'f' || it in 'A'..'F' })
        ) return null
        return runCatching { InetAddress.getByName(candidate) }.getOrNull()
    }
}

data class OpenArchievenCacheKey(
    val source: HistoricalSearchSource,
    /** SHA-256 of the complete normalized provider context; never contains search text. */
    val normalizedContextDigest: String,
    val start: Int,
    val limit: Int,
    val language: String,
)

class OpenArchievenResponseCache(
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofSeconds(30),
    private val maxEntries: Int = 1_024,
) {
    private data class Entry(val page: HistoricalSearchPage, val expiresAt: Instant)
    private val entries = LinkedHashMap<OpenArchievenCacheKey, Entry>(16, 0.75f, true)

    init {
        require(!ttl.isNegative && !ttl.isZero)
        require(maxEntries > 0)
    }

    fun get(key: OpenArchievenCacheKey): HistoricalSearchPage? = synchronized(entries) {
        val entry = entries[key] ?: return@synchronized null
        if (!entry.expiresAt.isAfter(clock.instant()) || !usable(entry.page)) {
            entries.remove(key)
            return@synchronized null
        }
        entry.page
    }

    fun put(key: OpenArchievenCacheKey, page: HistoricalSearchPage) {
        if (!usable(page)) return
        synchronized(entries) {
            entries[key] = Entry(page, clock.instant().plus(ttl))
            while (entries.size > maxEntries) entries.entries.iterator().apply {
                if (hasNext()) { next(); remove() }
            }
        }
    }

    private fun usable(page: HistoricalSearchPage): Boolean =
        page.source == HistoricalSearchSource.OPEN_ARCHIEVEN &&
            page.status == HistoricalTechnicalStatus.AVAILABLE &&
            page.total >= 0 && page.consumed >= 0 &&
            page.results.all { it.source == HistoricalSearchSource.OPEN_ARCHIEVEN }
}

fun OpenArchievenCacheKey.isPrivacySafe(): Boolean = normalizedContextDigest.matches(Regex("[0-9a-f]{64}"))

internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
