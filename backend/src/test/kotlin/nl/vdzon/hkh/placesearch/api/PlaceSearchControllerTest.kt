package nl.vdzon.hkh.placesearch.api

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.vdzon.hkh.placesearch.PlaceSearchAnswer
import nl.vdzon.hkh.placesearch.PlaceSearchAnswerSentence
import nl.vdzon.hkh.placesearch.PlaceSearchCandidateSummary
import nl.vdzon.hkh.placesearch.PlaceSearchOutcome
import nl.vdzon.hkh.placesearch.PlaceSearchService
import nl.vdzon.hkh.placesearch.PlaceSearchSourceCitation
import org.springframework.http.HttpStatus

class PlaceSearchControllerTest {

    private val checkedAt: Instant = Instant.parse("2026-08-31T10:00:00Z")

    private fun controllerReturning(outcome: PlaceSearchOutcome): PlaceSearchController {
        var receivedTerm: String? = null
        val service = object : PlaceSearchService(
            wikidataClient = throwingWikidataClient(),
            commonsClient = throwingCommonsClient(),
            answerBuilder = nl.vdzon.hkh.placesearch.PlaceSearchAnswerBuilder(),
            executor = java.util.concurrent.Executors.newSingleThreadExecutor(),
        ) {
            override fun search(candidateTerm: String): PlaceSearchOutcome {
                receivedTerm = candidateTerm
                return outcome
            }
        }
        return PlaceSearchController(service)
    }

    private fun throwingWikidataClient() =
        nl.vdzon.hkh.placesearch.PlaceSearchWikidataClient(
            org.springframework.web.client.RestClient.create("http://localhost:1"),
        )

    private fun throwingCommonsClient() =
        nl.vdzon.hkh.placesearch.PlaceSearchCommonsClient(
            org.springframework.web.client.RestClient.create("http://localhost:1"),
        )

    @Test
    fun `rejects a request without a candidate term and never calls the service`() {
        var searched = false
        val service = object : PlaceSearchService(
            wikidataClient = throwingWikidataClient(),
            commonsClient = throwingCommonsClient(),
            answerBuilder = nl.vdzon.hkh.placesearch.PlaceSearchAnswerBuilder(),
            executor = java.util.concurrent.Executors.newSingleThreadExecutor(),
        ) {
            override fun search(candidateTerm: String): PlaceSearchOutcome {
                searched = true
                return PlaceSearchOutcome.NoMatch(emptyList())
            }
        }
        val controller = PlaceSearchController(service)

        val response = controller.search(PlaceSearchApiRequest(candidateTerm = "   "))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(!searched)
    }

    @Test
    fun `maps a supported answer to a READY response with sources and images`() {
        val answer = PlaceSearchAnswer(
            qid = "Q1968571",
            label = "Kasteel Assumburg",
            description = "kasteel in Heemskerk",
            sentences = listOf(PlaceSearchAnswerSentence("Kasteel Assumburg is een kasteel.", listOf(1))),
            contextSentence = PlaceSearchAnswerSentence("Kasteel Assumburg ligt in de gemeente Heemskerk.", listOf(2)),
            sources = listOf(
                PlaceSearchSourceCitation(1, "Q1968571", "https://www.wikidata.org/wiki/Q1968571", checkedAt),
                PlaceSearchSourceCitation(2, "Q1968571", "https://www.wikidata.org/wiki/Q1968571", checkedAt),
            ),
            images = emptyList(),
            disclaimer = "disclaimer",
            checkedAt = checkedAt,
        )
        val controller = controllerReturning(PlaceSearchOutcome.SupportedAnswer(answer, commonsOutage = false))

        val response = controller.search(PlaceSearchApiRequest(candidateTerm = "Kasteel Assumburg"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as PlaceSearchApiResponse
        assertEquals("READY", body.status)
        assertEquals("Q1968571", body.answer?.qid)
        assertEquals(2, body.answer?.sources?.size)
        assertTrue(body.refinementCandidates.isEmpty())
    }

    @Test
    fun `maps a no match outcome with refinement candidates`() {
        val controller = controllerReturning(
            PlaceSearchOutcome.NoMatch(listOf(PlaceSearchCandidateSummary("Q1", "Kasteel A"), PlaceSearchCandidateSummary("Q2", "Kasteel B"))),
        )

        val response = controller.search(PlaceSearchApiRequest(candidateTerm = "Kasteel"))

        val body = response.body as PlaceSearchApiResponse
        assertEquals("NO_MATCH", body.status)
        assertEquals(listOf("Kasteel A", "Kasteel B"), body.refinementCandidates.map { it.label })
    }

    @Test
    fun `maps a wikidata outage`() {
        val controller = controllerReturning(PlaceSearchOutcome.WikidataOutage)

        val response = controller.search(PlaceSearchApiRequest(candidateTerm = "Kasteel Assumburg"))

        val body = response.body as PlaceSearchApiResponse
        assertEquals("OUTAGE", body.status)
    }
}
