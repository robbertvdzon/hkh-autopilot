package nl.vdzon.hkh.topicsearch.api

import java.time.Instant
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.vdzon.hkh.topicsearch.ArchivesEuropeanaClient
import nl.vdzon.hkh.topicsearch.EuropeanaSearchOutcome
import nl.vdzon.hkh.topicsearch.TopicSearchAnswer
import nl.vdzon.hkh.topicsearch.TopicSearchContext
import nl.vdzon.hkh.topicsearch.TopicSearchLicenseBadge
import nl.vdzon.hkh.topicsearch.TopicSearchOutcome
import nl.vdzon.hkh.topicsearch.TopicSearchRecord
import nl.vdzon.hkh.topicsearch.TopicSearchService
import nl.vdzon.hkh.topicsearch.TopicSearchWikidataContextSource
import org.springframework.http.HttpStatus

class TopicSearchControllerTest {

    private val checkedAt: Instant = Instant.parse("2026-09-10T10:00:00Z")

    private val throwingEuropeanaClient = ArchivesEuropeanaClient { EuropeanaSearchOutcome.Failure }
    private val emptyWikidataContext = TopicSearchWikidataContextSource { null }

    private fun controllerReturning(outcome: TopicSearchOutcome): TopicSearchController {
        val service = object : TopicSearchService(
            europeanaClient = throwingEuropeanaClient,
            wikidataContextClient = emptyWikidataContext,
            executor = Executors.newSingleThreadExecutor(),
        ) {
            override fun search(topicSearchTerm: String): TopicSearchOutcome = outcome
        }
        return TopicSearchController(service)
    }

    @Test
    fun `rejects a request without a topic search term and never calls the service`() {
        var searched = false
        val service = object : TopicSearchService(
            europeanaClient = throwingEuropeanaClient,
            wikidataContextClient = emptyWikidataContext,
            executor = Executors.newSingleThreadExecutor(),
        ) {
            override fun search(topicSearchTerm: String): TopicSearchOutcome {
                searched = true
                return TopicSearchOutcome.EuropeanaOutage
            }
        }
        val controller = TopicSearchController(service)

        val response = controller.search(TopicSearchApiRequest(topicSearchTerm = "   "))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(!searched)
    }

    @Test
    fun `maps a ready outcome to a READY response with records and context`() {
        val answer = TopicSearchAnswer(
            topicSearchTerm = "watersnood van 1916",
            records = listOf(
                TopicSearchRecord(
                    title = "Watersnood van 1916",
                    dataProvider = "Noord-Hollands Archief",
                    license = TopicSearchLicenseBadge("Publiek domein", "https://example.test/rights"),
                    sourceUrl = "https://archief.example/1",
                ),
            ),
            context = TopicSearchContext("Watersnood van 1916", "overstroming"),
            checkedAt = checkedAt,
        )
        val controller = controllerReturning(TopicSearchOutcome.Ready(answer))

        val response = controller.search(TopicSearchApiRequest(topicSearchTerm = "watersnood van 1916"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as TopicSearchApiResponse
        assertEquals("READY", body.status)
        assertEquals(1, body.answer?.records?.size)
        assertEquals("Publiek domein", body.answer?.records?.first()?.licenseText)
        assertEquals("Watersnood van 1916", body.answer?.context?.label)
    }

    @Test
    fun `maps an empty outcome with refinement suggestions`() {
        val controller = controllerReturning(TopicSearchOutcome.Empty(checkedAt, listOf("Gebruik een breder trefwoord.")))

        val response = controller.search(TopicSearchApiRequest(topicSearchTerm = "onbekend"))

        val body = response.body as TopicSearchApiResponse
        assertEquals("EMPTY", body.status)
        assertEquals(listOf("Gebruik een breder trefwoord."), body.refinementSuggestions)
    }

    @Test
    fun `maps a europeana outage`() {
        val controller = controllerReturning(TopicSearchOutcome.EuropeanaOutage)

        val response = controller.search(TopicSearchApiRequest(topicSearchTerm = "watersnood van 1916"))

        val body = response.body as TopicSearchApiResponse
        assertEquals("OUTAGE", body.status)
    }
}
