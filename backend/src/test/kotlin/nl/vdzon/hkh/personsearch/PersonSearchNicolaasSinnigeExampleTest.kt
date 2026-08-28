package nl.vdzon.hkh.personsearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.vdzon.hkh.personsearch.api.PersonSearchApiRequest
import nl.vdzon.hkh.personsearch.api.PersonSearchApiResponse
import nl.vdzon.hkh.personsearch.api.PersonSearchController
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.client.RestClient

/**
 * Het letterlijke, gecontroleerde voorbeeld uit de story: 'Wie was Nicolaas Jacobus Sinnige,
 * geboren in Heemskerk in 1878?'. End-to-end door de echte `RestClientArchivesOpenSearchClient`
 * (tegen een JDK `HttpServer`-fixture) en de echte `PersonSearchController`, zonder Spring-context
 * of database - deze module heeft er geen nodig.
 */
class PersonSearchNicolaasSinnigeExampleTest {

    private var server: HttpServer? = null
    private var searchRequests = 0
    private var showRequests = 0

    @AfterTest
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `search yields exactly one nha match and show yields the birth with father and mother`() {
        val newServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        newServer.createContext("/records/search.json") { exchange ->
            searchRequests++
            respondJson(
                exchange,
                200,
                """{"number_found": 1, "results": [{"archive_code": "nha", "identifier": "002ED0F3-F08C-4223-A5EA-BA385D04336E"}]}""",
            )
        }
        newServer.createContext("/records/show.json") { exchange ->
            showRequests++
            val query = parseQuery(exchange.requestURI.rawQuery)
            assertEquals("nha", query["archive"])
            assertEquals("002ED0F3-F08C-4223-A5EA-BA385D04336E", query["identifier"])
            respondJson(
                exchange,
                200,
                """
                {
                  "person": {"name": "Nicolaas Jacobus Sinnige"},
                  "event": {"type": "Geboorte", "date": "1878-07-25", "place": "Heemskerk"},
                  "relationEP": [
                    {"role": "Vader", "person": "Pieter Sinnige"},
                    {"role": "Moeder", "person": "Anna Geertruida Eenhuis"}
                  ],
                  "source": {
                    "institution": "Noord-Hollands Archief",
                    "source_type": "Geboorteakte",
                    "archive_number": "123",
                    "register_number": "4",
                    "deed_number": "56",
                    "record_number": "789"
                  }
                }
                """.trimIndent(),
            )
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
        val archivesClient = RestClientArchivesOpenSearchClient(restClient, PersonSearchRateLimiter(maxPerSecond = 100, sleep = {}))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val service = PersonSearchService(
                archivesClient = archivesClient,
                contextSource = PersonSearchContextSource { null },
                answerBuilder = PersonSearchAnswerBuilder(),
                jobStore = PersonSearchJobStore(),
                executor = executor,
                clock = Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC),
            )
            val controller = PersonSearchController(PersonSearchSessionResolver(), service)

            val response = controller.submit(
                PersonSearchApiRequest(
                    recognizedName = "Nicolaas Jacobus Sinnige",
                    yearOrPeriod = "1878",
                    originalQuery = "Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?",
                ),
                MockHttpServletRequest(),
                MockHttpServletResponse(),
            )

            val body = response.body as PersonSearchApiResponse
            assertEquals("SUPPORTED_ANSWER", body.status)
            assertEquals(1, searchRequests)
            assertEquals(1, showRequests)

            val answer = body.answer!!
            assertTrue(answer.sentences.any { it.text.contains("Nicolaas Jacobus Sinnige is geboren op 25 juli 1878 in Heemskerk") })
            assertTrue(answer.sentences.any { it.text == "Pieter Sinnige was de vader van Nicolaas Jacobus Sinnige." })
            assertTrue(answer.sentences.any { it.text == "Anna Geertruida Eenhuis was de moeder van Nicolaas Jacobus Sinnige." })
            assertTrue(answer.disclaimer.contains("geen volledig levensverhaal"))
            assertTrue(answer.disclaimer.contains("geen overzicht van alle gebeurtenissen in Heemskerk in 1878"))

            val source = answer.sources.single()
            assertEquals("nha", source.archiveCode)
            assertEquals("002ED0F3-F08C-4223-A5EA-BA385D04336E", source.identifier)
            assertEquals(
                "https://www.openarchieven.nl/nha:002ED0F3-F08C-4223-A5EA-BA385D04336E",
                source.openArchivesLink,
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").associate { pair ->
            val (key, value) = pair.split("=", limit = 2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
