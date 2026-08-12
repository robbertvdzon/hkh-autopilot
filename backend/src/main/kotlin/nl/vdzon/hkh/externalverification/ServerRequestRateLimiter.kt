package nl.vdzon.hkh.externalverification

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
