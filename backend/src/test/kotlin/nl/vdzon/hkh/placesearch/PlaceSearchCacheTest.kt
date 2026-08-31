package nl.vdzon.hkh.placesearch

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class MutableTestClock(var instant: Instant) : java.time.Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): java.time.Clock = this
    override fun instant(): Instant = instant
}

class PlaceSearchCacheTest {

    @Test
    fun `a cached value is returned without invoking the loader again within the ttl`() {
        val clock = MutableTestClock(Instant.parse("2026-08-31T10:00:00Z"))
        val cache = PlaceSearchCache<String, String>(Duration.ofMinutes(5), clock)
        var loadCount = 0

        val first = cache.getOrPut("Q1") { loadCount++; "value" }
        val second = cache.getOrPut("Q1") { loadCount++; "other" }

        assertEquals("value", first)
        assertEquals("value", second)
        assertEquals(1, loadCount)
    }

    @Test
    fun `an expired entry is reloaded`() {
        val clock = MutableTestClock(Instant.parse("2026-08-31T10:00:00Z"))
        val cache = PlaceSearchCache<String, String>(Duration.ofMinutes(5), clock)
        cache.getOrPut("Q1") { "value" }

        clock.instant = clock.instant.plus(Duration.ofMinutes(6))
        val reloaded = cache.getOrPut("Q1") { "fresh" }

        assertEquals("fresh", reloaded)
    }

    @Test
    fun `a failed load is never cached`() {
        val clock = MutableTestClock(Instant.parse("2026-08-31T10:00:00Z"))
        val cache = PlaceSearchCache<String, String>(Duration.ofMinutes(5), clock)

        val result = cache.getOrPut("Q1") { null }

        assertNull(result)
    }
}
