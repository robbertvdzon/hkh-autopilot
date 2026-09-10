package nl.vdzon.hkh.topicsearch.api

import java.time.Instant
import nl.vdzon.hkh.topicsearch.TopicSearchAnswer
import nl.vdzon.hkh.topicsearch.TopicSearchContext
import nl.vdzon.hkh.topicsearch.TopicSearchOutcome
import nl.vdzon.hkh.topicsearch.TopicSearchRecord
import nl.vdzon.hkh.topicsearch.TopicSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class TopicSearchApiRequest(val topicSearchTerm: String? = null)

data class TopicSearchFieldErrorsResponse(val fieldErrors: List<String>)

data class TopicSearchRecordResponse(
    val title: String,
    val dataProvider: String,
    val licenseText: String,
    val licenseUrl: String?,
    val sourceUrl: String,
)

data class TopicSearchContextResponse(val label: String, val description: String?)

data class TopicSearchAnswerResponse(
    val topicSearchTerm: String,
    val records: List<TopicSearchRecordResponse>,
    val context: TopicSearchContextResponse?,
    val checkedAt: Instant,
)

/** `status`: `READY` (>=1 geldig record), `EMPTY` (0 geldige records), of `OUTAGE` (Europeana-fout/timeout/budget/configuratiefout). */
data class TopicSearchApiResponse(
    val status: String,
    val topicSearchTerm: String,
    val answer: TopicSearchAnswerResponse? = null,
    val refinementSuggestions: List<String> = emptyList(),
)

/**
 * Neemt per verzoek precies één herkende onderwerp/voorwerp/gebeurtenis-zoekterm in en voert de
 * synchrone Europeana-/Wikidata-zoekopdracht direct in dit request uit (harde 2000ms-totaalbudget,
 * geen sessiegebonden achtergrondjob-infrastructuur zoals `personsearch`).
 */
@RestController
@RequestMapping("/api/topic-search")
class TopicSearchController(private val service: TopicSearchService) {

    @PostMapping
    fun search(@RequestBody request: TopicSearchApiRequest): ResponseEntity<Any> {
        val topicSearchTerm = request.topicSearchTerm?.trim()
        if (topicSearchTerm.isNullOrEmpty()) {
            return ResponseEntity.badRequest().body(TopicSearchFieldErrorsResponse(listOf("topicSearchTerm")))
        }

        return when (val outcome = service.search(topicSearchTerm)) {
            is TopicSearchOutcome.Ready -> ResponseEntity.ok(
                TopicSearchApiResponse(
                    status = "READY",
                    topicSearchTerm = topicSearchTerm,
                    answer = outcome.answer.toResponse(),
                ),
            )
            is TopicSearchOutcome.Empty -> ResponseEntity.ok(
                TopicSearchApiResponse(
                    status = "EMPTY",
                    topicSearchTerm = topicSearchTerm,
                    refinementSuggestions = outcome.refinementSuggestions,
                ),
            )
            TopicSearchOutcome.EuropeanaOutage -> ResponseEntity.ok(
                TopicSearchApiResponse(status = "OUTAGE", topicSearchTerm = topicSearchTerm),
            )
        }
    }
}

private fun TopicSearchAnswer.toResponse() = TopicSearchAnswerResponse(
    topicSearchTerm = topicSearchTerm,
    records = records.map { it.toResponse() },
    context = context?.toResponse(),
    checkedAt = checkedAt,
)

private fun TopicSearchRecord.toResponse() =
    TopicSearchRecordResponse(title, dataProvider, license.text, license.url, sourceUrl)

private fun TopicSearchContext.toResponse() = TopicSearchContextResponse(label, description)
