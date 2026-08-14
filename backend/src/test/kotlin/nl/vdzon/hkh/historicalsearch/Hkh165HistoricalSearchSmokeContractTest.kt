package nl.vdzon.hkh.historicalsearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.RestClient
import nl.vdzon.hkh.historicalsearch.api.HistoricalSearchController
import tools.jackson.databind.json.JsonMapper

/**
 * One deterministic contract slice for the public Heemskerk search route.
 *
 * The fixture server is local and all values are synthetic. This deliberately exercises the
 * route, rather than only testing an adapter or a page in isolation.
 */
class Hkh165HistoricalSearchSmokeContractTest {
    @Test
    fun `public Heemskerk route maps a valid Open Archieven fixture with provider identity`() {
        val fixture = Hkh165FixtureServer(responder = { Hkh165FixtureResponse.valid() })
        try {
            val mockMvc = routeFor(
                fixture = fixture,
                europeanaKey = "",
            )

            val response = mockMvc.perform(
                get("/api/historical-search")
                    .param("q", "Heemskerk")
                    .param("source", "OPEN_ARCHIEVEN"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.state").value("RESULTS"))
                .andExpect(jsonPath("$.results").isArray)
                .andReturn()

            val body = response.response.contentAsString
            assertVisibleOpenArchievenContract(body)
            assertEquals(1, fixture.requestCount.get())
            assertTrue(fixture.lastRequestPath.get().contains("archive_code=hee"))

            mockMvc.perform(
                get("/api/historical-search")
                    .param("q", "Heemskerk"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.state").value("PARTIAL_AVAILABILITY"))
                .andExpect(jsonPath("$.sources[0].status").value("DISABLED"))
                .andExpect(jsonPath("$.sources[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.results[0].stable_identifier").value("hee:synthetic-1"))

            val sourceStatus = mockMvc.perform(
                get("/api/historical-search")
                    .param("q", "Heemskerk")
                    .param("source", "OPEN_ARCHIEVEN"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.sources[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.sources[0].resultCount").value(1))
                .andExpect(jsonPath("$.results[0].metadataRights").value("ALLOWED"))
                .andExpect(jsonPath("$.results[0].privacyStatus").value("CLEAR"))
                .andReturn()

            assertTrue(sourceStatus.response.contentAsString.contains("\"state\":\"RESULTS\""))
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun `zero result fixture stays available and is not rendered as source failure`() {
        val fixture = Hkh165FixtureServer(responder = { Hkh165FixtureResponse.empty() })
        try {
            val mockMvc = routeFor(fixture, europeanaKey = "")

            mockMvc.perform(
                get("/api/historical-search")
                    .param("q", "Heemskerk")
                    .param("source", "OPEN_ARCHIEVEN"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.state").value("NO_RESULTS"))
                .andExpect(jsonPath("$.results").isEmpty)
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.sources[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.sources[0].resultCount").value(0))
                .andExpect(jsonPath("$.sources[0].heemskerkCount").value(0))
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun `partial and complete source failures retain safe source distinctions`() {
        val partialFixture = Hkh165FixtureServer(responder = { path ->
            if (path.startsWith("/record/v2/")) {
                Hkh165FixtureResponse(status = 503, body = "synthetic failure")
            } else {
                Hkh165FixtureResponse.valid()
            }
        })
        try {
            val partial = routeFor(partialFixture, europeanaKey = "synthetic-europeana-key")
            partial.perform(
                get("/api/historical-search").param("q", "Heemskerk"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.state").value("PARTIAL_AVAILABILITY"))
                .andExpect(jsonPath("$.results[0].stable_identifier").value("hee:synthetic-1"))
                .andExpect(jsonPath("$.sources[0].status").value("TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.sources[0].resultCount").doesNotExist())
                .andExpect(jsonPath("$.sources[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.sources[1].resultCount").value(1))
        } finally {
            partialFixture.stop()
        }

        val failedFixture = Hkh165FixtureServer(responder = {
            Hkh165FixtureResponse(status = 503, body = "synthetic failure")
        })
        try {
            val failed = routeFor(failedFixture, europeanaKey = "synthetic-europeana-key")
            failed.perform(
                get("/api/historical-search").param("q", "Heemskerk"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.state").value("SOURCE_FAILURE"))
                .andExpect(jsonPath("$.results").isEmpty)
                .andExpect(jsonPath("$.sources[0].resultCount").doesNotExist())
                .andExpect(jsonPath("$.sources[1].resultCount").doesNotExist())
        } finally {
            failedFixture.stop()
        }
    }

    @Test
    fun `missing provider fields are diagnosed by result index and contract field`() {
        listOf("source_name", "uuid", "original_source_url").forEach { missingField ->
            val fixture = Hkh165FixtureServer(responder = {
                Hkh165FixtureResponse.bodyWithDocument(missingField)
            })
            try {
                val adapter = openAdapter(fixture)
                val page = adapter.search(HistoricalSearchQuery(text = "Heemskerk"))
                assertEquals(
                    HistoricalTechnicalStatus.MISSING_REQUIRED_FIELDS,
                    page.status,
                    "resultaat[0] mist $missingField",
                )
                assertTrue(page.results.isEmpty(), "resultaat[0] met ontbrekend $missingField mag niet zichtbaar zijn")
            } finally {
                fixture.stop()
            }
        }
    }

    @Test
    fun `missing normalized result status is diagnosed by result index and field`() {
        val normalizedResponseWithoutStatus = """
            {
              "results": [{
                "metadataRights": "ALLOWED",
                "source_name": "Synthetisch Archief",
                "stable_identifier": "hee:synthetic-1",
                "original_source_url": "https://synthetic.example/items/record-1"
              }],
              "sources": [{"status": "AVAILABLE"}]
            }
        """.trimIndent()

        val failure = assertFailsWith<AssertionError> {
            assertVisibleOpenArchievenContract(normalizedResponseWithoutStatus)
        }
        assertTrue(
            failure.message.orEmpty().contains("resultaat[0] mist technicalStatus"),
            "ontbrekende status moet resultaatindex en veldnaam benoemen",
        )
    }

    @Test
    fun `identical concurrent route searches use one upstream attempt and one budget slot`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Hkh165FixtureServer(
            responder = { Hkh165FixtureResponse.valid() },
            entered = entered,
            release = release,
        )
        val budgetCalls = AtomicInteger()
        val executor = Executors.newFixedThreadPool(6)
        try {
            val budget = HistoricalSearchRequestBudget {
                budgetCalls.incrementAndGet()
                true
            }
            val mockMvc = routeFor(fixture, europeanaKey = "", requestBudget = budget)
            val futures = (1..6).map {
                executor.submit<String> {
                    mockMvc.perform(
                        get("/api/historical-search")
                            .param("q", " Heemskerk ")
                            .param("source", "OPEN_ARCHIEVEN"),
                    ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.state").value("RESULTS"))
                        .andExpect(jsonPath("$.results[0].stable_identifier").value("hee:synthetic-1"))
                        .andReturn().response.contentAsString
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS), "lokale Open Archieven-mock ontving geen upstream-aanvraag")
            release.countDown()
            val responses = futures.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, fixture.requestCount.get())
            assertEquals(1, budgetCalls.get())
            assertEquals(1, responses.distinct().size, "identieke zoekcontexten leveren verschillende route-uitkomsten")
            assertFalse(responses.any { it.contains("RATE_LIMITED") })
        } finally {
            release.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "smoke-taken bleven achter in de executor")
            fixture.stop()
        }
    }

    private fun routeFor(
        fixture: Hkh165FixtureServer,
        europeanaKey: String,
        requestBudget: HistoricalSearchRequestBudget = HistoricalSearchRequestBudget { true },
    ): MockMvc {
        val europeana = EuropeanaSearchAdapter(
            restClient = RestClient.builder().baseUrl(fixture.baseUrl).build(),
            wskey = europeanaKey,
            clock = smokeClock(),
        )
        return MockMvcBuilders.standaloneSetup(
            HistoricalSearchController(
                service = HistoricalSearchService(listOf(europeana, openAdapter(fixture))),
                requestBudget = requestBudget,
            ),
        ).build()
    }

    private fun openAdapter(fixture: Hkh165FixtureServer): OpenArchievenSearchAdapter {
        val requestFactory = JdkClientHttpRequestFactory().apply {
            setReadTimeout(Duration.ofSeconds(5))
        }
        return OpenArchievenSearchAdapter(
            restClient = RestClient.builder()
                .baseUrl(fixture.baseUrl)
                .requestFactory(requestFactory)
                .build(),
            rateLimiter = HistoricalSearchRateLimiter { },
            clock = smokeClock(),
            requestBudget = HistoricalSearchRequestBudget { true },
            retrySleeper = { },
        )
    }

    private fun smokeClock() = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC)

    private fun assertVisibleOpenArchievenContract(body: String) {
        val root = JsonMapper.builder().build().readTree(body)
        val result = assertNotNull(
            root.get("results")?.get(0),
            "resultaat[0] ontbreekt in de genormaliseerde route-respons",
        )
        assertEquals(
            "ALLOWED",
            result.get("metadataRights")?.asString(),
            "resultaat[0] mist toegestane metadatarechten",
        )
        assertEquals(
            "Synthetisch Archief",
            result.get("source_name")?.asString()?.takeIf(String::isNotBlank),
            "resultaat[0] mist source_name",
        )
        assertEquals(
            "hee:synthetic-1",
            result.get("stable_identifier")?.asString()?.takeIf(String::isNotBlank),
            "resultaat[0] mist stable_identifier in hee:uuid-vorm",
        )
        assertEquals(
            "https://synthetic.example/items/record-1",
            result.get("original_source_url")?.asString(),
            "resultaat[0] mist de brongeleverde original_source_url",
        )
        assertEquals(
            "AVAILABLE",
            result.get("technicalStatus")?.asString()?.takeIf(String::isNotBlank),
            "resultaat[0] mist technicalStatus (beschikbare status)",
        )
        assertEquals(
            "AVAILABLE",
            root.get("sources")?.get(0)?.get("status")?.asString(),
            "resultaat[0] mist een beschikbare bronstatus",
        )
    }
}

private data class Hkh165FixtureResponse(
    val status: Int,
    val body: String,
) {
    companion object {
        fun valid() = Hkh165FixtureResponse(
            status = 200,
            body = """
                {"response":{"number_found":1,"docs":[
                  {"source_name":"Synthetisch Archief","uuid":"synthetic-1",
                   "original_source_url":"https://synthetic.example/items/record-1",
                   "title":"Synthetisch Heemskerk-resultaat","description":"Minimale testfixture",
                   "eventplace":"Heemskerk","metadataRights":"ALLOWED","objectRights":"RESTRICTED",
                   "privacyStatus":"CLEAR","eventdate":{"year":"1900"}}
                ]}}
            """.trimIndent(),
        )

        fun empty() = Hkh165FixtureResponse(
            status = 200,
            body = """{"response":{"number_found":0,"docs":[]}}""",
        )

        fun bodyWithDocument(missingField: String) = valid().copy(
            body = valid().body.replace(
                Regex("\\\"$missingField\\\":\\\"[^\\\"]*\\\",?"),
                "",
            ),
        )
    }
}

private class Hkh165FixtureServer(
    responder: (String) -> Hkh165FixtureResponse,
    private val entered: CountDownLatch? = null,
    private val release: CountDownLatch? = null,
) {
    private val response = responder
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requestCount = AtomicInteger()
    val lastRequestPath = AtomicReference("")
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            lastRequestPath.set(exchange.requestURI.toString())
            entered?.countDown()
            release?.await(5, TimeUnit.SECONDS)
            respond(exchange, response(exchange.requestURI.path))
        }
        server.start()
    }

    private fun respond(exchange: HttpExchange, fixture: Hkh165FixtureResponse) {
        val bytes = fixture.body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(fixture.status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    fun stop() = server.stop(0)
}
