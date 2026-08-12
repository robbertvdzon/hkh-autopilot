package nl.vdzon.hkh.externalverification

import kotlin.math.max

fun interface HistoricalMetadataRateLimiter {
    fun awaitPermit()
}

/**
 * Procesbrede limiter voor de gedeelde uitgaande serververbinding. De minimale tussenruimte van
 * 251 ms houdt ook bij tijdvenster-randgevallen maximaal vier verzoeken per seconde over. Omdat
 * alle bronadapters dezelfde singleton gebruiken, kan een andere doelhost of ander doel-IP geen
 * extra bucket openen voor hetzelfde server-uitgaande proces.
 */
class FourPerSecondRateLimiter(
    private val intervalNanos: Long = 251_000_000L,
    private val nowNanos: () -> Long = System::nanoTime,
    private val sleepNanos: (Long) -> Unit = { nanos ->
        Thread.sleep(nanos / 1_000_000L, (nanos % 1_000_000L).toInt())
    },
) : HistoricalMetadataRateLimiter {
    private var nextPermitAt = Long.MIN_VALUE

    override fun awaitPermit() {
        synchronized(this) {
            val now = nowNanos()
            val nextPermit = max(now, nextPermitAt)
            val waitNanos = nextPermit - now
            if (waitNanos > 0) sleepNanos(waitNanos)
            val afterWait = nowNanos()
            nextPermitAt = max(afterWait, nextPermit) + intervalNanos
        }
    }
}
