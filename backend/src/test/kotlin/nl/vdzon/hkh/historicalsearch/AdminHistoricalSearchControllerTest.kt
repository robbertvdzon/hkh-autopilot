package nl.vdzon.hkh.historicalsearch

import java.time.Instant
import kotlin.test.Test
import nl.vdzon.hkh.auth.AdminAuthConfig
import nl.vdzon.hkh.auth.AdminAuthenticator
import nl.vdzon.hkh.auth.GoogleIdentity
import nl.vdzon.hkh.auth.GoogleIdTokenVerifier
import nl.vdzon.hkh.auth.PreviewRuntimeConfig
import nl.vdzon.hkh.historicalsearch.api.AdminHistoricalSearchController
import org.hamcrest.Matchers.containsString
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AdminHistoricalSearchControllerTest {
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            AdminHistoricalSearchController(
                HistoricalSearchService(listOf(FixtureHistoricalAdminAdapter())),
                AdminAuthenticator(
                    AdminAuthConfig("test-client", "admin@example.com"),
                    GoogleIdTokenVerifier { GoogleIdentity("admin@example.com", true) },
                    PreviewRuntimeConfig(false, "", "jdbc:postgresql://localhost/hkh", ""),
                ),
            ),
        )
        .build()

    @Test
    fun `admin route requires authentication`() {
        mockMvc.perform(get("/api/admin/historical-search").param("q", "kerk"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `admin route returns safe metadata and all textual status reasons`() {
        mockMvc.perform(
            get("/api/admin/historical-search")
                .param("q", "kerk")
                .param("source", "OPEN_ARCHIEVEN")
                .header("Authorization", "Bearer valid-token"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].source_name").value("Synthetisch Archief"))
            .andExpect(jsonPath("$.results[0].stable_identifier").value("hee:record-1"))
            .andExpect(jsonPath("$.results[0].original_source_url").value("https://source.example/record-1"))
            .andExpect(jsonPath("$.results[0].sourceVerificationStatus").value("CONFIRMED"))
            .andExpect(jsonPath("$.results[0].sourceVerificationReason").isNotEmpty)
            .andExpect(jsonPath("$.results[0].metadataRightsStatus").value("CONFIRMED"))
            .andExpect(jsonPath("$.results[0].metadataRightsReason").isNotEmpty)
            .andExpect(jsonPath("$.results[0].privacyStatus").value("CONFIRMED"))
            .andExpect(jsonPath("$.results[0].privacyReason").isNotEmpty)
            .andExpect(jsonPath("$.results[0].publicReleaseStatus").value("CONFIRMED"))
            .andExpect(jsonPath("$.results[0].publicReleaseReason").isNotEmpty)
            .andExpect(jsonPath("$.results[0].objectMediaRightsStatus").value("CONFIRMED"))
            .andExpect(jsonPath("$.results[0].title").doesNotExist())
            .andExpect(jsonPath("$.results[0].relationships").doesNotExist())
            .andExpect(jsonPath("$.state").value("RESULTS"))
    }

    @Test
    fun `invalid provider identity is not returned and status is fail closed`() {
        val controller = AdminHistoricalSearchController(
            HistoricalSearchService(listOf(FixtureHistoricalAdminAdapter().copyInvalid())),
            AdminAuthenticator(
                AdminAuthConfig("test-client", "admin@example.com"),
                GoogleIdTokenVerifier { GoogleIdentity("admin@example.com", true) },
                PreviewRuntimeConfig(false, "", "jdbc:postgresql://localhost/hkh", ""),
            ),
        )
        val invalidMockMvc = MockMvcBuilders.standaloneSetup(controller).build()

        invalidMockMvc.perform(
            get("/api/admin/historical-search").header("Authorization", "Bearer valid-token"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].source_name").doesNotExist())
            .andExpect(jsonPath("$.results[0].stable_identifier").doesNotExist())
            .andExpect(jsonPath("$.results[0].original_source_url").doesNotExist())
            .andExpect(jsonPath("$.results[0].sourceVerificationStatus").value("REJECTED"))
            .andExpect(jsonPath("$.results[0].sourceVerificationReason").value(containsString("ongeldige")))
    }
}

private data class FixtureHistoricalAdminAdapter(
    val invalid: Boolean = false,
) : HistoricalSearchAdapter {
    override val source = HistoricalSearchSource.OPEN_ARCHIEVEN

    fun copyInvalid() = copy(invalid = true)

    override fun search(query: HistoricalSearchQuery) = HistoricalSearchPage(
        source = source,
        results = listOf(
            HistoricalSearchResult(
                source = source,
                sourceRecordId = "provider-internal-id",
                stableUrl = "https://legacy.example/do-not-use",
                title = "Raw title must not escape",
                description = "Raw description must not escape",
                person = "Raw person must not escape",
                event = "Raw event must not escape",
                dateStart = null,
                dateEnd = null,
                institution = null,
                rights = "PUBLIC",
                privacy = "CLEAR",
                retrievedAt = Instant.parse("2026-01-01T00:00:00Z"),
                technicalStatus = HistoricalTechnicalStatus.AVAILABLE,
                metadataRights = HistoricalRightsStatus.ALLOWED,
                objectMediaRights = HistoricalRightsStatus.ALLOWED,
                privacyStatus = HistoricalPrivacyStatus.CLEAR,
                sourceName = if (invalid) "\u0000raw" else "Synthetisch Archief",
                stableIdentifier = if (invalid) "" else "hee:record-1",
                originalSourceUrl = if (invalid) "javascript:alert(1)" else "https://source.example/record-1",
            ),
        ),
        total = 1,
        status = HistoricalTechnicalStatus.AVAILABLE,
    )
}
