package nl.vdzon.hkh.externalverification

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/** Fixture/mock-archiefendpoint dat geen autorisatietoken vereist, naar het patroon van AC 1. */
class RestClientArchivesNlClientTest {

    private var server: HttpServer? = null
    private var receivedAcceptHeader: String? = null
    private var receivedAuthorizationHeader: String? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `fetches the resolvable uri with the ld+json accept header and no authorization token`() {
        val client = startServerAndClient { exchange ->
            receivedAcceptHeader = exchange.requestHeaders.getFirst("Accept")
            receivedAuthorizationHeader = exchange.requestHeaders.getFirst("Authorization")
            respondJson(
                exchange,
                200,
                """{"name": "Jan Jansen", "birthDate": "1900-01-01", "deathDate": "1980-05-05"}""",
            )
        }

        val result = client.fetch("1234", "abcd-ef01", accessToken = null)

        assertEquals("application/ld+json", receivedAcceptHeader)
        assertNull(receivedAuthorizationHeader)
        assertEquals(ArchiveFetchResult.Found(ArchiveRecordFields("Jan Jansen", "1900-01-01", "1980-05-05")), result)
    }

    @Test
    fun `a license field in the ld plus json response is read as the record license`() {
        val client = startServerAndClient { exchange ->
            respondJson(
                exchange,
                200,
                """{"name": "Jan Jansen", "birthDate": "1900-01-01", "deathDate": "1980-05-05", "license": "CC0"}""",
            )
        }

        val result = client.fetch("1234", "abcd-ef01", accessToken = null)

        assertEquals(
            ArchiveFetchResult.Found(ArchiveRecordFields("Jan Jansen", "1900-01-01", "1980-05-05", "CC0")),
            result,
        )
    }

    @Test
    fun `a missing license field yields no license value`() {
        val client = startServerAndClient { exchange ->
            respondJson(
                exchange,
                200,
                """{"name": "Piet Pietersen", "birthDate": "1901-01-01", "deathDate": "1975-01-01"}""",
            )
        }

        val result = client.fetch("0000", "no-license", accessToken = null) as ArchiveFetchResult.Found

        assertNull(result.fields.license)
    }

    @Test
    fun `an unknown or invalid guid yields not found`() {
        val client = startServerAndClient { exchange -> respondJson(exchange, 404, """{"error": "not found"}""") }

        val result = client.fetch("0000", "not-a-real-guid", accessToken = null)

        assertEquals(ArchiveFetchResult.NotFound, result)
    }

    @Test
    fun `an endpoint that requires authentication is reported distinctly`() {
        val client = startServerAndClient { exchange -> respondJson(exchange, 401, """{"error": "unauthorized"}""") }

        val result = client.fetch("1234", "abcd-ef01", accessToken = null)

        assertEquals(ArchiveFetchResult.AuthenticationRequired, result)
    }

    @Test
    fun `an access token is only sent when explicitly provided`() {
        val client = startServerAndClient { exchange ->
            receivedAuthorizationHeader = exchange.requestHeaders.getFirst("Authorization")
            respondJson(exchange, 200, """{"name": "Jan Jansen"}""")
        }

        client.fetch("1234", "abcd-ef01", accessToken = "supplied-token")

        assertEquals("Bearer supplied-token", receivedAuthorizationHeader)
    }

    private fun startServerAndClient(handler: (HttpExchange) -> Unit): ArchivesNlClient {
        val newServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        newServer.createContext("/") { exchange -> handler(exchange) }
        newServer.start()
        server = newServer
        val restClient = RestClient.builder().baseUrl("http://localhost:${newServer.address.port}").build()
        return RestClientArchivesNlClient(restClient)
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/ld+json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
