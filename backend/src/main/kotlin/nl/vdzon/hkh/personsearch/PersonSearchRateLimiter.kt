package nl.vdzon.hkh.personsearch

import java.util.ArrayDeque

/**
 * Procesbrede rate limiter (niet per sessie, consistent met eerdere externe-API-integraties in
 * deze repo): staat maximaal [maxPerSecond] aanroepen per glijdend venster van één seconde toe.
 * [clockMillis] en [sleep] zijn injecteerbaar zodat tests deterministisch blijven zonder echte
 * wachttijd.
 */
class PersonSearchRateLimiter(
    private val maxPerSecond: Int = 4,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = { millis -> if (millis > 0) Thread.sleep(millis) },
) {
    private val timestamps = ArrayDeque<Long>()

    @Synchronized
    fun acquire() {
        while (true) {
            val now = clockMillis()
            while (timestamps.isNotEmpty() && now - timestamps.peekFirst() >= 1000) {
                timestamps.pollFirst()
            }
            if (timestamps.size < maxPerSecond) {
                timestamps.addLast(now)
                return
            }
            val waitMillis = 1000 - (now - timestamps.peekFirst())
            sleep(waitMillis.coerceAtLeast(1))
        }
    }
}
