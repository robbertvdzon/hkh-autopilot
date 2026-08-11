package nl.vdzon.hkh.recordintake.api

import kotlin.test.assertEquals
import nl.vdzon.hkh.recordintake.RecordIntakeTokenIdentity
import nl.vdzon.hkh.recordintake.RecordIntakeTokenVerifier
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Dekt de publieke `GET /api/records/{localIdentifier}`-route en de admin-only
 * bevestigingsactie end-to-end, inclusief het zelfherstellende gedrag: eenzelfde
 * `localIdentifier` levert CONFIRMED op na bevestiging, en degradeert automatisch naar de
 * neutrale status zodra een latere wijziging van `deceasedStatus` de live herclassificatie naar
 * Blocked verandert - zonder dat `confirmed_by`/`confirmed_at` in de database gewist worden.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "hkh.auth.google-client-id=test-client",
        "hkh.auth.admin-allowed-emails=admin@example.com",
    ],
)
@Import(RecordPublicApiIntegrationTest.TestConfig::class)
class RecordPublicApiIntegrationTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `a local identifier with no intake at all yields NO_INTAKE with HTTP 200`() {
        mockMvc.get("/api/records/HKH-2026-UNKNOWN").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NO_INTAKE") }
            jsonPath("$.name") { doesNotExist() }
            jsonPath("$.sourceUri") { doesNotExist() }
        }
    }

    @Test
    fun `a record saved without external source data yields SAVED_WITHOUT_SOURCE`() {
        createIntake(localIdentifier = "HKH-2026-0101", deceasedStatus = "overleden", confirmExternalArchiveData = false)

        mockMvc.get("/api/records/HKH-2026-0101").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SAVED_WITHOUT_SOURCE") }
            jsonPath("$.name") { doesNotExist() }
            jsonPath("$.sourceUri") { doesNotExist() }
        }
    }

    @Test
    fun `archive data present but not yet confirmed by an administrator still yields SAVED_WITHOUT_SOURCE`() {
        createIntake(localIdentifier = "HKH-2026-0102", deceasedStatus = "overleden", confirmExternalArchiveData = true)

        mockMvc.get("/api/records/HKH-2026-0102").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SAVED_WITHOUT_SOURCE") }
        }
    }

    @Test
    fun `the admin confirmation action requires admin authentication`() {
        createIntake(localIdentifier = "HKH-2026-0103", deceasedStatus = "overleden", confirmExternalArchiveData = true)

        mockMvc.post("/api/admin/record-intake/HKH-2026-0103/confirm").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a confirmed processable record exposes year only dates, license, source link and confirmation text fields`() {
        createIntake(localIdentifier = "HKH-2026-0104", deceasedStatus = "overleden", confirmExternalArchiveData = true)

        confirmAsAdmin("HKH-2026-0104").andExpect {
            status { isOk() }
            jsonPath("$.confirmedBy") { value("admin@example.com") }
        }

        mockMvc.get("/api/records/HKH-2026-0104").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
            jsonPath("$.name") { value("Jan Jansen") }
            jsonPath("$.birthYear") { value("1900") }
            jsonPath("$.deathYear") { value("1980") }
            jsonPath("$.license") { value("CC0") }
            jsonPath("$.sourceUri") { value("http://opendata.archieven.nl/id/1000/verified-jan-deceased") }
            jsonPath("$.confirmedAt") { exists() }
        }
    }

    @Test
    fun `self healing - a reclassification to Blocked degrades to neutral without wiping the confirmation, and heals back`() {
        createIntake(localIdentifier = "HKH-2026-0105", deceasedStatus = "overleden", confirmExternalArchiveData = true)
        confirmAsAdmin("HKH-2026-0105").andExpect { status { isOk() } }

        mockMvc.get("/api/records/HKH-2026-0105").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
        }

        jdbcTemplate.update(
            "UPDATE record_intake SET deceased_status = 'levend' WHERE local_identifier = ?",
            "HKH-2026-0105",
        )

        mockMvc.get("/api/records/HKH-2026-0105").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SAVED_WITHOUT_SOURCE") }
            jsonPath("$.name") { doesNotExist() }
        }

        val confirmation = jdbcTemplate.queryForMap(
            "SELECT confirmed_by, confirmed_at FROM record_intake WHERE local_identifier = ?",
            "HKH-2026-0105",
        )
        assertEquals("admin@example.com", confirmation["confirmed_by"])
        assertEquals(false, confirmation["confirmed_at"] == null)

        jdbcTemplate.update(
            "UPDATE record_intake SET deceased_status = 'overleden' WHERE local_identifier = ?",
            "HKH-2026-0105",
        )

        mockMvc.get("/api/records/HKH-2026-0105").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
            jsonPath("$.name") { value("Jan Jansen") }
        }
    }

    @Test
    fun `confirming an unknown local identifier fails with 404`() {
        confirmAsAdmin("HKH-2026-DOES-NOT-EXIST").andExpect { status { isNotFound() } }
    }

    private fun confirmAsAdmin(localIdentifier: String) = mockMvc.post("/api/admin/record-intake/$localIdentifier/confirm") {
        header("Authorization", "Bearer valid-token")
    }

    private fun createIntake(localIdentifier: String, deceasedStatus: String, confirmExternalArchiveData: Boolean) {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "localIdentifier": "$localIdentifier",
                  "title": "Testrecord",
                  "dating": "circa 1900",
                  "provenance": "Streekarchief Waterland",
                  "rightsStatus": "publicatie toegestaan",
                  "privacyClassification": "geen persoonsgegevens",
                  "accessUrl": "https://collectie.hkh-autopilot.local/records/$localIdentifier",
                  "deceasedStatus": "$deceasedStatus",
                  "confirmExternalArchiveData": $confirmExternalArchiveData,
                  "externalLink": {
                    "durableUrl": "http://opendata.archieven.nl/id/1000/verified-jan-deceased",
                    "linkRationale": "Zelfde herkomst en datering.",
                    "uncertainty": "laag"
                  }
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        private val mockServer = RecordPublicFixtureArchivesNlServer()

        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun startMockServer() {
            mockServer.start()
        }

        @org.junit.jupiter.api.AfterAll
        @JvmStatic
        fun stopMockServer() {
            mockServer.stop()
        }

        @org.springframework.test.context.DynamicPropertySource
        @JvmStatic
        fun archivesBaseUrl(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("hkh.externalverification.archives-base-url") { mockServer.baseUrl() }
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun testRecordIntakeTokenVerifier(): RecordIntakeTokenVerifier = RecordIntakeTokenVerifier { token ->
            if (token != "valid-token") throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
            )
            RecordIntakeTokenIdentity("collection-manager-1")
        }

        @Bean
        @Primary
        fun testGoogleIdTokenVerifier(): nl.vdzon.hkh.auth.GoogleIdTokenVerifier =
            nl.vdzon.hkh.auth.GoogleIdTokenVerifier { nl.vdzon.hkh.auth.GoogleIdentity("admin@example.com", true) }
    }
}

/** Minimale fixture/mock-implementatie van het archieven.nl JSON-LD-endpoint voor deze testklasse. */
private class RecordPublicFixtureArchivesNlServer {
    private var server: com.sun.net.httpserver.HttpServer? = null

    fun start() {
        val newServer = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("localhost", 0), 0)
        newServer.createContext("/") { exchange ->
            val guid = exchange.requestURI.path.substringAfterLast("/")
            val (status, body) = when (guid) {
                "verified-jan-deceased" -> 200 to
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
