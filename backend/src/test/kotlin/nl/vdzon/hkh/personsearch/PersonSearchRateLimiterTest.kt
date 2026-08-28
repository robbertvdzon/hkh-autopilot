package nl.vdzon.hkh.personsearch

import kotlin.test.Test
import kotlin.test.assertEquals

class PersonSearchRateLimiterTest {

    @Test
    fun `allows up to four calls within the same second without sleeping`() {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = PersonSearchRateLimiter(maxPerSecond = 4, clockMillis = { now }, sleep = { sleeps += it })

        repeat(4) { limiter.acquire() }

        assertEquals(emptyList(), sleeps)
    }

    @Test
    fun `sleeps until the oldest call drops out of the one second window for a fifth call`() {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = PersonSearchRateLimiter(
            maxPerSecond = 4,
            clockMillis = { now },
            sleep = { millis ->
                sleeps += millis
                now += millis
            },
        )

        repeat(4) { limiter.acquire() }
        limiter.acquire()

        assertEquals(listOf(1000L), sleeps)
    }
}
