package nl.vdzon.hkh.news.api

import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.vdzon.hkh.auth.GoogleIdTokenVerifier
import nl.vdzon.hkh.auth.GoogleIdentity
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "hkh.auth.google-client-id=test-client",
        "hkh.auth.admin-allowed-emails=admin@example.com",
    ],
)
@Import(LatestNewsApiIntegrationTest.AuthTestConfiguration::class)
class LatestNewsApiIntegrationTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
    @param:Autowired private val applicationContext: ApplicationContext,
    @param:Autowired private val objectMapper: ObjectMapper,
) {
    @Test
    fun `administrator creates news and public API returns it newest first`() {
        create("Eerste bericht QA-volgorde", "Dit is het eerste nieuwsbericht met merker qa-volgorde-marker.")
        create("Tweede bericht QA-volgorde", "Dit is het nieuwste nieuwsbericht met merker qa-volgorde-marker.")

        mockMvc.get("/api/news") { param("q", "qa-volgorde-marker") }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].title") { value("Tweede bericht QA-volgorde") }
                jsonPath("$.items[1].title") { value("Eerste bericht QA-volgorde") }
                jsonPath("$.items[0].publishedAt") { exists() }
                jsonPath("$.total") { value(2) }
            }
    }

    @Test
    fun `admin API rejects missing preview credentials and invalid input`() {
        mockMvc.post("/api/admin/news") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Niet toegestaan","message":"Geen credentials"}"""
        }.andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/admin/news") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"  ","message":"Geldig bericht"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `free text search matches title and message with non-empty provenance`() {
        create("Nieuw archief geopend", "Bezoekers kunnen nu de Kerkweg-collectie inzien.")
        create("Ander bericht", "Dit gaat nergens over de zoekterm.")

        mockMvc.get("/api/news") { param("q", "Kerkweg-collectie") }
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(1) }
                jsonPath("$.items[0].title") { value("Nieuw archief geopend") }
                jsonPath("$.items[0].source") { value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())) }
                jsonPath("$.items[0].source") { value(org.hamcrest.Matchers.containsString("gepubliceerd HKH-nieuwsbericht")) }
                jsonPath("$.items[0].source") { value(org.hamcrest.Matchers.containsString("gepubliceerd op")) }
            }
    }

    @Test
    fun `entity filter returns only messages linked to that entity with the correct type label`() {
        create("Lezing over Slot Assumburg", "Bewoners en landschap van het kasteel door de eeuwen heen.")
        create("Kermis van dit jaar", "Muziek en attracties op het plein.")
        create("Neutraal bericht", "Bevat geen bekende entiteit.")

        mockMvc.get("/api/news") { param("entity", "Slot Assumburg") }
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(1) }
                jsonPath("$.items[0].title") { value("Lezing over Slot Assumburg") }
                jsonPath("$.items[0].entities[?(@.label == 'Slot Assumburg')].type") { value("PLEK") }
            }

        mockMvc.get("/api/news") { param("entity", "Kermis") }
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(1) }
                jsonPath("$.items[0].title") { value("Kermis van dit jaar") }
                jsonPath("$.items[0].entities[?(@.label == 'Kermis')].type") { value("GEBEURTENIS") }
            }
    }

    @Test
    fun `search or entity filter without matches yields an explicit empty result`() {
        create("Bericht zonder relevantie", "Geen entiteit en geen match voor de zoekterm.")

        mockMvc.get("/api/news") { param("q", "onvindbareterm-xyz") }
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(0) }
                jsonPath("$.items") { isEmpty() }
            }

        mockMvc.get("/api/news") { param("entity", "Onbekende Entiteit") }
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(0) }
                jsonPath("$.items") { isEmpty() }
            }
    }

    @Test
    fun `response never exposes record intake, privacy classification or external verification data`() {
        create("Bericht met Klaas Groen", "Verslag van een gesprek met Klaas Groen over vroeger.")

        val forbiddenKeys = listOf(
            "recordIntake",
            "privacyClassification",
            "externalVerification",
            "license",
            "verificationStatus",
            "gedcomSource",
            "accessToken",
        )

        mockMvc.get("/api/news").andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString.let { body ->
            forbiddenKeys.forEach { key ->
                org.junit.jupiter.api.Assertions.assertFalse(
                    body.contains("\"$key\""),
                    "Response bevat onverwacht veld $key",
                )
            }
        }
    }

    @Test
    fun `a message placed directly in the store layer outside latest_news never appears in entities or search`() {
        create("Zichtbaar bericht over Waterakkers", "Verslag van werk aan de Waterakkers in het dorp.")

        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS test_unpublished_news_fixture (
                id BIGINT PRIMARY KEY,
                title VARCHAR(160) NOT NULL,
                message VARCHAR(10000) NOT NULL
            )
            """.trimIndent(),
        )
        jdbcTemplate.update(
            "INSERT INTO test_unpublished_news_fixture (id, title, message) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
            -999L,
            "Niet gepubliceerd bericht over Slot Assumburg",
            "Dit bericht staat buiten latest_news en mag nooit verschijnen.",
        )

        mockMvc.get("/api/news") { param("q", "Niet gepubliceerd bericht") }
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(0) }
                jsonPath("$.items") { isEmpty() }
            }

        mockMvc.get("/api/news")
            .andExpect {
                status { isOk() }
                jsonPath("$.entities[?(@.label == 'Slot Assumburg')]") { doesNotExist() }
            }
    }

    @Test
    fun `news module still exposes exactly two controllers with the existing two routes`() {
        val mapping = applicationContext.getBean(
            "requestMappingHandlerMapping",
            org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping::class.java,
        )
        val newsHandlerMethods = mapping.handlerMethods.filterValues {
            it.beanType == LatestNewsController::class.java || it.beanType == AdminLatestNewsController::class.java
        }

        val controllers = newsHandlerMethods.values.map { it.beanType }.toSet()
        assertEquals(setOf(LatestNewsController::class.java, AdminLatestNewsController::class.java), controllers)
        assertEquals(2, newsHandlerMethods.size, "Unexpected route count for the news module: ${newsHandlerMethods.keys}")
    }

    @Test
    fun `the extended news response contract is documented via the generated OpenAPI schema`() {
        val body = mockMvc.get("/v3/api-docs").andExpect { status { isOk() } }.andReturn().response.contentAsString
        val root = objectMapper.readTree(body)

        val getNewsResponseSchemaRef = root
            .path("paths").path("/api/news").path("get")
            .path("responses").path("200")
            .path("content").path("*/*").path("schema").path("\$ref").asText()
        assertTrue(getNewsResponseSchemaRef.endsWith("LatestNewsListResponse"), "Unexpected schema ref: $getNewsResponseSchemaRef")

        val getNewsOperation: tools.jackson.databind.JsonNode =
            root.path("paths").path("/api/news").path("get")
        val parametersNode: tools.jackson.databind.JsonNode = getNewsOperation.path("parameters")
        val queryParamNames: Set<String> = parametersNode.asIterable().map { node -> node.path("name").asText() }.toSet()
        assertEquals(setOf("q", "entity"), queryParamNames)

        val schemas = root.path("components").path("schemas")
        val listResponseProperties = schemas.path("LatestNewsListResponse").path("properties").propertyNames().toSet()
        assertEquals(setOf("items", "total", "entities"), listResponseProperties)

        val itemResponseProperties = schemas.path("LatestNewsItemResponse").path("properties").propertyNames().toSet()
        assertEquals(setOf("id", "title", "message", "publishedAt", "createdAt", "entities", "source"), itemResponseProperties)

        val aggregatedEntityProperties =
            schemas.path("AggregatedNewsEntityResponse").path("properties").propertyNames().toSet()
        assertEquals(setOf("type", "label", "itemCount"), aggregatedEntityProperties)
    }

    private fun create(title: String, message: String) {
        mockMvc.post("/api/admin/news") {
            header("Authorization", "Bearer valid-token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title","message":"$message"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.title") { value(title) }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @TestConfiguration
    class AuthTestConfiguration {
        @Bean
        @Primary
        fun testGoogleIdTokenVerifier(): GoogleIdTokenVerifier =
            GoogleIdTokenVerifier { GoogleIdentity("admin@example.com", true) }
    }
}
