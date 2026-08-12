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
               "archive_org":"Historisch Archief","url":"https://www.openarchieven.nl/a:abc",
               "metadataRights":"ALLOWED","privacyStatus":"CLEAR"}
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
    fun `open archieven error response is not reported as an empty available result`() {
        val fixture = startFixture(
            """
            {"error_code":21,"error_description":"Missing required name"}
            """.trimIndent(),
        )
        try {
            val result = OpenArchievenSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                rateLimiter = HistoricalSearchRateLimiter { },
            ).search(HistoricalSearchQuery(text = "geschiedenis"))

            assertEquals(HistoricalTechnicalStatus.INVALID_RESPONSE, result.status)
            assertEquals(emptyList(), result.results)
            assertEquals("Open Archieven retourneerde een foutrespons.", result.message)
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun `adapters fail closed for missing or restricted metadata and privacy`() {
        val fixture = startFixture(
            """
            {"totalResults":3,"items":[
              {"id":"safe","guid":"https://data.example/safe","title":"Veilige titel",
               "metadataRights":"ALLOWED","privacyStatus":"CLEAR","description":"Beschrijving"},
              {"id":"unknown","guid":"https://data.example/unknown","title":"Niet tonen",
               "description":"Ook niet tonen"},
              {"id":"restricted","guid":"https://data.example/restricted","title":"Ook niet tonen",
               "metadataRights":"RESTRICTED","privacyStatus":"CLEAR","person":"Persoon"}
            ]}
            """.trimIndent(),
        )
        try {
            val result = EuropeanaSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                wskey = "test-key",
                clock = fixedClock(),
            ).search(HistoricalSearchQuery(text = "geschiedenis"))

            assertEquals("Veilige titel", result.results[0].title)
            assertEquals("Beschrijving", result.results[0].description)
            assertEquals(null, result.results[1].title)
            assertEquals(null, result.results[1].description)
            assertEquals(null, result.results[2].person)
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

    @Test
    fun `service merges source cursors without duplicating results across pages`() {
        val europeana = recordingAdapter(HistoricalSearchSource.EUROPEANA)
        val open = recordingAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN)
        val service = HistoricalSearchService(listOf(europeana, open))

        val outcome = service.search(HistoricalSearchQuery(text = "kerk", start = 100, limit = 100))

        assertEquals(100, outcome.results.size)
        assertEquals(listOf(0), europeana.queries.map { it.start })
        assertEquals(listOf(0), open.queries.map { it.start })
        assertEquals(100, outcome.results.map { it.sourceRecordId }.distinct().size)

        europeana.queries.clear()
        open.queries.clear()
        val unevenEuropeana = sequenceAdapter(HistoricalSearchSource.EUROPEANA, 1)
        val unevenOpen = sequenceAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN, 150)
        val unevenService = HistoricalSearchService(listOf(unevenEuropeana, unevenOpen))
        val unevenOutcome = unevenService.search(HistoricalSearchQuery(text = "kerk", start = 100, limit = 100))

        assertEquals(51, unevenOutcome.results.size)
        assertEquals("OPEN_ARCHIEVEN-99", unevenOutcome.results.first().sourceRecordId)
        assertEquals(51, unevenOutcome.results.map { it.sourceRecordId }.distinct().size)
        assertEquals(listOf(0), unevenEuropeana.queries.map { it.start })
        assertEquals(listOf(0, 100), unevenOpen.queries.map { it.start })
    }

    @Test
    fun `disabled source does not reserve slots in an available source page`() {
        val disabled = object : HistoricalSearchAdapter {
            override val source = HistoricalSearchSource.EUROPEANA

            override fun search(query: HistoricalSearchQuery) = HistoricalSearchPage(
                source = source,
                results = emptyList(),
                total = 0,
                status = HistoricalTechnicalStatus.DISABLED,
            )
        }
        val open = recordingAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN)
        val outcome = HistoricalSearchService(listOf(disabled, open)).search(
            HistoricalSearchQuery(text = "kerk", limit = 100),
        )

        assertEquals(100, outcome.results.size)
        assertEquals(100, open.queries.last().limit)
        assertEquals(0, open.queries.last().start)
        assertEquals(HistoricalTechnicalStatus.DISABLED, outcome.sources.first().status)
    }

    @Test
    fun `invalid dates and conflicting aliases are rendered fail closed`() {
        val fixture = startFixture(
            """
            {"totalResults":1,"items":[
              {"id":"conflict","guid":"https://data.example/conflict","title":"Titel A",
               "dcTitle":"Titel B","year":"not-a-year","metadataRights":"ALLOWED","privacyStatus":"CLEAR"}
            ]}
            """.trimIndent(),
        )
        try {
            val result = EuropeanaSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                wskey = "test-key",
            ).search(HistoricalSearchQuery(text = "geschiedenis"))

            assertEquals(1, result.results.size)
            assertEquals(null, result.results.single().title)
            assertEquals(null, result.results.single().dateStart)
        } finally {
            fixture.stop()
        }
    }

    private fun fakeAdapter(source: HistoricalSearchSource) = object : HistoricalSearchAdapter {
        override val source = source
        var calls = 0

        override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
            calls++
            return HistoricalSearchPage(source, emptyList(), 0, HistoricalTechnicalStatus.AVAILABLE)
        }
    }

    private fun recordingAdapter(source: HistoricalSearchSource) = object : HistoricalSearchAdapter {
        override val source = source
        val queries = mutableListOf<HistoricalSearchQuery>()

        override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
            queries += query
            val results = (query.start until query.start + query.limit).map { index ->
                HistoricalSearchResult(
                    source = source,
                    sourceRecordId = "$source-$index",
                    stableUrl = "https://example.test/$source/$index",
                    title = null,
                    description = null,
                    person = null,
                    event = null,
                    dateStart = null,
                    dateEnd = null,
                    institution = null,
                    rights = null,
                    privacy = null,
                    retrievedAt = Instant.parse("2026-08-12T00:00:00Z"),
                )
            }
            return HistoricalSearchPage(source, results, 200, HistoricalTechnicalStatus.AVAILABLE)
        }
    }

    private fun sequenceAdapter(source: HistoricalSearchSource, count: Int) = object : HistoricalSearchAdapter {
        override val source = source
        val queries = mutableListOf<HistoricalSearchQuery>()

        override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
            queries += query
            val end = (query.start + query.limit).coerceAtMost(count)
            val results = (query.start until end).map { index ->
                HistoricalSearchResult(
                    source = source,
                    sourceRecordId = "$source-$index",
                    stableUrl = "https://example.test/$source/$index",
                    title = null,
                    description = null,
                    person = null,
                    event = null,
                    dateStart = null,
                    dateEnd = null,
                    institution = null,
                    rights = null,
                    privacy = null,
                    retrievedAt = Instant.parse("2026-08-12T00:00:00Z"),
                )
            }
            return HistoricalSearchPage(
                source, results, count, HistoricalTechnicalStatus.AVAILABLE, consumed = results.size,
            )
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
