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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.hamcrest.Matchers.containsString
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import nl.vdzon.hkh.historicalsearch.api.HistoricalSearchController

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
    fun `relations use only certain exact context, exclude opened result and keep visible order`() {
        val opened = historicalResult(HistoricalSearchSource.OPEN_ARCHIEVEN, 0).copy(
            place = "  Heemskerk ",
            person = "Jan",
            placeStatus = HistoricalContextStatus.AVAILABLE,
            personStatus = HistoricalContextStatus.AVAILABLE,
            dateStart = "1900",
            dateEnd = "1910",
        )
        val placeMatch = historicalResult(HistoricalSearchSource.OPEN_ARCHIEVEN, 1).copy(
            place = "HEEMSKERK",
            placeStatus = HistoricalContextStatus.AVAILABLE,
            dateStart = "1905",
            dateEnd = "1915",
        )
        val periodOnly = historicalResult(HistoricalSearchSource.OPEN_ARCHIEVEN, 2).copy(
            dateStart = "1905",
            dateEnd = "1906",
        )
        val uncertainPerson = historicalResult(HistoricalSearchSource.OPEN_ARCHIEVEN, 3).copy(
            person = "JAN",
            personStatus = HistoricalContextStatus.UNCERTAIN,
        )
        val personMatch = historicalResult(HistoricalSearchSource.OPEN_ARCHIEVEN, 4).copy(
            person = " Jan ",
            personStatus = HistoricalContextStatus.AVAILABLE,
        )
        val thirdMatch = historicalResult(HistoricalSearchSource.OPEN_ARCHIEVEN, 5).copy(
            event = "Huwelijk",
            eventStatus = HistoricalContextStatus.AVAILABLE,
        )
        val fourthMatch = historicalResult(HistoricalSearchSource.OPEN_ARCHIEVEN, 6).copy(
            place = "Heemskerk",
            placeStatus = HistoricalContextStatus.AVAILABLE,
        )

        val relations = HistoricalSearchRelations.find(
            opened.copy(event = "Huwelijk", eventStatus = HistoricalContextStatus.AVAILABLE),
            listOf(opened, placeMatch, periodOnly, uncertainPerson, personMatch, thirdMatch, fourthMatch),
        )

        assertEquals(listOf("OPEN_ARCHIEVEN-1", "OPEN_ARCHIEVEN-4", "OPEN_ARCHIEVEN-5"), relations.map { it.sourceRecordId })
        assertEquals(listOf("Plaats"), relations.first().sharedFields.map { it.field })
        assertEquals(listOf("Persoon"), relations[1].sharedFields.map { it.field })
        assertEquals(listOf("Gebeurtenis"), relations[2].sharedFields.map { it.field })
        assertTrue(relations.first().periodOverlaps)
        assertFalse(relations[1].periodOverlaps)
    }

    @Test
    fun `adapter distinguishes missing and contradictory place metadata`() {
        val fixture = startFixture(
            """
            {"totalResults":2,"items":[
              {"id":"uncertain","guid":"https://data.example/uncertain","place":["Heemskerk","Beverwijk"],"metadataRights":"ALLOWED","privacyStatus":"CLEAR"},
              {"id":"missing","guid":"https://data.example/missing","metadataRights":"ALLOWED","privacyStatus":"CLEAR"}
            ]}
            """.trimIndent(),
        )
        try {
            val results = EuropeanaSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                wskey = "test-key",
                clock = fixedClock(),
            ).search(HistoricalSearchQuery(text = "geschiedenis")).results

            assertEquals(null, results[0].place)
            assertEquals(HistoricalContextStatus.UNCERTAIN, results[0].placeStatus)
            assertEquals(null, results[1].place)
            assertEquals(HistoricalContextStatus.MISSING, results[1].placeStatus)
        } finally {
            fixture.stop()
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
    fun `adapters map only complete explicit source relationships in provider order`() {
        val fixture = startFixture(
            """
            {"response":{"number_found":1,"docs":[
              {"identifier":"abc","url":"https://www.openarchieven.nl/a:abc",
               "metadataRights":"ALLOWED","privacyStatus":"CLEAR",
               "relationships":[
                 {"type":"isPartOf","source":{"name":"Open Archieven"},
                  "target":{"name":"Register 1900","uri":"https://data.example/target/1","link":"https://source.example/target/1"}},
                 {"type":"derivedFrom","source":{"name":"Open Archieven"},
                  "target":{"name":"Geen URI","link":"https://source.example/target/2"}},
                 {"type":"relatedTo","source":{"name":"Open Archieven"},
                  "target":{"name":"Geen link","uri":"https://data.example/target/3","link":"javascript:alert(1)"}},
                 {"type":"afgeleid","source":{},
                  "target":{"name":"Uit periode-overlap","uri":"https://data.example/target/4","link":"https://source.example/target/4"}}
               ]}
            ]}}
            """.trimIndent(),
        )
        try {
            val result = OpenArchievenSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                rateLimiter = HistoricalSearchRateLimiter { },
                clock = fixedClock(),
            ).search(HistoricalSearchQuery(text = "geschiedenis"))

            assertEquals(1, result.results.single().relationships.size)
            val relationship = result.results.single().relationships.single()
            assertEquals("isPartOf", relationship.type)
            assertEquals("Open Archieven", relationship.source.name)
            assertEquals("Register 1900", relationship.target.name)
            assertEquals("https://data.example/target/1", relationship.target.uri)
            assertEquals("https://source.example/target/1", relationship.target.link)
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun `relationships are removed with restricted metadata`() {
        val fixture = startFixture(
            """
            {"totalResults":1,"items":[
              {"id":"restricted","guid":"https://data.example/restricted",
               "metadataRights":"RESTRICTED","privacyStatus":"CLEAR",
               "relationships":[{"type":"relatedTo","source":{"name":"Europeana"},
                 "target":{"name":"Target","uri":"https://data.example/target","link":"https://source.example/target"}}]}
            ]}
            """.trimIndent(),
        )
        try {
            val result = EuropeanaSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                wskey = "test-key",
                clock = fixedClock(),
            ).search(HistoricalSearchQuery(text = "geschiedenis"))

            assertTrue(result.results.single().relationships.isEmpty())
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
    fun `incomplete Europeana response is reported as invalid`() {
        val fixture = startFixture("{}")
        try {
            val result = EuropeanaSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                wskey = "test-key",
            ).search(HistoricalSearchQuery(text = "geschiedenis"))

            assertEquals(HistoricalTechnicalStatus.INVALID_RESPONSE, result.status)
            assertTrue(result.results.isEmpty())
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun `incomplete Open Archieven response is reported as invalid`() {
        val fixture = startFixture("{\"response\":{}}")
        try {
            val result = OpenArchievenSearchAdapter(
                RestClient.builder().baseUrl(fixture.baseUrl).build(),
                rateLimiter = HistoricalSearchRateLimiter { },
            ).search(HistoricalSearchQuery(text = "geschiedenis"))

            assertEquals(HistoricalTechnicalStatus.INVALID_RESPONSE, result.status)
            assertTrue(result.results.isEmpty())
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
        assertEquals(HistoricalSearchState.NO_RESULTS, outcome.state)
        assertEquals(0, outcome.sources.single().resultCount)
        assertEquals(0, outcome.sources.single().heemskerkCount)
    }

    @Test
    fun `service counts only visible certain normalized Heemskerk place metadata per source`() {
        val europeana = object : HistoricalSearchAdapter {
            override val source = HistoricalSearchSource.EUROPEANA

            override fun search(query: HistoricalSearchQuery) = HistoricalSearchPage(
                source = source,
                results = listOf(
                    historicalResult(source, 1).copy(
                        place = "  H\uFF25\uFF25\uFF2D\uFF33\uFF2B\uFF45\uFF52\uFF2B\t",
                        placeStatus = HistoricalContextStatus.AVAILABLE,
                    ),
                    historicalResult(source, 2).copy(
                        place = "Heemskerk\u00A0",
                        placeStatus = HistoricalContextStatus.AVAILABLE,
                    ),
                    historicalResult(source, 3).copy(
                        place = "Heemskerk",
                        placeStatus = HistoricalContextStatus.UNCERTAIN,
                    ),
                    historicalResult(source, 4).copy(
                        place = "Beverwijk",
                        placeStatus = HistoricalContextStatus.AVAILABLE,
                    ),
                    historicalResult(source, 5),
                ),
                total = 5,
                status = HistoricalTechnicalStatus.AVAILABLE,
                consumed = 5,
            )
        }
        val open = sequenceAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN, 1)

        val outcome = HistoricalSearchService(listOf(europeana, open)).search(
            HistoricalSearchQuery(text = "kerk"),
        )

        val europeanaStatus = outcome.sources.first { it.source == HistoricalSearchSource.EUROPEANA }
        val openStatus = outcome.sources.first { it.source == HistoricalSearchSource.OPEN_ARCHIEVEN }
        assertEquals(5, europeanaStatus.resultCount)
        assertEquals(2, europeanaStatus.heemskerkCount)
        assertEquals(1, openStatus.resultCount)
        assertEquals(0, openStatus.heemskerkCount)
    }

    @Test
    fun `service returns only available totals and marks partial availability`() {
        val disabled = object : HistoricalSearchAdapter {
            override val source = HistoricalSearchSource.EUROPEANA

            override fun search(query: HistoricalSearchQuery) = HistoricalSearchPage(
                source, emptyList(), 999, HistoricalTechnicalStatus.DISABLED,
            )
        }
        val available = sequenceAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN, 1)

        val outcome = HistoricalSearchService(listOf(disabled, available)).search(
            HistoricalSearchQuery(text = "kerk"),
        )

        assertEquals(HistoricalSearchState.PARTIAL_AVAILABILITY, outcome.state)
        assertEquals(1, outcome.total)
        assertEquals(1, outcome.results.size)
        assertEquals(HistoricalSourceMessages.NOT_CONFIGURED, outcome.sources.first().message)
        assertEquals(null, outcome.sources.first().resultCount)
        assertEquals(1, outcome.sources.last().resultCount)
    }

    @Test
    fun `temporary source failure keeps available results and contributes no total`() {
        val temporarilyUnavailable = object : HistoricalSearchAdapter {
            override val source = HistoricalSearchSource.EUROPEANA

            override fun search(query: HistoricalSearchQuery) = HistoricalSearchPage(
                source, emptyList(), 999, HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE,
            )
        }
        val available = sequenceAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN, 2)

        val outcome = HistoricalSearchService(listOf(temporarilyUnavailable, available)).search(
            HistoricalSearchQuery(text = "kerk"),
        )

        assertEquals(HistoricalSearchState.PARTIAL_AVAILABILITY, outcome.state)
        assertEquals(2, outcome.total)
        assertEquals(2, outcome.results.size)
        assertEquals(
            listOf(
                HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE,
                HistoricalTechnicalStatus.AVAILABLE,
            ),
            outcome.sources.map { it.status },
        )
        assertEquals(null, outcome.sources.first().resultCount)
        assertEquals(2, outcome.sources.last().resultCount)
    }

    @Test
    fun `service distinguishes no results from complete source failure`() {
        val empty = fakeAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN)
        val noResults = HistoricalSearchService(listOf(empty)).search(
            HistoricalSearchQuery(text = "kerk", source = HistoricalSearchSource.OPEN_ARCHIEVEN),
        )
        assertEquals(HistoricalSearchState.NO_RESULTS, noResults.state)
        assertEquals(0, noResults.total)

        val failed = object : HistoricalSearchAdapter {
            override val source = HistoricalSearchSource.OPEN_ARCHIEVEN

            override fun search(query: HistoricalSearchQuery) = HistoricalSearchPage(
                source, emptyList(), 100, HistoricalTechnicalStatus.INVALID_RESPONSE,
                "provider secret=do-not-return",
            )
        }
        val sourceFailure = HistoricalSearchService(listOf(failed)).search(
            HistoricalSearchQuery(text = "kerk", source = HistoricalSearchSource.OPEN_ARCHIEVEN),
        )
        assertEquals(HistoricalSearchState.SOURCE_FAILURE, sourceFailure.state)
        assertEquals(0, sourceFailure.total)
        assertTrue(sourceFailure.results.isEmpty())
        assertEquals(HistoricalSourceMessages.INVALID_RESPONSE, sourceFailure.sources.single().message)
        assertEquals(null, sourceFailure.sources.single().resultCount)
        assertEquals(null, sourceFailure.sources.single().heemskerkCount)
    }

    @Test
    fun `controller exposes the historical HTTP contract and rejects invalid periods`() {
        val adapter = object : HistoricalSearchAdapter {
            override val source = HistoricalSearchSource.OPEN_ARCHIEVEN

            override fun search(query: HistoricalSearchQuery) = HistoricalSearchPage(
                source = source,
                results = listOf(
                    HistoricalSearchResult(
                        source = source,
                        sourceRecordId = "record-1",
                        stableUrl = "https://example.test/record-1",
                        title = null,
                        description = null,
                        person = null,
                        event = null,
                        dateStart = null,
                        dateEnd = null,
                        institution = null,
                        rights = null,
                        privacy = null,
                        retrievedAt = fixedClock().instant(),
                        relationships = listOf(
                            HistoricalSearchRelationship(
                                type = "isPartOf",
                                source = HistoricalRelationshipSource("Open Archieven"),
                                target = HistoricalRelationshipTarget(
                                    name = "Register 1900",
                                    uri = "https://data.example/target/1",
                                    link = "https://source.example/target/1",
                                ),
                            ),
                        ),
                    ),
                ),
                total = 1,
                status = HistoricalTechnicalStatus.AVAILABLE,
            )
        }
        val mockMvc: MockMvc = MockMvcBuilders
            .standaloneSetup(HistoricalSearchController(HistoricalSearchService(listOf(adapter))))
            .build()

        mockMvc.perform(
            get("/api/historical-search")
                .param("q", "kerk")
                .param("source", "OPEN_ARCHIEVEN")
                .param("start", "0")
                .param("limit", "1"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].sourceRecordId").value("record-1"))
            .andExpect(jsonPath("$.results[0].stableUrl").value("https://example.test/record-1"))
            .andExpect(jsonPath("$.results[0].relationships").isArray)
            .andExpect(jsonPath("$.results[0].relationships[0].type").value("isPartOf"))
            .andExpect(jsonPath("$.results[0].relationships[0].source.name").value("Open Archieven"))
            .andExpect(jsonPath("$.results[0].relationships[0].target.name").value("Register 1900"))
            .andExpect(jsonPath("$.results[0].relationships[0].target.uri").value("https://data.example/target/1"))
            .andExpect(jsonPath("$.results[0].relationships[0].target.link").value("https://source.example/target/1"))
            .andExpect(jsonPath("$.sources[0].status").value("AVAILABLE"))
            .andExpect(jsonPath("$.sources[0].resultCount").value(1))
            .andExpect(jsonPath("$.sources[0].heemskerkCount").value(0))
            .andExpect(jsonPath("$.state").value("RESULTS"))
            .andExpect(jsonPath("$.limit").value(1))

        mockMvc.perform(
            get("/api/historical-search")
                .param("q", "kerk")
                .param("fromYear", "1900"),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(containsString("samen")))
    }

    @Test
    fun `rate limiter enforces at least 251 milliseconds between permits`() {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = FourPerSecondHistoricalRateLimiter(
            intervalNanos = 251_000_000L,
            nowNanos = { now },
            sleepNanos = { nanos -> sleeps += nanos; now += nanos },
        )

        limiter.awaitPermit()
        limiter.awaitPermit()
        limiter.awaitPermit()

        assertEquals(listOf(251_000_000L, 251_000_000L), sleeps)
    }

    @Test
    fun `source failure during a continuation page is exposed in the outcome`() {
        val adapter = object : HistoricalSearchAdapter {
            override val source = HistoricalSearchSource.OPEN_ARCHIEVEN
            val queries = mutableListOf<Int>()

            override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
                queries += query.start
                return if (query.start == 0) {
                    HistoricalSearchPage(
                        source = source,
                        results = (0 until 100).map { index -> historicalResult(source, index) },
                        total = 200,
                        status = HistoricalTechnicalStatus.AVAILABLE,
                    )
                } else {
                    HistoricalSearchPage(
                        source = source,
                        results = emptyList(),
                        total = 200,
                        status = HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE,
                        message = "Vervolgpagina niet beschikbaar.",
                        consumed = 0,
                    )
                }
            }
        }

        val outcome = HistoricalSearchService(listOf(adapter)).search(
            HistoricalSearchQuery(text = "kerk", source = HistoricalSearchSource.OPEN_ARCHIEVEN, start = 100),
        )

        assertTrue(outcome.results.isEmpty())
        assertEquals(listOf(0, 100), adapter.queries)
        assertEquals(HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE, outcome.sources.single().status)
        assertEquals("Vervolgpagina niet beschikbaar.", outcome.sources.single().message)
        assertEquals(0, outcome.total)
        assertEquals(HistoricalSearchState.SOURCE_FAILURE, outcome.state)
    }

    @Test
    fun `continuation failure never returns results at an offset outside the final total`() {
        val failing = object : HistoricalSearchAdapter {
            override val source = HistoricalSearchSource.EUROPEANA

            override fun search(query: HistoricalSearchQuery): HistoricalSearchPage =
                if (query.start == 0) {
                    HistoricalSearchPage(
                        source = source,
                        results = (0 until 100).map { index -> historicalResult(source, index) },
                        total = 200,
                        status = HistoricalTechnicalStatus.AVAILABLE,
                    )
                } else {
                    HistoricalSearchPage(
                        source = source,
                        results = emptyList(),
                        total = 200,
                        status = HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE,
                        consumed = 0,
                    )
                }
        }
        val available = recordingAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN)

        val outcome = HistoricalSearchService(listOf(failing, available)).search(
            HistoricalSearchQuery(text = "kerk", start = 200, limit = 100),
        )

        assertEquals(100, outcome.start)
        assertEquals(100, outcome.results.size)
        assertEquals((100 until 200).map { "OPEN_ARCHIEVEN-$it" }, outcome.results.map { it.sourceRecordId })
        assertEquals(200, outcome.total)
        assertEquals(HistoricalSearchState.PARTIAL_AVAILABILITY, outcome.state)
        assertEquals(HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE, outcome.sources.first().status)
        assertEquals(HistoricalTechnicalStatus.AVAILABLE, outcome.sources.last().status)
    }

    @Test
    fun `service merges source cursors without duplicating results across pages`() {
        val europeana = recordingAdapter(HistoricalSearchSource.EUROPEANA)
        val open = recordingAdapter(HistoricalSearchSource.OPEN_ARCHIEVEN)
        val service = HistoricalSearchService(listOf(europeana, open))

        val outcome = service.search(HistoricalSearchQuery(text = "kerk", start = 100, limit = 100))

        assertEquals(100, outcome.results.size)
        assertEquals(400, outcome.total)
        assertEquals(HistoricalSearchState.RESULTS, outcome.state)
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

    private fun historicalResult(source: HistoricalSearchSource, index: Int) = HistoricalSearchResult(
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
        retrievedAt = fixedClock().instant(),
    )

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
