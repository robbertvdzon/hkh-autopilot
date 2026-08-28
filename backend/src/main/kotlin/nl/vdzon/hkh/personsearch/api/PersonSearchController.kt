package nl.vdzon.hkh.personsearch.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import nl.vdzon.hkh.personsearch.PersonSearchAnswer
import nl.vdzon.hkh.personsearch.PersonSearchRequest
import nl.vdzon.hkh.personsearch.PersonSearchService
import nl.vdzon.hkh.personsearch.PersonSearchSessionIndicator
import nl.vdzon.hkh.personsearch.PersonSearchSessionResolver
import nl.vdzon.hkh.personsearch.PersonSearchStatusResult
import nl.vdzon.hkh.personsearch.PersonSearchSubmitResult
import nl.vdzon.hkh.personsearch.PersonSearchWikidataContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PersonSearchApiRequest(
    val recognizedName: String? = null,
    val secondName: String? = null,
    val eventType: String? = null,
    val yearOrPeriod: String? = null,
    val heemskerkMeaningQid: String? = null,
    val originalQuery: String? = null,
)

data class PersonSearchFieldErrorsResponse(val fieldErrors: List<String>)

data class PersonSearchSourceCitationResponse(
    val number: Int,
    val institution: String,
    val sourceType: String,
    val archiveCode: String,
    val identifier: String,
    val archiveNumber: String?,
    val registerNumber: String?,
    val deedNumber: String?,
    val recordNumber: String,
    val openArchivesLink: String,
    val digitalOriginalLink: String?,
    val checkedAt: Instant,
)

data class PersonSearchAnswerSentenceResponse(val text: String, val sourceNumbers: List<Int>)

data class PersonSearchConnectionResponse(val role: String, val personName: String)

data class PersonSearchAnswerResponse(
    val sentences: List<PersonSearchAnswerSentenceResponse>,
    val sources: List<PersonSearchSourceCitationResponse>,
    val connections: List<PersonSearchConnectionResponse>,
    val disclaimer: String,
)

data class PersonSearchContextResponse(val label: String, val description: String?)

data class PersonSearchApiResponse(
    val jobId: String,
    val status: String,
    val originalQuery: String,
    val refinementMessage: String? = null,
    val answer: PersonSearchAnswerResponse? = null,
    val context: PersonSearchContextResponse? = null,
)

/**
 * Statusaanvraag-/stopactie-/openactie-respons. Bevat nooit een sessie-id; het opgevraagde
 * `jobId` is de enige identifier erin.
 */
data class PersonSearchStatusResponse(
    val jobId: String,
    val status: String,
    val originalQuery: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val openArchievenStatus: String,
    val wikidataStatus: String,
    val openedAt: Instant? = null,
    val refinementMessage: String? = null,
    val answer: PersonSearchAnswerResponse? = null,
    val context: PersonSearchContextResponse? = null,
)

data class PersonSearchSessionIndicatorResponse(
    val runningCount: Int,
    val readyUnopenedCount: Int,
    val runningJobIds: List<String>,
    val readyUnopenedJobIds: List<String>,
)

/**
 * Neemt per verzoek precies één ondersteunde persoonsvraag in, gebonden aan een route-specifieke
 * sessiecookie (geen login). Voert de live Records/Search-/Records/Show-aanroepen synchroon uit
 * binnen een deadline van 2000ms; bij een langer lopende job retourneert het verzoek met status
 * `QUEUED`/`RUNNING` zonder dat de achtergrondtaak stopt. Statusaanvraag, stopactie, openactie en
 * sessie-indicator hierboven zijn allemaal fail-closed sessiegebonden (een andere sessie krijgt
 * een 404, alsof de job niet bestaat).
 */
@RestController
@RequestMapping("/api/person-search")
class PersonSearchController(
    private val sessionResolver: PersonSearchSessionResolver,
    private val service: PersonSearchService,
) {
    @PostMapping
    fun submit(
        @RequestBody request: PersonSearchApiRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<Any> {
        val recognizedName = request.recognizedName?.trim()
        if (recognizedName.isNullOrEmpty()) {
            return ResponseEntity.badRequest().body(PersonSearchFieldErrorsResponse(listOf("recognizedName")))
        }

        val sessionId = sessionResolver.resolve(httpRequest, httpResponse)
        val domain = PersonSearchRequest(
            recognizedName = recognizedName,
            secondName = request.secondName?.trim()?.takeIf { it.isNotEmpty() },
            eventType = request.eventType?.trim()?.takeIf { it.isNotEmpty() },
            yearOrPeriod = request.yearOrPeriod?.trim()?.takeIf { it.isNotEmpty() },
            heemskerkMeaningQid = request.heemskerkMeaningQid?.trim()?.takeIf { it.isNotEmpty() },
            originalQuery = request.originalQuery?.trim()?.takeIf { it.isNotEmpty() } ?: recognizedName,
        )

        val result = service.submit(sessionId, domain)
        return ResponseEntity.status(HttpStatus.OK).body(result.toResponse(domain.originalQuery))
    }

    @GetMapping("/{jobId}/status")
    fun status(
        @PathVariable jobId: String,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<Any> {
        val sessionId = sessionResolver.resolve(httpRequest, httpResponse)
        val result = service.status(jobId, sessionId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result.toResponse())
    }

    @PostMapping("/{jobId}/cancel")
    fun cancel(
        @PathVariable jobId: String,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<Any> {
        val sessionId = sessionResolver.resolve(httpRequest, httpResponse)
        val result = service.cancel(jobId, sessionId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result.toResponse())
    }

    @PostMapping("/{jobId}/open")
    fun open(
        @PathVariable jobId: String,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<Any> {
        val sessionId = sessionResolver.resolve(httpRequest, httpResponse)
        val result = service.open(jobId, sessionId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result.toResponse())
    }

    @GetMapping("/session")
    fun session(httpRequest: HttpServletRequest, httpResponse: HttpServletResponse): ResponseEntity<Any> {
        val sessionId = sessionResolver.resolve(httpRequest, httpResponse)
        val indicator = service.sessionIndicator(sessionId)
        return ResponseEntity.ok(indicator.toResponse())
    }
}

private fun PersonSearchSubmitResult.toResponse(originalQuery: String) = PersonSearchApiResponse(
    jobId = jobId,
    status = status.name,
    originalQuery = originalQuery,
    refinementMessage = payload?.refinementMessage,
    answer = payload?.answer?.toResponse(),
    context = payload?.context?.toResponse(),
)

private fun PersonSearchStatusResult.toResponse() = PersonSearchStatusResponse(
    jobId = job.id,
    status = job.status.name,
    originalQuery = originalQuery,
    createdAt = job.createdAt,
    updatedAt = job.updatedAt,
    openArchievenStatus = job.openArchievenStatus.name,
    wikidataStatus = job.wikidataStatus.name,
    openedAt = job.openedAt,
    refinementMessage = payload?.refinementMessage,
    answer = payload?.answer?.toResponse(),
    context = payload?.context?.toResponse(),
)

private fun PersonSearchSessionIndicator.toResponse() = PersonSearchSessionIndicatorResponse(
    runningCount = runningCount,
    readyUnopenedCount = readyUnopenedCount,
    runningJobIds = runningJobIds,
    readyUnopenedJobIds = readyUnopenedJobIds,
)

private fun PersonSearchAnswer.toResponse() = PersonSearchAnswerResponse(
    sentences = sentences.map { PersonSearchAnswerSentenceResponse(it.text, it.sourceNumbers) },
    sources = sources.map {
        PersonSearchSourceCitationResponse(
            number = it.number,
            institution = it.institution,
            sourceType = it.sourceType,
            archiveCode = it.archiveCode,
            identifier = it.identifier,
            archiveNumber = it.archiveNumber,
            registerNumber = it.registerNumber,
            deedNumber = it.deedNumber,
            recordNumber = it.recordNumber,
            openArchivesLink = it.openArchivesLink,
            digitalOriginalLink = it.digitalOriginalLink,
            checkedAt = it.checkedAt,
        )
    },
    connections = connections.map { PersonSearchConnectionResponse(it.role, it.personName) },
    disclaimer = disclaimer,
)

private fun PersonSearchWikidataContext.toResponse() = PersonSearchContextResponse(label, description)
