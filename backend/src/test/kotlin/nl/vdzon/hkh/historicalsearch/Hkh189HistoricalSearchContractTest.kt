package nl.vdzon.hkh.historicalsearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import nl.vdzon.hkh.historicalsearch.api.HistoricalSearchController

/**
 * Contract matrix for the public historical search route. Every provider response is served by a
 * local fixture and each assertion names the scenario and its expected public contract.
 */
class Hkh189HistoricalSearchContractTest {
    @Test
    fun `public route exposes the complete safe status matrix`() {
        val cases = listOf(
            Hkh189MatrixCase(
                name = "valid Open Archieven response",
                fixture = Hkh189FixtureResponse.valid(),
                expectedState = "RESULTS",
                expectedSourceStatus = "AVAILABLE",
                expectedTotal = 1,
                expectedResultCount = 1,
                cardVisible = true,
            ),
            Hkh189MatrixCase(
                name = "valid zero result",
                fixture = Hkh189FixtureResponse.empty(),
                expectedState = "NO_RESULTS",
                expectedSourceStatus = "AVAILABLE",
                expectedTotal = 0,
                expectedResultCount = 0,
                cardVisible = false,
            ),
            Hkh189MatrixCase(
                name = "invalid JSON",
                fixture = Hkh189FixtureResponse(status = 200, body = "{provider-secret"),
                expectedState = "SOURCE_FAILURE",
                expectedSourceStatus = "INVALID_JSON",
                expectedTotal = 0,
                expectedResultCount = null,
                cardVisible = false,
            ),
            Hkh189MatrixCase(
                name = "missing required fields",
                fixture = Hkh189FixtureResponse(
                    status = 200,
                    body = """{"response":{"number_found":1,"docs":[{}]}}""",
                ),
                expectedState = "SOURCE_FAILURE",
                expectedSourceStatus = "MISSING_REQUIRED_FIELDS",
                expectedTotal = 0,
                expectedResultCount = null,
                cardVisible = false,
            ),
            Hkh189MatrixCase(
                name = "contradictory provider totals",
                fixture = Hkh189FixtureResponse(
                    status = 200,
                    body = Hkh189FixtureResponse.valid().body.replace(
                        "\"number_found\":1",
                        "\"number_found\":0",
                    ),
                ),
                expectedState = "SOURCE_FAILURE",
                expectedSourceStatus = "MISSING_REQUIRED_FIELDS",
                expectedTotal = 0,
                expectedResultCount = null,
                cardVisible = false,
            ),
            Hkh189MatrixCase(
                name = "HTTP 5xx response",
                fixture = Hkh189FixtureResponse(status = 503, body = "provider-secret-5xx"),
                expectedState = "SOURCE_FAILURE",
                expectedSourceStatus = "HTTP_ERROR",
                expectedTotal = 0,
                expectedResultCount = null,
                cardVisible = false,
            ),
            Hkh189MatrixCase(
                name = "missing rights and privacy metadata",
                fixture = Hkh189FixtureResponse.unknownMetadata(),
                expectedState = "RESULTS",
                expectedSourceStatus = "AVAILABLE",
                expectedTotal = 1,
                expectedResultCount = 1,
                cardVisible = true,
            ),
        )

        cases.forEach { matrixCase ->
            val fixture = Hkh189FixtureServer { matrixCase.fixture }
            try {
                val response = routeFor(
                    fixture = fixture,
                    timeout = Duration.ofSeconds(2),
                ).perform(
                    get("/api/historical-search")
                        .param("q", "Heemskerk")
                        .param("source", "OPEN_ARCHIEVEN"),
                ).andExpect(status().isOk)
                    .andReturn()
                    .response
                val root = JsonMapper.builder().build().readTree(response.contentAsString)
                val source = assertNotNull(root.get("sources")?.get(0), diagnostic(matrixCase))
                val results = assertNotNull(root.get("results"), diagnostic(matrixCase))

                assertEquals(matrixCase.expectedState, root.get("state")?.asString(), diagnostic(matrixCase))
                assertEquals(matrixCase.expectedSourceStatus, source.get("status")?.asString(), diagnostic(matrixCase))
                assertEquals(matrixCase.expectedTotal, root.get("total")?.asInt(), diagnostic(matrixCase))
                if (matrixCase.expectedResultCount == null) {
                    assertNullNode(source.get("resultCount"), diagnostic(matrixCase))
                } else {
                    assertEquals(matrixCase.expectedResultCount, source.get("resultCount")?.asInt(), diagnostic(matrixCase))
                }
                assertEquals(matrixCase.cardVisible, results.size() > 0, diagnostic(matrixCase))
                assertFalse(
                    response.contentAsString.contains("provider-secret"),
                    "${diagnostic(matrixCase)}; providerinhoud mag niet in de publieke respons staan",
                )

                if (matrixCase.name == "valid Open Archieven response") {
                    assertValidProviderIdentity(results.get(0), matrixCase)
                }
                if (matrixCase.name == "missing rights and privacy metadata") {
                    val result = results.get(0)
                    assertEquals("UNKNOWN", result.get("metadataRights")?.asString(), diagnostic(matrixCase))
                    assertEquals("UNKNOWN", result.get("privacyStatus")?.asString(), diagnostic(matrixCase))
                    assertNullNode(result.get("title"), diagnostic(matrixCase))
                }
            } finally {
                fixture.stop()
            }
        }
    }

    @Test
    fun `timeout is a safe source failure with no card or provider content`() {
        val fixture = Hkh189FixtureServer {
            Hkh189FixtureResponse(
                status = 200,
                body = "provider-secret-timeout",
                delayMillis = 500,
            )
        }
        try {
            val response = routeFor(fixture, timeout = Duration.ofMillis(100)).perform(
                get("/api/historical-search")
                    .param("q", "Heemskerk")
                    .param("source", "OPEN_ARCHIEVEN"),
            ).andExpect(status().isOk)
                .andReturn()
                .response
            val root = JsonMapper.builder().build().readTree(response.contentAsString)
            val source = assertNotNull(root.get("sources")?.get(0), timeoutDiagnostic())

            assertEquals("SOURCE_FAILURE", root.get("state")?.asString(), timeoutDiagnostic())
            assertEquals("TIMEOUT", source.get("status")?.asString(), timeoutDiagnostic())
            assertEquals(0, root.get("total")?.asInt(), timeoutDiagnostic())
            assertNullNode(source.get("resultCount"), timeoutDiagnostic())
            assertTrue(root.get("results")?.size() == 0, timeoutDiagnostic())
            assertFalse(response.contentAsString.contains("provider-secret"), timeoutDiagnostic())
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun `partial availability preserves the available source and withholds failed count`() {
        val fixture = Hkh189FixtureServer { path ->
            if (path.startsWith("/record/v2/")) {
                Hkh189FixtureResponse.europeanaValid()
            } else {
                Hkh189FixtureResponse(status = 503, body = "provider-secret-partial")
            }
        }
        try {
            val response = routeForBoth(fixture).perform(
                get("/api/historical-search").param("q", "Heemskerk"),
            ).andExpect(status().isOk)
                .andReturn()
                .response
            val root = JsonMapper.builder().build().readTree(response.contentAsString)
            val sources = assertNotNull(root.get("sources"), partialDiagnostic())

            assertEquals("PARTIAL_AVAILABILITY", root.get("state")?.asString(), partialDiagnostic())
            assertEquals(1, root.get("total")?.asInt(), partialDiagnostic())
            assertEquals(1, root.get("results")?.size(), partialDiagnostic())
            assertEquals("AVAILABLE", sources.get(0)?.get("status")?.asString(), partialDiagnostic())
            assertEquals(1, sources.get(0)?.get("resultCount")?.asInt(), partialDiagnostic())
            assertEquals("HTTP_ERROR", sources.get(1)?.get("status")?.asString(), partialDiagnostic())
            assertNullNode(sources.get(1)?.get("resultCount"), partialDiagnostic())
            assertTrue(
                response.contentAsString.contains("Bron kon niet worden bevraagd.") ||
                    response.contentAsString.contains("Open Archieven gaf een fout bij het opvragen."),
                partialDiagnostic(),
            )
            assertFalse(response.contentAsString.contains("provider-secret"), partialDiagnostic())
        } finally {
            fixture.stop()
        }
    }

    private fun routeFor(fixture: Hkh189FixtureServer, timeout: Duration): MockMvc {
        val requestFactory = JdkClientHttpRequestFactory().apply { setReadTimeout(timeout) }
        val openArchieven = OpenArchievenSearchAdapter(
            restClient = RestClient.builder()
                .baseUrl(fixture.baseUrl)
                .requestFactory(requestFactory)
                .build(),
            rateLimiter = HistoricalSearchRateLimiter { },
            clock = hkh189Clock(),
            retrySleeper = { },
        )
        return MockMvcBuilders.standaloneSetup(
            HistoricalSearchController(HistoricalSearchService(listOf(openArchieven))),
        ).build()
    }

    private fun routeForBoth(fixture: Hkh189FixtureServer): MockMvc {
        val requestFactory = JdkClientHttpRequestFactory().apply {
            setReadTimeout(Duration.ofSeconds(2))
        }
        val client = RestClient.builder()
            .baseUrl(fixture.baseUrl)
            .requestFactory(requestFactory)
            .build()
        return MockMvcBuilders.standaloneSetup(
            HistoricalSearchController(
                HistoricalSearchService(
                    listOf(
                        EuropeanaSearchAdapter(client, wskey = "synthetic-key", clock = hkh189Clock()),
                        OpenArchievenSearchAdapter(
                            restClient = client,
                            rateLimiter = HistoricalSearchRateLimiter { },
                            clock = hkh189Clock(),
                            retrySleeper = { },
                        ),
                    ),
                ),
            ),
        ).build()
    }

    private fun assertValidProviderIdentity(result: JsonNode, matrixCase: Hkh189MatrixCase) {
        assertEquals("Synthetisch Archief", result.get("source_name")?.asString(), diagnostic(matrixCase))
        assertEquals("hee:synthetic-189", result.get("stable_identifier")?.asString(), diagnostic(matrixCase))
        assertEquals(
            "https://synthetic.example/items/record-189",
            result.get("original_source_url")?.asString(),
            diagnostic(matrixCase),
        )
    }

    private fun assertNullNode(node: JsonNode?, message: String) {
        assertTrue(node == null || node.isNull, message)
    }

    private fun diagnostic(matrixCase: Hkh189MatrixCase): String =
        "${matrixCase.name}: verwacht state=${matrixCase.expectedState}, " +
            "bronstatus=${matrixCase.expectedSourceStatus}, " +
            "totaal=${matrixCase.expectedTotal}, telling=${matrixCase.expectedResultCount}, " +
            "kaartzichtbaar=${matrixCase.cardVisible}"

    private fun timeoutDiagnostic() =
        "timeout: verwacht state=SOURCE_FAILURE, bronstatus=TIMEOUT, totaal=0, telling=null, kaartzichtbaar=false"

    private fun partialDiagnostic() =
        "partial availability: verwacht state=PARTIAL_AVAILABILITY, " +
            "beschikbare bron telling=1, uitgevallen bron telling=null, kaartzichtbaar=true"

    private fun hkh189Clock() = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC)
}

private data class Hkh189MatrixCase(
    val name: String,
    val fixture: Hkh189FixtureResponse,
    val expectedState: String,
    val expectedSourceStatus: String,
    val expectedTotal: Int,
    val expectedResultCount: Int?,
    val cardVisible: Boolean,
)

private data class Hkh189FixtureResponse(
    val status: Int,
    val body: String,
    val delayMillis: Long = 0,
) {
    companion object {
        fun valid() = Hkh189FixtureResponse(
            status = 200,
            body = """
                {"response":{"number_found":1,"docs":[
                  {"source_name":"Synthetisch Archief","uuid":"synthetic-189",
                   "original_source_url":"https://synthetic.example/items/record-189",
                   "title":"Synthetisch Heemskerk-resultaat","metadataRights":"ALLOWED",
                   "privacyStatus":"CLEAR","eventplace":"Heemskerk"}
                ]}}
            """.trimIndent(),
        )

        fun unknownMetadata() = valid().copy(
            body = valid().body
                .replace("Synthetisch Heemskerk-resultaat", "provider-secret-title")
                .replace("\"metadataRights\":\"ALLOWED\",", "")
                .replace("\"privacyStatus\":\"CLEAR\",", ""),
        )

        fun empty() = Hkh189FixtureResponse(
            status = 200,
            body = """{"response":{"number_found":0,"docs":[]}}""",
        )

        fun europeanaValid() = Hkh189FixtureResponse(
            status = 200,
            body = """
                {"totalResults":1,"items":[
                  {"id":"europeana-189","guid":"https://synthetic.example/europeana-189",
                   "title":"Veilige deeluitkomst","metadataRights":"ALLOWED","privacyStatus":"CLEAR"}
                ]}
            """.trimIndent(),
        )
    }
}

private class Hkh189FixtureServer(
    responder: (String) -> Hkh189FixtureResponse,
) {
    private val response = responder
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            val fixture = response(exchange.requestURI.path)
            if (fixture.delayMillis > 0) Thread.sleep(fixture.delayMillis)
            val bytes = fixture.body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(fixture.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    fun stop() = server.stop(0)
}
