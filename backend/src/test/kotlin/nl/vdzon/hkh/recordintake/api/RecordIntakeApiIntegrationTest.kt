package nl.vdzon.hkh.recordintake.api

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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(RecordIntakeApiIntegrationTest.TokenTestConfiguration::class)
class RecordIntakeApiIntegrationTest(@param:Autowired private val mockMvc: MockMvc) {

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

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")
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
