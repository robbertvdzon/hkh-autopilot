package nl.vdzon.hkh.recordintake.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Dekt AC 1 (geldige adtid/guid-URL -> Geverifieerd met kernvelden, tegen een fixture-
 * archiefendpoint) en AC 2 (niet-matchend patroon -> Niet bereikbaar zonder aanroep, onbekende
 * guid -> Geen match) van het niet-persisterende preview-endpoint.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RecordIntakeExternalArchivePreviewIntegrationTest(@param:Autowired private val mockMvc: MockMvc) {

    @Test
    fun `a matching url returns verified with the core fields and the source uri`() {
        mockMvc.post("/api/record-intake/external-archive-preview") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"durableUrl": "http://opendata.archieven.nl/id/1000/verified-jan"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("GEVERIFIEERD") }
            jsonPath("$.name") { value("Jan Jansen") }
            jsonPath("$.birthDate") { value("1900-01-01") }
            jsonPath("$.deathDate") { value("1980-05-05") }
            jsonPath("$.license") { value("CC0") }
            jsonPath("$.sourceUri") { value("http://opendata.archieven.nl/id/1000/verified-jan") }
        }
    }

    @Test
    fun `a non matching guid returns no match without core fields`() {
        mockMvc.post("/api/record-intake/external-archive-preview") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"durableUrl": "http://opendata.archieven.nl/id/1000/does-not-exist"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("GEEN_MATCH") }
            jsonPath("$.name") { doesNotExist() }
        }
    }

    @Test
    fun `a url that does not follow the archieven nl pattern returns unreachable without ever calling the archive`() {
        val callsBefore = mockServer.receivedRequests.get()

        mockMvc.post("/api/record-intake/external-archive-preview") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"durableUrl": "https://noord-hollandsarchief.nl/record/1"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NIET_BEREIKBAAR") }
        }

        assertEquals(callsBefore, mockServer.receivedRequests.get())
    }

    @Test
    fun `a missing durable url returns unreachable`() {
        mockMvc.post("/api/record-intake/external-archive-preview") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NIET_BEREIKBAAR") }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        private val mockServer = RecordIntakePreviewFixtureArchivesNlServer()

        @JvmStatic
        @BeforeAll
        fun startMockServer() {
            mockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMockServer() {
            mockServer.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun archivesBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("hkh.externalverification.archives-base-url") { mockServer.baseUrl() }
        }
    }
}

private class RecordIntakePreviewFixtureArchivesNlServer {
    private var server: HttpServer? = null
    val receivedRequests = AtomicInteger(0)

    fun start() {
        val newServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        newServer.createContext("/") { exchange ->
            receivedRequests.incrementAndGet()
            val guid = exchange.requestURI.path.substringAfterLast("/")
            val (status, body) = when (guid) {
                "verified-jan" -> 200 to
                    """{"name": "Jan Jansen", "birthDate": "1900-01-01", "deathDate": "1980-05-05", "license": "CC0"}"""
                else -> 404 to """{"error": "not found"}"""
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/ld+json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        newServer.start()
        server = newServer
    }

    fun stop() {
        server?.stop(0)
    }

    fun baseUrl(): String = "http://localhost:${server!!.address.port}"
}
