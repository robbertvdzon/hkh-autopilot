package nl.vdzon.hkh.recordintake.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import nl.vdzon.hkh.recordintake.RecordIntakeTokenIdentity
import nl.vdzon.hkh.recordintake.RecordIntakeTokenVerifier
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(RecordIntakeApiIntegrationTest.TokenTestConfiguration::class)
class RecordIntakeApiIntegrationTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `rejects requests without a valid bearer token before touching validation`() {
        mockMvc.post("/api/record-intake") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a complete valid submission stores exactly one intern concept record with metadata only response`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "localIdentifier": "HKH-2026-0001",
                  "title": "Poldermolen De Eendracht",
                  "dating": "circa 1890",
                  "provenance": "Streekarchief Waterland",
                  "rightsStatus": "publicatie toegestaan",
                  "privacyClassification": "geen persoonsgegevens",
                  "accessUrl": "https://collectie.hkh-autopilot.local/records/hkh-2026-0001"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("intern_concept") }
            jsonPath("$.id") { exists() }
            jsonPath("$.externalLink") { doesNotExist() }
        }
    }

    @Test
    fun `a submission with a fully valid external link also creates the concept link`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "localIdentifier": "HKH-2026-0002",
                  "title": "Sluiswachterswoning",
                  "dating": "1920-1930",
                  "provenance": "Gemeentearchief",
                  "rightsStatus": "publicatie toegestaan",
                  "privacyClassification": "geen persoonsgegevens",
                  "accessUrl": "https://collectie.hkh-autopilot.local/records/hkh-2026-0002",
                  "externalLink": {
                    "durableUrl": "https://noord-hollandsarchief.nl/record/1",
                    "linkRationale": "Zelfde herkomst en datering.",
                    "uncertainty": "middel"
                  }
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.externalLink.status") { value("concept") }
        }
    }

    @Test
    fun `missing mandatory fields report machine readable field errors and never create a record`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"localIdentifier": "", "dating": "circa 1890"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors") { isArray() }
            jsonPath("$.fieldErrors") { value(org.hamcrest.Matchers.hasItem("localIdentifier")) }
        }
    }

    @Test
    fun `possible personal data is blocked fail closed with a technical error code and never stored`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "localIdentifier": "HKH-2026-0003",
                  "title": "Familiefoto",
                  "dating": "1950",
                  "provenance": "Particuliere collectie",
                  "rightsStatus": "publicatie toegestaan",
                  "privacyClassification": "mogelijk persoonsgegevens",
                  "accessUrl": "https://collectie.hkh-autopilot.local/records/hkh-2026-0003"
                }
            """.trimIndent()
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.errorCode") { value("PRIVACY_CLASSIFICATION_BLOCKED") }
        }
    }

    @Test
    fun `never echoes the authorization header or token value back to the caller`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "localIdentifier": "HKH-2026-0004",
                  "title": "Kaart",
                  "dating": "1900",
                  "provenance": "Kadaster",
                  "rightsStatus": "publicatie toegestaan",
                  "privacyClassification": "geen persoonsgegevens",
                  "accessUrl": "https://collectie.hkh-autopilot.local/records/hkh-2026-0004"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("valid-token"))) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Bearer"))) }
        }
    }

    @Test
    fun `a confirmed local and external processable classification stores the external core fields`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(
                localIdentifier = "HKH-2026-0010",
                deceasedStatus = "overleden",
                confirmExternalArchiveData = true,
                durableUrl = "http://opendata.archieven.nl/id/1000/verified-jan-deceased",
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.externalArchiveData.stored") { value(true) }
            jsonPath("$.externalArchiveData.license") { value("CC0") }
            jsonPath("$.externalArchiveData.sourceUri") {
                value("http://opendata.archieven.nl/id/1000/verified-jan-deceased")
            }
        }

        assertEquals(
            "Jan Jansen",
            jdbcTemplate.queryForObject(
                "SELECT archive_name FROM record_intake WHERE local_identifier = ?",
                String::class.java,
                "HKH-2026-0010",
            ),
        )
    }

    @Test
    fun `a locally blocked classification refuses persistence of name and dates but keeps the license`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(
                localIdentifier = "HKH-2026-0011",
                deceasedStatus = "levend",
                confirmExternalArchiveData = true,
                durableUrl = "http://opendata.archieven.nl/id/1000/verified-jan-deceased",
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.externalArchiveData.stored") { value(false) }
            jsonPath("$.externalArchiveData.reason") {
                value(org.hamcrest.Matchers.containsString("Lokale classificatie is geblokkeerd"))
            }
            jsonPath("$.externalArchiveData.license") { value("CC0") }
        }

        val row = jdbcTemplate.queryForMap(
            "SELECT archive_name, archive_birth_date, archive_death_date FROM record_intake WHERE local_identifier = ?",
            "HKH-2026-0011",
        )
        assertEquals(null, row["archive_name"])
        assertEquals(null, row["archive_birth_date"])
        assertEquals(null, row["archive_death_date"])
    }

    @Test
    fun `an unknown deceased status refuses persistence of name and dates`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(
                localIdentifier = "HKH-2026-0012",
                deceasedStatus = null,
                confirmExternalArchiveData = true,
                durableUrl = "http://opendata.archieven.nl/id/1000/verified-jan-deceased",
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.externalArchiveData.stored") { value(false) }
        }

        assertEquals(
            null,
            jdbcTemplate.queryForObject(
                "SELECT archive_name FROM record_intake WHERE local_identifier = ?",
                String::class.java,
                "HKH-2026-0012",
            ),
        )
    }

    @Test
    fun `no death date found despite a confirmed local deceased status refuses persistence but keeps the license`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(
                localIdentifier = "HKH-2026-0013",
                deceasedStatus = "overleden",
                confirmExternalArchiveData = true,
                durableUrl = "http://opendata.archieven.nl/id/1000/verified-jan-no-death",
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.externalArchiveData.stored") { value(false) }
            jsonPath("$.externalArchiveData.reason") {
                value(org.hamcrest.Matchers.containsString("Externe classificatie is geblokkeerd"))
            }
            jsonPath("$.externalArchiveData.sourceUri") {
                value("http://opendata.archieven.nl/id/1000/verified-jan-no-death")
            }
        }
    }

    @Test
    fun `an invalid or unreachable durable url refuses persistence of all archive fields`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(
                localIdentifier = "HKH-2026-0014",
                deceasedStatus = "overleden",
                confirmExternalArchiveData = true,
                durableUrl = "https://noord-hollandsarchief.nl/record/1",
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.externalArchiveData.stored") { value(false) }
            jsonPath("$.externalArchiveData.license") { doesNotExist() }
            jsonPath("$.externalArchiveData.sourceUri") { doesNotExist() }
        }
    }

    @Test
    fun `saving without external archive data stores no archive fields and no external archive data block`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(
                localIdentifier = "HKH-2026-0015",
                deceasedStatus = "overleden",
                confirmExternalArchiveData = false,
                durableUrl = "http://opendata.archieven.nl/id/1000/verified-jan-deceased",
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.externalArchiveData") { doesNotExist() }
        }

        val row = jdbcTemplate.queryForMap(
            "SELECT archive_name, archive_license, archive_source_uri, archive_fetched_at " +
                "FROM record_intake WHERE local_identifier = ?",
            "HKH-2026-0015",
        )
        assertEquals(null, row["archive_name"])
        assertEquals(null, row["archive_license"])
        assertEquals(null, row["archive_source_uri"])
        assertEquals(null, row["archive_fetched_at"])
    }

    @Test
    fun `only the structured record intake columns are stored, never the raw external response`() {
        mockMvc.post("/api/record-intake") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = requestJson(
                localIdentifier = "HKH-2026-0016",
                deceasedStatus = "overleden",
                confirmExternalArchiveData = true,
                durableUrl = "http://opendata.archieven.nl/id/1000/verified-jan-deceased",
            )
        }.andExpect { status { isCreated() } }

        val columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'record_intake'",
            String::class.java,
        ).toSet()

        assertEquals(
            setOf(
                "id",
                "local_identifier",
                "title",
                "description",
                "dating",
                "provenance",
                "rights_status",
                "privacy_classification",
                "access_url",
                "status",
                "created_at",
                "deceased_status",
                "next_of_kin_confirmed",
                "archive_name",
                "archive_birth_date",
                "archive_death_date",
                "archive_license",
                "archive_source_uri",
                "archive_fetched_at",
                "confirmed_by",
                "confirmed_at",
            ),
            columns,
        )
    }

    private fun requestJson(
        localIdentifier: String,
        deceasedStatus: String?,
        confirmExternalArchiveData: Boolean,
        durableUrl: String,
    ): String {
        val deceasedStatusJson = deceasedStatus?.let { "\"$it\"" } ?: "null"
        return """
            {
              "localIdentifier": "$localIdentifier",
              "title": "Testrecord",
              "dating": "circa 1900",
              "provenance": "Streekarchief Waterland",
              "rightsStatus": "publicatie toegestaan",
              "privacyClassification": "geen persoonsgegevens",
              "accessUrl": "https://collectie.hkh-autopilot.local/records/$localIdentifier",
              "deceasedStatus": $deceasedStatusJson,
              "confirmExternalArchiveData": $confirmExternalArchiveData,
              "externalLink": {
                "durableUrl": "$durableUrl",
                "linkRationale": "Zelfde herkomst en datering.",
                "uncertainty": "laag"
              }
            }
        """.trimIndent()
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        private val mockServer = RecordIntakeFixtureArchivesNlServer()

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

    @TestConfiguration
    class TokenTestConfiguration {
        @Bean
        @Primary
        fun testRecordIntakeTokenVerifier(): RecordIntakeTokenVerifier =
            RecordIntakeTokenVerifier { token ->
                if (token != "valid-token") throw org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                )
                RecordIntakeTokenIdentity("collection-manager-1")
            }
    }
}

/** Een minimale fixture/mock-implementatie van het archieven.nl JSON-LD-endpoint voor record-intake tests. */
private class RecordIntakeFixtureArchivesNlServer {
    private var server: HttpServer? = null

    fun start() {
        val newServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        newServer.createContext("/") { exchange ->
            val guid = exchange.requestURI.path.substringAfterLast("/")
            val (status, body) = when (guid) {
                "verified-jan-deceased" -> 200 to
                    """{"name": "Jan Jansen", "birthDate": "1900-01-01", "deathDate": "1980-05-05", "license": "CC0"}"""
                "verified-jan-no-death" -> 200 to
                    """{"name": "Jan Jansen", "birthDate": "1900-01-01", "license": "CC0"}"""
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
