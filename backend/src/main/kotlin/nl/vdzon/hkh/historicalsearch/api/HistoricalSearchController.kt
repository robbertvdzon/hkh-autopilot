package nl.vdzon.hkh.historicalsearch.api

import java.time.Instant
import nl.vdzon.hkh.historicalsearch.HistoricalPrivacyStatus
import nl.vdzon.hkh.historicalsearch.HistoricalRightsStatus
import nl.vdzon.hkh.historicalsearch.HistoricalSearchOutcome
import nl.vdzon.hkh.historicalsearch.HistoricalSearchService
import nl.vdzon.hkh.historicalsearch.HistoricalSearchValidation
import nl.vdzon.hkh.historicalsearch.HistoricalTechnicalStatus
import nl.vdzon.hkh.historicalsearch.HistoricalSearchSource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class HistoricalSearchResultResponse(
    val source: String,
    val sourceRecordId: String,
    val stableUrl: String,
    val title: String?,
    val description: String?,
    val place: String?,
    val person: String?,
    val event: String?,
    val dateStart: String?,
    val dateEnd: String?,
    val institution: String?,
    val rights: String?,
    val privacy: String?,
    val retrievedAt: Instant,
    val technicalStatus: String,
    val metadataRights: String,
    val objectMediaRights: String,
    val privacyStatus: String,
    val placeStatus: String,
    val personStatus: String,
    val eventStatus: String,
)

data class HistoricalSearchSourceStatusResponse(
    val source: String,
    val status: String,
    val message: String?,
    val resultCount: Int?,
    val heemskerkCount: Int?,
)

data class HistoricalSearchResponse(
    val results: List<HistoricalSearchResultResponse>,
    val total: Int,
    val start: Int,
    val limit: Int,
    val sources: List<HistoricalSearchSourceStatusResponse>,
    val state: String,
)

data class HistoricalSearchErrorResponse(val error: String)

@RestController
@RequestMapping("/api/historical-search")
class HistoricalSearchController(private val service: HistoricalSearchService) {
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
    ): ResponseEntity<Any> {
        val query = runCatching {
            HistoricalSearchValidation.normalize(q, place, person, event, fromYear, toYear, source, start, limit)
        }.getOrElse { return ResponseEntity.badRequest().body(HistoricalSearchErrorResponse(it.message ?: "Ongeldige zoekopdracht.")) }
        return ResponseEntity.ok(service.search(query).toResponse())
    }
}

private fun HistoricalSearchOutcome.toResponse() = HistoricalSearchResponse(
    results = results.map {
        HistoricalSearchResultResponse(
            source = it.source.name,
            sourceRecordId = it.sourceRecordId,
            stableUrl = it.stableUrl,
            title = it.title,
            description = it.description,
            place = it.place,
            person = it.person,
            event = it.event,
            dateStart = it.dateStart,
            dateEnd = it.dateEnd,
            institution = it.institution,
            rights = it.rights,
            privacy = it.privacy,
            retrievedAt = it.retrievedAt,
            technicalStatus = it.technicalStatus.name,
            metadataRights = it.metadataRights.name,
            objectMediaRights = it.objectMediaRights.name,
            privacyStatus = it.privacyStatus.name,
            placeStatus = it.placeStatus.name,
            personStatus = it.personStatus.name,
            eventStatus = it.eventStatus.name,
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
        )
    },
    state = state.name,
)
