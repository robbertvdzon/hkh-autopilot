package nl.vdzon.hkh.historicalsearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

class HistoricalSearchTest {
    @Test
    fun `normalizes optional filters and validates inclusive year period`() {
        val query = HistoricalSearchValidation.normalize(
            text = "  kasteel ", place = "", person = null, event = "", fromYear = "1800", toYear = "1899",
            source = "open_archieven", start = 100, limit = 100,
        )

        assertEquals("kasteel", query.text)
        assertEquals(null, query.place)
        assertEquals(HistoricalSearchSource.OPEN_ARCHIEVEN, query.source)
        assertEquals(1800, query.fromYear)
        assertEquals(1899, query.toYear)
        assertFailsWith<IllegalArgumentException> {
            HistoricalSearchValidation.normalize(null, null, null, null, "1899", "1800", null, 0, 100)
        }
        assertFailsWith<IllegalArgumentException> {
            HistoricalSearchValidation.normalize(null, null, null, null, "18", null, null, 0, 100)
        }
    }

    @Test
    fun `europeana repeats qf and only returns source supplied stable links`() {
        val fixture = startFixture(
            """
            {"totalResults":2,"items":[
              {"id":"/record-1","guid":"https://data.example/record-1","title":"Kasteel","year":"1800","provider":"Archief"},
              {"id":"/record-2","guid":"not-a-url","title":"Niet tonen"}
            ]}
            """.trimIndent(),
        )
        try {
            val adapter = EuropeanaSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                wskey = "test-key",
                clock = fixedClock(),
            )
            val result = adapter.search(
                HistoricalSearchQuery(text = "kasteel", place = "Heemskerk", person = "Jan", fromYear = 1800, toYear = 1899,
                    start = 20, limit = 50),
            )
            val params = UriComponentsBuilder.fromUriString(fixture.lastPath).build().queryParams
            assertEquals("test-key", params.getFirst("wskey"))
            assertEquals("kasteel", params.getFirst("query"))
            assertEquals(listOf("who:Jan", "where:Heemskerk", "YEAR:[1800%20TO%201899]"), params.get("qf"))
            assertEquals("50", params.getFirst("rows"))
            assertEquals("20", params.getFirst("start"))
            assertEquals(1, result.results.size)
            assertEquals("https://data.example/record-1", result.results.single().stableUrl)
            assertEquals(HistoricalTechnicalStatus.AVAILABLE, result.status)
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun `open archieven uses bounded page, descriptive user agent and year name syntax`() {
        val fixture = startFixture(
            """
            {"response":{"number_found":1,"docs":[
              {"identifier":"abc","personname":"Jan de Vries","eventtype":"Huwelijk","eventdate":{"year":1900},
               "archive_org":"Historisch Archief","url":"https://www.openarchieven.nl/a:abc"}
            ]}}
            """.trimIndent(),
        )
        try {
            val adapter = OpenArchievenSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                rateLimiter = HistoricalSearchRateLimiter { },
                clock = fixedClock(),
            )
            val result = adapter.search(
                HistoricalSearchQuery(person = "Jan", place = "Heemskerk", fromYear = 1900, toYear = 1910, limit = 150),
            )
            val params = UriComponentsBuilder.fromUriString(fixture.lastPath).build().queryParams
            assertEquals("Jan%201900-1910", params.getFirst("name"))
            assertEquals("Heemskerk", params.getFirst("eventplace"))
            assertEquals("100", params.getFirst("number_show"))
            assertEquals("0", params.getFirst("start"))
            assertEquals("HKH-Autopilot-HistoricalSearch/1.0", fixture.lastUserAgent)
            assertEquals("abc", result.results.single().sourceRecordId)
            assertEquals("1900", result.results.single().dateStart)
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun `open archieven marks event-only searches as low certainty`() {
        val fixture = startFixture("{\"response\":{\"number_found\":0,\"docs\":[]}}")
        try {
            val adapter = OpenArchievenSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                rateLimiter = HistoricalSearchRateLimiter { },
            )
            adapter.search(HistoricalSearchQuery(event = "Huwelijk", fromYear = 1900, toYear = 1910))
            val params = UriComponentsBuilder.fromUriString(fixture.lastPath).build().queryParams
            assertEquals("~Huwelijk%201900-1910", params.getFirst("name"))
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun `missing Europeana key disables only Europeana`() {
        val adapter = EuropeanaSearchAdapter(
            RestClient.builder().baseUrl("http://127.0.0.1:1").build(),
            wskey = "",
        )
        val result = adapter.search(HistoricalSearchQuery(text = "kasteel"))
        assertEquals(HistoricalTechnicalStatus.DISABLED, result.status)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `service queries selected adapters and keeps source statuses separate`() {
        val europeana = fakeAdapter(HistoricalSearchSource.EUROPEANA)
        val open = fakeAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN)
        val service = HistoricalSearchService(listOf(europeana, open))
        val outcome = service.search(HistoricalSearchQuery(text = "kerk", source = HistoricalSearchSource.OPEN_ARCHIEVEN))

        assertEquals(0, europeana.calls)
        assertEquals(1, open.calls)
        assertEquals(listOf(HistoricalSearchSource.OPEN_ARCHIEVEN), outcome.sources.map { it.source })
    }

    private fun fakeAdapter(source: HistoricalSearchSource) = object : HistoricalSearchAdapter {
        override val source = source
        var calls = 0

        override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
            calls++
            return HistoricalSearchPage(source, emptyList(), 0, HistoricalTechnicalStatus.AVAILABLE)
        }
    }

    private fun fixedClock() = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC)

    private fun startFixture(body: String): Fixture {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val fixture = Fixture(server, body)
        server.createContext("/") { exchange -> fixture.respond(exchange) }
        server.start()
        return fixture
    }

    private class Fixture(private val server: HttpServer, private val body: String) {
        val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"
        var lastPath: String = ""
        var lastUserAgent: String? = null

        fun respond(exchange: HttpExchange) {
            lastPath = exchange.requestURI.toString()
            lastUserAgent = exchange.requestHeaders.getFirst("User-Agent")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        fun stop() = server.stop(0)
    }
}
