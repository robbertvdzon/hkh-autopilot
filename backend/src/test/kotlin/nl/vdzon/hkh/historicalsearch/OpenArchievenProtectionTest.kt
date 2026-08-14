package nl.vdzon.hkh.historicalsearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.RestClient
import nl.vdzon.hkh.historicalsearch.api.HistoricalSearchController

class OpenArchievenProtectionTest {
    @Test
    fun `identical concurrent requests use one upstream call and identical normalized page`() {
        val fixture = OpenArchievenProtectionFixture(validOpenArchievenBody("shared"), holdFirstRequest = true)
        val started = CountDownLatch(1)
        fixture.onRequest = { started.countDown() }
        val adapter = adapter(fixture)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..8).map {
                executor.submit<HistoricalSearchPage> { adapter.search(HistoricalSearchQuery(text = "  Heemskerk  ")) }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            fixture.releaseFirstRequest.countDown()
            val pages = futures.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, fixture.calls.get())
            assertTrue(pages.all { it == pages.first() })
            assertEquals(HistoricalTechnicalStatus.AVAILABLE, pages.first().status)
        } finally {
            executor.shutdownNow()
            fixture.stop()
        }
    }

    @Test
    fun `identical valid page is served from cache until expiry and failed pages are not cached`() {
        val clock = OpenArchievenMutableClock(Instant.parse("2026-08-14T00:00:00Z"))
        val fixture = OpenArchievenProtectionFixture(validOpenArchievenBody("cached"))
        val cache = OpenArchievenResponseCache(clock, Duration.ofSeconds(10))
        val adapter = adapter(fixture, clock, cache)
        try {
            adapter.search(HistoricalSearchQuery(text = "cache"))
            adapter.search(HistoricalSearchQuery(text = "cache"))
            assertEquals(1, fixture.calls.get())
            clock.advance(Duration.ofSeconds(11))
            adapter.search(HistoricalSearchQuery(text = "cache"))
            assertEquals(2, fixture.calls.get())
        } finally {
            fixture.stop()
        }

        val invalidFixture = OpenArchievenProtectionFixture("{\"response\":{}}")
        val invalidAdapter = adapter(invalidFixture, clock, OpenArchievenResponseCache(clock, Duration.ofMinutes(1)))
        try {
            assertEquals(HistoricalTechnicalStatus.MISSING_REQUIRED_FIELDS,
                invalidAdapter.search(HistoricalSearchQuery(text = "invalid")).status)
            assertEquals(HistoricalTechnicalStatus.MISSING_REQUIRED_FIELDS,
                invalidAdapter.search(HistoricalSearchQuery(text = "invalid")).status)
            assertEquals(2, invalidFixture.calls.get())
        } finally {
            invalidFixture.stop()
        }
    }

    @Test
    fun `cache key includes normalized context pagination language and archive discriminator without raw terms`() {
        val adapter = OpenArchievenSearchAdapter(
            RestClient.builder().baseUrl("http://127.0.0.1:1").build(),
            rateLimiter = HistoricalSearchRateLimiter { },
        )
        val first = adapter.cacheKeyFor(
            HistoricalSearchQuery(text = " Jan de Vries ", place = "Heemskerk", start = 0, limit = 20),
        )
        val normalizedEquivalent = adapter.cacheKeyFor(
            HistoricalSearchQuery(text = "jan   de   vries", place = "  HEEMSKERK ", start = 0, limit = 20),
        )
        assertEquals(first, normalizedEquivalent)
        assertNotEquals(first, adapter.cacheKeyFor(HistoricalSearchQuery(text = "Jan de Vries", start = 1, limit = 20)))
        assertNotEquals(first, adapter.cacheKeyFor(HistoricalSearchQuery(text = "Jan de Vries", place = "Beverwijk", limit = 20)))
        assertNotEquals(first, adapter.cacheKeyFor(HistoricalSearchQuery(text = "Jan de Vries", place = "Heemskerk", limit = 21)))
        assertEquals("nl", first.language)
        assertTrue(first.isPrivacySafe())
        assertFalse(first.toString().contains("Jan", ignoreCase = true))
        assertFalse(first.toString().contains("Heemskerk", ignoreCase = true))
    }

    @Test
    fun `request budget allows ten burst requests, caps rolling minute and isolates IPs`() {
        val clock = OpenArchievenMutableClock(Instant.parse("2026-08-14T00:00:00Z"))
        val budget = SlidingWindowHistoricalSearchRequestBudget(clock)
        repeat(10) { assertTrue(budget.tryAcquire("192.0.2.1")) }
        assertFalse(budget.tryAcquire("192.0.2.1"))
        assertTrue(budget.tryAcquire("192.0.2.2"))
        repeat(50) {
            clock.advance(Duration.ofSeconds(1))
            assertTrue(budget.tryAcquire("192.0.2.1"))
        }
        assertFalse(budget.tryAcquire("192.0.2.1"))
        clock.advance(Duration.ofSeconds(10))
        assertTrue(budget.tryAcquire("192.0.2.1"))
    }

    @Test
    fun `forwarded IP is trusted only for configured direct proxy`() {
        val resolver = HistoricalClientIpResolver(setOf("10.0.0.1"))
        val trusted = MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.1"
            addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.2")
        }
        val untrusted = MockHttpServletRequest().apply {
            remoteAddr = "198.51.100.20"
            addHeader("X-Forwarded-For", "198.51.100.10")
        }
        assertEquals("198.51.100.10", resolver.resolve(trusted))
        assertEquals("198.51.100.20", resolver.resolve(untrusted))
    }

    @Test
    fun `upstream 429 retries once only when Retry-After is usable and caches successful retry`() {
        val fixture = OpenArchievenProtectionFixture(validOpenArchievenBody("retry"), firstResponseStatus = 429,
            firstRetryAfter = "1")
        val sleeps = mutableListOf<Duration>()
        val adapter = adapter(fixture, retrySleeper = { sleeps += it })
        try {
            val result = adapter.search(HistoricalSearchQuery(text = "retry"))
            assertEquals(HistoricalTechnicalStatus.AVAILABLE, result.status)
            assertEquals(2, fixture.calls.get())
            assertEquals(listOf(Duration.ofSeconds(1)), sleeps)
            adapter.search(HistoricalSearchQuery(text = "retry"))
            assertEquals(2, fixture.calls.get())
        } finally {
            fixture.stop()
        }

        val tooLong = OpenArchievenProtectionFixture(validOpenArchievenBody("too-long"), firstResponseStatus = 429,
            firstRetryAfter = "3")
        val noRetryAdapter = adapter(tooLong, retrySleeper = { error("retry should not be attempted") })
        try {
            assertEquals(HistoricalTechnicalStatus.RATE_LIMITED,
                noRetryAdapter.search(HistoricalSearchQuery(text = "too-long")).status)
            assertEquals(1, tooLong.calls.get())
        } finally {
            tooLong.stop()
        }
    }

    @Test
    fun `budget denial on incoming request is safe HTTP 429 and retry budget denial stays source status`() {
        val deniedBudget = HistoricalSearchRequestBudget { false }
        val controller = HistoricalSearchController(
            HistoricalSearchService(listOf(object : HistoricalSearchAdapter {
                override val source = HistoricalSearchSource.OPEN_ARCHIEVEN
                override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
                    throw HistoricalSearchRequestBudgetExceededException()
                }
            })),
            requestBudget = deniedBudget,
        )
        val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()
        mockMvc.perform(get("/api/historical-search").param("source", "OPEN_ARCHIEVEN").param("q", "private-name"))
            .andExpect(status().isTooManyRequests)
            .andExpect(content().json("{\"error\":\"RATE_LIMITED\"}"))

        val fixture = OpenArchievenProtectionFixture(validOpenArchievenBody("blocked-retry"), firstResponseStatus = 429,
            firstRetryAfter = "0")
        val oneAttemptBudget = object : HistoricalSearchRequestBudget {
            var calls = 0
            override fun tryAcquire(clientIp: String): Boolean = ++calls == 1
        }
        val adapter = adapter(fixture, requestBudget = oneAttemptBudget, retrySleeper = { })
        try {
            val result = HistoricalSearchRequestContext.withIdentity(
                HistoricalSearchRequestIdentity("192.0.2.7", oneAttemptBudget),
            ) { adapter.search(HistoricalSearchQuery(text = "retry-budget")) }
            assertEquals(HistoricalTechnicalStatus.RATE_LIMITED, result.status)
            assertEquals(1, fixture.calls.get())
        } finally {
            fixture.stop()
        }
    }

    private fun adapter(
        fixture: OpenArchievenProtectionFixture,
        clock: Clock = Clock.systemUTC(),
        cache: OpenArchievenResponseCache = OpenArchievenResponseCache(clock, Duration.ofMinutes(1)),
        requestBudget: HistoricalSearchRequestBudget = AllowAllHistoricalSearchRequestBudget,
        retrySleeper: (Duration) -> Unit = {},
    ) = OpenArchievenSearchAdapter(
        RestClient.builder().baseUrl(fixture.baseUrl).build(),
        rateLimiter = HistoricalSearchRateLimiter { },
        clock = clock,
        configured = true,
        requestBudget = requestBudget,
        responseCache = cache,
        retrySleeper = retrySleeper,
    )
}

private class OpenArchievenMutableClock(initial: Instant) : Clock() {
    private var current = initial

    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}

private class OpenArchievenProtectionFixture(
    private val responseBody: String,
    private val firstResponseStatus: Int = 200,
    private val firstRetryAfter: String? = null,
    private val holdFirstRequest: Boolean = false,
) {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val calls = AtomicInteger()
    val releaseFirstRequest = CountDownLatch(1)
    var onRequest: () -> Unit = {}
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange -> respond(exchange) }
        server.start()
    }

    private fun respond(exchange: HttpExchange) {
        val call = calls.incrementAndGet()
        onRequest()
        if (holdFirstRequest && call == 1) releaseFirstRequest.await(5, TimeUnit.SECONDS)
        val status = if (call == 1) firstResponseStatus else 200
        val body = if (status == 200) responseBody else "upstream body must not escape"
        exchange.responseHeaders.set("Content-Type", "application/json")
        firstRetryAfter?.let { if (call == 1) exchange.responseHeaders.set("Retry-After", it) }
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    fun stop() = server.stop(0)
}

private fun validOpenArchievenBody(uuid: String): String =
    """{"response":{"number_found":1,"docs":[{"source_name":"Open Archieven","uuid":"$uuid","original_source_url":"https://example.test/$uuid","metadataRights":"ALLOWED","privacyStatus":"CLEAR"}]}}"""
