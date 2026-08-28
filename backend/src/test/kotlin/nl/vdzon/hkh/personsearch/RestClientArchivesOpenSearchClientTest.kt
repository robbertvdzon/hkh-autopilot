package nl.vdzon.hkh.personsearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Fixture/mock Open Archieven-endpoint (embedded JDK [HttpServer]), naar het patroon van
 * `RestClientArchivesNlClientTest`. Dekt de exacte queryparameters, gzip, User-Agent,
 * fail-closed validatie (status, JSON, verplichte velden, `error_code`) en dedup-ready
 * resultaten.
 */
class RestClientArchivesOpenSearchClientTest {

    private var server: HttpServer? = null
    private var lastQuery: Map<String, String> = emptyMap()
    private var lastPath: String? = null
    private var lastAcceptEncoding: String? = null
    private var lastUserAgent: String? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `search sends the exact required query parameters and a descriptive user agent`() {
        val client = startClient { exchange ->
            respondJson(exchange, 200, """{"response": {"number_found": 1, "docs": [{"archive_code":"nha","identifier":"ABC"}]}}""")
        }

        client.search("Nicolaas Jacobus Sinnige", start = 0, numberShow = 100)

        assertEquals("/records/search.json", lastPath)
        assertEquals("nha", lastQuery["archive_code"])
        assertEquals("Heemskerk", lastQuery["eventplace"])
        assertEquals("nl", lastQuery["lang"])
        assertEquals("100", lastQuery["number_show"])
        assertEquals("0", lastQuery["start"])
        assertEquals("Nicolaas Jacobus Sinnige", lastQuery["name"])
        assertEquals("gzip", lastAcceptEncoding)
        assertTrue(lastUserAgent.orEmpty().contains("hkh-autopilot-personsearch"))
    }

    @Test
    fun `search decodes a gzip encoded response transparently`() {
        val client = startClient { exchange ->
            respondJson(exchange, 200, """{"response": {"number_found": 0, "docs": []}}""", gzip = true)
        }

        val result = client.search("Onbekend", start = 0, numberShow = 100) as ArchivesSearchOutcome.Success

        assertEquals(0, result.numberFound)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `a non 2xx search response is a failed source consultation`() {
        val client = startClient { exchange -> respondJson(exchange, 500, """{"error":"boom"}""") }

        val result = client.search("Naam", start = 0, numberShow = 100)

        assertEquals(ArchivesSearchOutcome.Failure, result)
    }

    @Test
    fun `a filled error_code at http 200 is a failed source consultation`() {
        val client = startClient { exchange ->
            respondJson(exchange, 200, """{"response": {"number_found": 1, "docs": []}, "error_code": 1}""")
        }

        val result = client.search("Naam", start = 0, numberShow = 100)

        assertEquals(ArchivesSearchOutcome.Failure, result)
    }

    @Test
    fun `missing required search fields are a failed source consultation`() {
        val client = startClient { exchange -> respondJson(exchange, 200, """{"response": {"docs": []}}""") }

        val result = client.search("Naam", start = 0, numberShow = 100)

        assertEquals(ArchivesSearchOutcome.Failure, result)
    }

    @Test
    fun `invalid json is a failed source consultation`() {
        val client = startClient { exchange -> respondJson(exchange, 200, "not json") }

        val result = client.search("Naam", start = 0, numberShow = 100)

        assertEquals(ArchivesSearchOutcome.Failure, result)
    }

    @Test
    fun `show sends archive identifier and language and validates required fields`() {
        val client = startClient { exchange ->
            respondJson(
                exchange,
                200,
                """
                {
                  "Person": [
                    {"@pid": "Person1", "PersonName": {"PersonNameFirstName": "Nicolaas Jacobus", "PersonNameLastName": "Sinnige"}},
                    {"@pid": "Person2", "PersonName": {"PersonNameFirstName": "Pieter", "PersonNameLastName": "Sinnige"}},
                    {"@pid": "Person3", "PersonName": {"PersonNameFirstName": "Anna Geertruida", "PersonNameLastName": "Eenhuis"}}
                  ],
                  "Event": {"@eid": "Event1", "EventType": "Geboorte", "EventDate": {"Year": "1878", "Month": "07", "Day": "25"}, "EventPlace": {"Place": "Heemskerk"}},
                  "RelationEP": [
                    {"PersonKeyRef": "Person1", "EventKeyRef": "Event1", "RelationType": "Kind"},
                    {"PersonKeyRef": "Person2", "EventKeyRef": "Event1", "RelationType": "Vader"},
                    {"PersonKeyRef": "Person3", "EventKeyRef": "Event1", "RelationType": "Moeder"}
                  ],
                  "Source": {
                    "SourceType": "Geboorteakte",
                    "SourceReference": {"InstitutionName": "Noord-Hollands Archief", "Archive": "123", "RegistryNumber": "4", "DocumentNumber": "56"},
                    "RecordIdentifier": "789"
                  }
                }
                """.trimIndent(),
            )
        }

        val result = client.show("nha", "002ED0F3-F08C-4223-A5EA-BA385D04336E") as ArchivesShowOutcome.Success

        assertEquals("/records/show.json", lastPath)
        assertEquals("nha", lastQuery["archive"])
        assertEquals("002ED0F3-F08C-4223-A5EA-BA385D04336E", lastQuery["identifier"])
        assertEquals("nl", lastQuery["lang"])
        assertEquals("Nicolaas Jacobus Sinnige", result.record.personName)
        assertEquals("1878-07-25", result.record.eventDate)
        assertEquals(listOf(ArchivesRelation("Vader", "Pieter Sinnige"), ArchivesRelation("Moeder", "Anna Geertruida Eenhuis")), result.record.relations)
    }

    @Test
    fun `show without a source record number is a failed source consultation`() {
        val client = startClient { exchange ->
            respondJson(
                exchange,
                200,
                """
                {
                  "Person": [{"@pid": "Person1", "PersonName": {"PersonNameFirstName": "X", "PersonNameLastName": "Y"}}],
                  "Event": {"@eid": "Event1", "EventType": "Geboorte", "EventDate": {"Year": "1900", "Month": "01", "Day": "01"}, "EventPlace": {"Place": "Heemskerk"}},
                  "RelationEP": [{"PersonKeyRef": "Person1", "EventKeyRef": "Event1", "RelationType": "Kind"}],
                  "Source": {"SourceType": "Akte", "SourceReference": {"InstitutionName": "NHA"}}
                }
                """.trimIndent(),
            )
        }

        val result = client.show("nha", "missing-record-number")

        assertEquals(ArchivesShowOutcome.Failure, result)
    }

    @Test
    fun `retries a transient server error a bounded number of times before failing`() {
        var attempts = 0
        val client = startClient(maxAttempts = 2, retryBaseDelayMillis = 1) { exchange ->
            attempts++
            respondJson(exchange, 503, """{"error":"unavailable"}""")
        }

        val result = client.search("Naam", start = 0, numberShow = 100)

        assertEquals(ArchivesSearchOutcome.Failure, result)
        assertEquals(2, attempts)
    }

    private fun startClient(
        maxAttempts: Int = 3,
        retryBaseDelayMillis: Long = 1,
        handler: (HttpExchange) -> Unit,
    ): ArchivesOpenSearchClient {
        val newServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        newServer.createContext("/") { exchange ->
            lastPath = exchange.requestURI.path
            lastAcceptEncoding = exchange.requestHeaders.getFirst("Accept-Encoding")
            lastUserAgent = exchange.requestHeaders.getFirst("User-Agent")
            lastQuery = parseQuery(exchange.requestURI.rawQuery)
            handler(exchange)
        }
        newServer.start()
        server = newServer
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(2000)
            setReadTimeout(2000)
        }
        val restClient = RestClient.builder()
            .baseUrl("http://localhost:${newServer.address.port}")
            .requestFactory(requestFactory)
            .requestInterceptor(GzipRequestInterceptor("hkh-autopilot-personsearch/1.0 (+test)"))
            .build()
        return RestClientArchivesOpenSearchClient(
            restClient,
            PersonSearchRateLimiter(maxPerSecond = 100, sleep = {}),
            maxAttempts = maxAttempts,
            retryBaseDelayMillis = retryBaseDelayMillis,
            sleep = {},
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").associate { pair ->
            val (key, value) = pair.split("=", limit = 2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: String, gzip: Boolean = false) {
        exchange.responseHeaders.set("Content-Type", "application/json")
        val bytes = if (gzip) {
            exchange.responseHeaders.set("Content-Encoding", "gzip")
            val buffer = ByteArrayOutputStream()
            GZIPOutputStream(buffer).use { it.write(body.toByteArray(Charsets.UTF_8)) }
            buffer.toByteArray()
        } else {
            body.toByteArray(Charsets.UTF_8)
        }
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
