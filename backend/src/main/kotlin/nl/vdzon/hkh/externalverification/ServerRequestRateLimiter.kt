package nl.vdzon.hkh.externalverification

import java.net.InetAddress
import java.net.URI
import kotlin.math.max

fun interface HistoricalMetadataRateLimiter {
    fun awaitPermit(serverKey: String)
}

/**
 * Eenvoudige servergerichte limiter. De minimale tussenruimte van 251 ms houdt ook bij
 * tijdvenster-randgevallen maximaal vier verzoeken per seconde over. De sleutel wordt door de
 * adapter als server-uitgaand netwerkdoel aangeleverd; er is geen eindgebruikers-IP bij betrokken.
 */
class FourPerSecondRateLimiter(
    private val intervalNanos: Long = 251_000_000L,
    private val nowNanos: () -> Long = System::nanoTime,
    private val sleepNanos: (Long) -> Unit = { nanos ->
        Thread.sleep(nanos / 1_000_000L, (nanos % 1_000_000L).toInt())
    },
) : HistoricalMetadataRateLimiter {
    private val nextPermitByServer = mutableMapOf<String, Long>()

    override fun awaitPermit(serverKey: String) {
        synchronized(nextPermitByServer) {
            val now = nowNanos()
            val nextPermit = nextPermitByServer[serverKey] ?: now
            val waitNanos = nextPermit - now
            if (waitNanos > 0) sleepNanos(waitNanos)
            val afterWait = nowNanos()
            nextPermitByServer[serverKey] = max(afterWait, nextPermit) + intervalNanos
        }
    }
}

/**
 * Resolves a source base URL to the network identity used by the outbound limiter. DNS aliases
 * that resolve to the same address therefore share one bucket instead of getting one bucket per
 * hostname. All resolved addresses are part of the key, which stays fail-safe when a host has
 * multiple address records.
 */
internal fun resolveServerRateLimitKey(
    baseUrl: String,
    resolveHost: (String) -> Array<InetAddress> = InetAddress::getAllByName,
): String {
    val uri = runCatching { URI(baseUrl) }.getOrNull()
    val host = uri?.host?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        ?: return "host:${baseUrl.trim()}"
    val addresses = runCatching {
        resolveHost(host)
            .mapNotNull { it.hostAddress?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())
    return addresses.takeIf { it.isNotEmpty() }?.let { "ip:${it.joinToString(",")}" } ?: "host:$host"
}
