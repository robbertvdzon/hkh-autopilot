package nl.vdzon.hkh.topicsearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Fixture/mock Europeana-endpoint (embedded JDK [HttpServer]), naar het patroon van
 * `RestClientArchivesOpenSearchClientTest`. Dekt de exacte queryparameters, fail-closed validatie
 * (status, JSON) en recordvalidatie (title/beschrijving, dataProvider, bronverwijzing).
 */
class RestClientArchivesEuropeanaClientTest {

    private var server: HttpServer? = null
    private var lastPath: String? = null
    private var lastQuery: Map<String, String> = emptyMap()

    @AfterTest
    fun tearDown() {
        server?.stop(0)
    }

    private fun startServer(handler: (HttpExchange) -> Unit): RestClient {
        val httpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        httpServer.createContext("/") { exchange ->
            lastPath = exchange.requestURI.path
            lastQuery = (exchange.requestURI.rawQuery ?: "").split("&").filter { it.isNotBlank() }.associate {
                val (key, value) = it.split("=", limit = 2)
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        httpServer.start()
        server = httpServer
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(1000)
            setReadTimeout(3000)
        }
        return RestClient.builder()
            .baseUrl("http://localhost:${httpServer.address.port}")
            .requestFactory(requestFactory)
            .build()
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun `search sends the exact required query parameters and the api key`() {
        val restClient = startServer { exchange -> respondJson(exchange, 200, """{"items": []}""") }
        val client = RestClientArchivesEuropeanaClient(restClient, "own-project-key")

        client.search("watersnood van 1916 AND Heemskerk")

        assertEquals("/record/v2/search.json", lastPath)
        assertEquals("watersnood van 1916 AND Heemskerk", lastQuery["query"])
        assertEquals("8", lastQuery["rows"])
        assertEquals("rich", lastQuery["profile"])
        assertEquals("own-project-key", lastQuery["wskey"])
    }

    @Test
    fun `a blank api key never calls europeana and is a failure`() {
        var called = false
        val restClient = startServer { exchange -> called = true; respondJson(exchange, 200, """{"items": []}""") }
        val client = RestClientArchivesEuropeanaClient(restClient, "")

        val outcome = client.search("watersnood van 1916 AND Heemskerk")

        assertEquals(EuropeanaSearchOutcome.Failure, outcome)
        assertTrue(!called)
    }

    @Test
    fun `only items with title, dataProvider and a source reference count as valid records`() {
        val restClient = startServer { exchange ->
            respondJson(
                exchange,
                200,
                """
                {"items": [
                    {"title": ["Watersnood van 1916"], "dataProvider": ["Noord-Hollands Archief"],
                     "edmIsShownAt": ["https://archief.example/1"], "rights": ["http://creativecommons.org/publicdomain/mark/1.0/"]},
                    {"title": ["Zonder dataProvider"], "edmIsShownAt": ["https://archief.example/2"]},
                    {"dataProvider": ["Noord-Hollands Archief"], "guid": "https://www.europeana.eu/item/3"}
                ]}
                """.trimIndent(),
            )
        }
        val client = RestClientArchivesEuropeanaClient(restClient, "own-project-key")

        val outcome = client.search("watersnood van 1916 AND Heemskerk") as EuropeanaSearchOutcome.Success

        assertEquals(1, outcome.validRecords.size)
        assertEquals("Watersnood van 1916", outcome.validRecords.first().title)
        assertEquals("Publiek domein", outcome.validRecords.first().license.text)
    }

    @Test
    fun `a non-2xx status is a failure`() {
        val restClient = startServer { exchange -> respondJson(exchange, 500, """{"error": "boom"}""") }
        val client = RestClientArchivesEuropeanaClient(restClient, "own-project-key")

        val outcome = client.search("watersnood van 1916 AND Heemskerk")

        assertEquals(EuropeanaSearchOutcome.Failure, outcome)
    }

    @Test
    fun `invalid json is a failure`() {
        val restClient = startServer { exchange -> respondJson(exchange, 200, "not json") }
        val client = RestClientArchivesEuropeanaClient(restClient, "own-project-key")

        val outcome = client.search("watersnood van 1916 AND Heemskerk")

        assertEquals(EuropeanaSearchOutcome.Failure, outcome)
    }
}
