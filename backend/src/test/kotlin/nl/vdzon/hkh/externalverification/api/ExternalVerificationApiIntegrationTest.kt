package nl.vdzon.hkh.externalverification.api

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import nl.vdzon.hkh.externalverification.ExternalVerificationOutcome
import nl.vdzon.hkh.externalverification.ExternalVerificationPublishBlockedException
import nl.vdzon.hkh.externalverification.ExternalVerificationPublishGuard
import nl.vdzon.hkh.externalverification.ExternalVerificationRequest
import nl.vdzon.hkh.externalverification.ExternalVerificationService
import nl.vdzon.hkh.externalverification.ExternalVerificationStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Integratietest tegen een fixture/mock-archiefendpoint (embedded [HttpServer]), naar het patroon
 * van [nl.vdzon.hkh.recordintake.api.RecordIntakeApiIntegrationTest]. Dekt AC 1 (geen
 * autorisatietoken nodig), AC 2 (minimaal 2 matching-fixtures), AC 3 (ongeldige guid -> Unverified +
 * publish-guard weigert), AC 4 (uitsluitend minimale verificatievelden opgeslagen) en AC 5 (geen
 * tokenwaarde in logoutput of API-respons).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ExternalVerificationApiIntegrationTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
    @param:Autowired private val service: ExternalVerificationService,
) {

    @Test
    fun `queries the resolvable uri with the ld+json accept header and no authorization token`() {
        mockMvc.post("/api/external-verification") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(localIdentifier = "HKH-2026-0100", adtid = "1000", guid = "verified-jan")
        }.andExpect {
            status { isCreated() }
        }

        assertTrue(mockServer.receivedAcceptHeaders.contains("verified-jan" to "application/ld+json"))
        assertFalse(mockServer.receivedAuthorizationHeaders.containsKey("verified-jan"))
    }

    @Test
    fun `matching fixture one yields a verified status`() {
        mockMvc.post("/api/external-verification") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(localIdentifier = "HKH-2026-0101", adtid = "1000", guid = "verified-jan")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("VERIFIED") }
            jsonPath("$.matchedFields") { isArray() }
        }
    }

    @Test
    fun `matching fixture two - a different person - also yields a verified status`() {
        mockMvc.post("/api/external-verification") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(
                localIdentifier = "HKH-2026-0102",
                name = "Maria de Vries",
                birthDate = "1875-11-20",
                deathDate = "1950-02-14",
                adtid = "2000",
                guid = "verified-maria",
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("VERIFIED") }
        }
    }

    @Test
    fun `a non existent or invalid guid yields unverified and the publish guard refuses publication`() {
        mockMvc.post("/api/external-verification") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(localIdentifier = "HKH-2026-0103", adtid = "9999", guid = "not-a-real-guid")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("UNVERIFIED") }
        }

        val outcome = service.verify(
            ExternalVerificationRequest(
                localIdentifier = "HKH-2026-0104",
                name = "Piet Pietersen",
                birthDate = "1901-01-01",
                deathDate = "1975-01-01",
                adtid = "9999",
                guid = "not-a-real-guid",
            ),
        )
        assertEquals(ExternalVerificationStatus.UNVERIFIED.name, outcome.record.status)
        assertFailsWith<ExternalVerificationPublishBlockedException> {
            ExternalVerificationPublishGuard.assertPublishable(outcome)
        }
    }

    @Test
    fun `only the minimal verification fields are stored - never the full external payload`() {
        mockMvc.post("/api/external-verification") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(localIdentifier = "HKH-2026-0105", adtid = "1000", guid = "verified-jan")
        }.andExpect { status { isCreated() } }

        val columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'external_verification'",
            String::class.java,
        ).toSet()

        assertEquals(
            setOf(
                "id",
                "local_identifier",
                "external_uri",
                "matched_fields",
                "status",
                "checked_at",
                "encrypted_access_token",
            ),
            columns,
        )
    }

    @Test
    fun `an access token never appears in the api response or the application logs`() {
        val logger = LoggerFactory.getLogger(ExternalVerificationService::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)

        try {
            val result = mockMvc.post("/api/external-verification") {
                contentType = MediaType.APPLICATION_JSON
                content = requestJson(
                    localIdentifier = "HKH-2026-0106",
                    adtid = "1000",
                    guid = "verified-jan",
                    accessToken = "top-secret-archive-token",
                )
            }.andExpect { status { isCreated() } }.andReturn()

            val responseBody = result.response.contentAsString
            assertFalse(responseBody.contains("top-secret-archive-token"))

            val logged = appender.list.joinToString("\n") { it.formattedMessage }
            assertFalse(logged.contains("top-secret-archive-token"))
        } finally {
            logger.detachAppender(appender)
        }
    }

    private fun requestJson(
        localIdentifier: String,
        name: String = "Jan Jansen",
        birthDate: String = "1900-01-01",
        deathDate: String = "1980-05-05",
        adtid: String,
        guid: String,
        accessToken: String? = null,
    ): String = """
        {
          "localIdentifier": "$localIdentifier",
          "name": "$name",
          "birthDate": "$birthDate",
          "deathDate": "$deathDate",
          "adtid": "$adtid",
          "guid": "$guid"
          ${accessToken?.let { ""","accessToken": "$it"""" } ?: ""}
        }
    """.trimIndent()

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        private val mockServer = FixtureArchivesNlServer()

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
            registry.add("hkh.externalverification.token-key") {
                java.util.Base64.getEncoder().encodeToString(ByteArray(32))
            }
        }
    }
}

/** Een minimale fixture/mock-implementatie van het archieven.nl JSON-LD-endpoint. */
private class FixtureArchivesNlServer {
    private var server: HttpServer? = null
    val receivedAcceptHeaders = mutableListOf<Pair<String, String>>()
    val receivedAuthorizationHeaders = mutableMapOf<String, String>()

    fun start() {
        val newServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        newServer.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val guid = path.substringAfterLast("/")
            exchange.requestHeaders.getFirst("Accept")?.let { receivedAcceptHeaders += guid to it }
            exchange.requestHeaders.getFirst("Authorization")?.let { receivedAuthorizationHeaders[guid] = it }

            val (status, body) = when {
                guid == "verified-jan" -> 200 to
                    """{"name": "Jan Jansen", "birthDate": "1900-01-01", "deathDate": "1980-05-05"}"""
                guid == "verified-maria" -> 200 to
                    """{"name": "Maria de Vries", "birthDate": "1875-11-20", "deathDate": "1950-02-14"}"""
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
