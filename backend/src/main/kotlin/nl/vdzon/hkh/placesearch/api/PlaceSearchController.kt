package nl.vdzon.hkh.placesearch.api

import java.time.Instant
import nl.vdzon.hkh.placesearch.PlaceSearchAnswer
import nl.vdzon.hkh.placesearch.PlaceSearchAnswerSentence
import nl.vdzon.hkh.placesearch.PlaceSearchCandidateSummary
import nl.vdzon.hkh.placesearch.PlaceSearchImage
import nl.vdzon.hkh.placesearch.PlaceSearchOutcome
import nl.vdzon.hkh.placesearch.PlaceSearchService
import nl.vdzon.hkh.placesearch.PlaceSearchSourceCitation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PlaceSearchApiRequest(val candidateTerm: String? = null)

data class PlaceSearchFieldErrorsResponse(val fieldErrors: List<String>)

data class PlaceSearchSourceCitationResponse(
    val number: Int,
    val qid: String,
    val wikidataLink: String,
    val checkedAt: Instant,
)

data class PlaceSearchAnswerSentenceResponse(val text: String, val sourceNumbers: List<Int>)

data class PlaceSearchImageResponse(
    val url: String,
    val fileName: String,
    val license: String?,
    val filePageUrl: String,
)

data class PlaceSearchAnswerResponse(
    val qid: String,
    val label: String,
    val description: String?,
    val sentences: List<PlaceSearchAnswerSentenceResponse>,
    val contextSentence: PlaceSearchAnswerSentenceResponse?,
    val sources: List<PlaceSearchSourceCitationResponse>,
    val images: List<PlaceSearchImageResponse>,
    val commonsOutage: Boolean,
    val disclaimer: String,
    val checkedAt: Instant,
)

data class PlaceSearchCandidateResponse(val qid: String, val label: String)

/** `status`: `READY` (precies 1 match), `NO_MATCH` (0 of >1 match), of `OUTAGE` (Wikidata-fout/timeout/budget). */
data class PlaceSearchApiResponse(
    val status: String,
    val candidateTerm: String,
    val answer: PlaceSearchAnswerResponse? = null,
    val refinementCandidates: List<PlaceSearchCandidateResponse> = emptyList(),
)

/**
 * Neemt per verzoek precies één herkende plek/gebouw-zoekterm in en voert de synchrone
 * Wikidata-/Wikimedia Commons-zoekopdracht direct in dit request uit (harde 2000ms-totaalbudget,
 * geen sessiegebonden achtergrondjob-infrastructuur zoals `personsearch`).
 */
@RestController
@RequestMapping("/api/place-search")
class PlaceSearchController(private val service: PlaceSearchService) {

    @PostMapping
    fun search(@RequestBody request: PlaceSearchApiRequest): ResponseEntity<Any> {
        val candidateTerm = request.candidateTerm?.trim()
        if (candidateTerm.isNullOrEmpty()) {
            return ResponseEntity.badRequest().body(PlaceSearchFieldErrorsResponse(listOf("candidateTerm")))
        }

        return when (val outcome = service.search(candidateTerm)) {
            is PlaceSearchOutcome.SupportedAnswer -> ResponseEntity.ok(
                PlaceSearchApiResponse(
                    status = "READY",
                    candidateTerm = candidateTerm,
                    answer = outcome.answer.toResponse(outcome.commonsOutage),
                ),
            )
            is PlaceSearchOutcome.NoMatch -> ResponseEntity.ok(
                PlaceSearchApiResponse(
                    status = "NO_MATCH",
                    candidateTerm = candidateTerm,
                    refinementCandidates = outcome.candidates.map { it.toResponse() },
                ),
            )
            PlaceSearchOutcome.WikidataOutage -> ResponseEntity.ok(
                PlaceSearchApiResponse(status = "OUTAGE", candidateTerm = candidateTerm),
            )
        }
    }
}

private fun PlaceSearchAnswer.toResponse(commonsOutage: Boolean) = PlaceSearchAnswerResponse(
    qid = qid,
    label = label,
    description = description,
    sentences = sentences.map { it.toResponse() },
    contextSentence = contextSentence?.toResponse(),
    sources = sources.map { it.toResponse() },
    images = images.map { it.toResponse() },
    commonsOutage = commonsOutage,
    disclaimer = disclaimer,
    checkedAt = checkedAt,
)

private fun PlaceSearchAnswerSentence.toResponse() = PlaceSearchAnswerSentenceResponse(text, sourceNumbers)

private fun PlaceSearchSourceCitation.toResponse() = PlaceSearchSourceCitationResponse(number, qid, wikidataLink, checkedAt)

private fun PlaceSearchImage.toResponse() = PlaceSearchImageResponse(url, fileName, license, filePageUrl)

private fun PlaceSearchCandidateSummary.toResponse() = PlaceSearchCandidateResponse(qid, label)
