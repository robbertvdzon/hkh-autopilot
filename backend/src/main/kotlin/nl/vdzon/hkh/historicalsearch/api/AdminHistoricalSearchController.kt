package nl.vdzon.hkh.historicalsearch.api

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import nl.vdzon.hkh.auth.AdminAuthenticator
import nl.vdzon.hkh.auth.PreviewRuntimeConfig
import nl.vdzon.hkh.historicalsearch.AllowAllHistoricalSearchRequestBudget
import nl.vdzon.hkh.historicalsearch.HistoricalAdminStatusContract
import nl.vdzon.hkh.historicalsearch.HistoricalClientIpResolver
import nl.vdzon.hkh.historicalsearch.HistoricalSearchOutcome
import nl.vdzon.hkh.historicalsearch.HistoricalSearchRequestBudget
import nl.vdzon.hkh.historicalsearch.HistoricalSearchRequestBudgetExceededException
import nl.vdzon.hkh.historicalsearch.HistoricalSearchRequestContext
import nl.vdzon.hkh.historicalsearch.HistoricalSearchRequestIdentity
import nl.vdzon.hkh.historicalsearch.HistoricalSearchService
import nl.vdzon.hkh.historicalsearch.HistoricalSearchValidation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class HistoricalAdminStatusResponse(
    val status: String,
    val reason: String,
)

data class HistoricalAdminResultResponse(
    val source: String,
    @get:JsonProperty("source_name")
    val sourceName: String?,
    @get:JsonProperty("stable_identifier")
    val stableIdentifier: String?,
    @get:JsonProperty("original_source_url")
    val originalSourceUrl: String?,
    val technicalStatus: String,
    val sourceVerificationStatus: String,
    val sourceVerificationReason: String,
    val metadataRightsStatus: String,
    val metadataRightsReason: String,
    val privacyStatus: String,
    val privacyReason: String,
    val publicReleaseStatus: String,
    val publicReleaseReason: String,
    val objectMediaRightsStatus: String,
    val objectMediaRightsReason: String,
)

data class HistoricalAdminSearchResponse(
    val results: List<HistoricalAdminResultResponse>,
    val total: Int,
    val start: Int,
    val limit: Int,
    val sources: List<HistoricalSearchSourceStatusResponse>,
    val state: String,
)

data class HistoricalAdminSearchErrorResponse(val error: String)

/** Authenticated admin view over the existing normalized historical search result contract. */
@RestController
@RequestMapping("/api/admin/historical-search")
class AdminHistoricalSearchController(
    private val service: HistoricalSearchService,
    private val authenticator: AdminAuthenticator,
    private val requestBudget: HistoricalSearchRequestBudget = AllowAllHistoricalSearchRequestBudget,
    private val clientIpResolver: HistoricalClientIpResolver = HistoricalClientIpResolver(),
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) place: String?,
        @RequestParam(required = false) person: String?,
        @RequestParam(required = false) event: String?,
        @RequestParam(required = false) fromYear: String?,
        @RequestParam(required = false) toYear: String?,
        @RequestParam(required = false) source: String?,
        @RequestParam(defaultValue = "0") start: Int,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestHeader(PreviewRuntimeConfig.ADMIN_HEADER, required = false) previewHeader: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        authenticator.authenticate(authorization, previewHeader)
        val query = runCatching {
            HistoricalSearchValidation.normalize(q, place, person, event, fromYear, toYear, source, start, limit)
        }.getOrElse {
            return ResponseEntity.badRequest().body(
                HistoricalAdminSearchErrorResponse(it.message ?: "Ongeldige zoekopdracht."),
            )
        }
        return try {
            val identity = HistoricalSearchRequestIdentity(clientIpResolver.resolve(request), requestBudget)
            HistoricalSearchRequestContext.withIdentity(identity) {
                ResponseEntity.ok(service.search(query).toAdminResponse())
            }
        } catch (_: HistoricalSearchRequestBudgetExceededException) {
            ResponseEntity.status(429).body(HistoricalAdminSearchErrorResponse("RATE_LIMITED"))
        }
    }
}

private fun HistoricalSearchOutcome.toAdminResponse() = HistoricalAdminSearchResponse(
    results = results.map { result ->
        val status = HistoricalAdminStatusContract.evaluate(result)
        HistoricalAdminResultResponse(
            source = result.source.name,
            sourceName = HistoricalAdminStatusContract.safeSourceName(result),
            stableIdentifier = HistoricalAdminStatusContract.safeStableIdentifier(result),
            originalSourceUrl = HistoricalAdminStatusContract.safeOriginalSourceUrl(result),
            technicalStatus = result.technicalStatus.name,
            sourceVerificationStatus = status.sourceVerification.status.name,
            sourceVerificationReason = status.sourceVerification.reason,
            metadataRightsStatus = status.metadataRights.status.name,
            metadataRightsReason = status.metadataRights.reason,
            privacyStatus = status.privacy.status.name,
            privacyReason = status.privacy.reason,
            publicReleaseStatus = status.publicRelease.status.name,
            publicReleaseReason = status.publicRelease.reason,
            objectMediaRightsStatus = status.objectMediaRights.status.name,
            objectMediaRightsReason = status.objectMediaRights.reason,
        )
    },
    total = total,
    start = start,
    limit = limit,
    sources = sources.map {
        HistoricalSearchSourceStatusResponse(
            source = it.source.name,
            status = it.status.name,
            message = it.message,
            resultCount = it.resultCount,
            heemskerkCount = it.heemskerkCount,
            querySemantics = it.querySemantics,
        )
    },
    state = state.name,
)
