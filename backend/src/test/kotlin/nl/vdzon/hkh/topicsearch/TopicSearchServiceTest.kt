package nl.vdzon.hkh.topicsearch

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopicSearchServiceTest {

    private val executors = mutableListOf<ExecutorService>()
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-09-10T10:00:00Z"), ZoneOffset.UTC)

    @AfterTest
    fun tearDown() {
        executors.forEach { it.shutdownNow() }
    }

    private val record = TopicSearchRecord(
        title = "Watersnood van 1916",
        dataProvider = "Noord-Hollands Archief",
        license = TopicSearchLicenseBadge("Publiek domein", "https://creativecommons.org/publicdomain/mark/1.0/"),
        sourceUrl = "https://archief.example/1",
    )

    private class FakeEuropeanaClient(private val outcomeFor: (String) -> EuropeanaSearchOutcome) : ArchivesEuropeanaClient {
        var callCount = 0
        override fun search(query: String): EuropeanaSearchOutcome {
            callCount++
            return outcomeFor(query)
        }
    }

    private class SlowEuropeanaClient(private val delayMillis: Long) : ArchivesEuropeanaClient {
        override fun search(query: String): EuropeanaSearchOutcome {
            Thread.sleep(delayMillis)
            return EuropeanaSearchOutcome.Success(emptyList())
        }
    }

    private fun service(
        europeanaClient: ArchivesEuropeanaClient,
        wikidataContextClient: TopicSearchWikidataContextSource = TopicSearchWikidataContextSource { null },
        deadlineMillis: Long = TOPIC_SEARCH_DEADLINE_MILLIS,
    ): TopicSearchService {
        val executor = Executors.newFixedThreadPool(2).also { executors += it }
        return TopicSearchService(
            europeanaClient = europeanaClient,
            wikidataContextClient = wikidataContextClient,
            executor = executor,
            clock = fixedClock,
            deadlineMillis = deadlineMillis,
        )
    }

    @Test
    fun `at least one valid record yields a ready outcome with the context when applicable`() {
        val client = FakeEuropeanaClient { EuropeanaSearchOutcome.Success(listOf(record)) }
        val context = TopicSearchContext("Watersnood van 1916", "overstroming")

        val outcome = service(client, TopicSearchWikidataContextSource { context })
            .search("watersnood van 1916") as TopicSearchOutcome.Ready

        assertEquals(1, outcome.answer.records.size)
        assertEquals(context, outcome.answer.context)
        assertEquals(Instant.parse("2026-09-10T10:00:00Z"), outcome.answer.checkedAt)
    }

    @Test
    fun `zero valid records yields empty with refinement suggestions and no wikidata call`() {
        var wikidataCalled = false
        val client = FakeEuropeanaClient { EuropeanaSearchOutcome.Success(emptyList()) }

        val outcome = service(client, TopicSearchWikidataContextSource { wikidataCalled = true; null })
            .search("onbekend onderwerp") as TopicSearchOutcome.Empty

        assertTrue(outcome.refinementSuggestions.isNotEmpty())
        assertTrue(!wikidataCalled)
    }

    @Test
    fun `a europeana failure yields an outage without any claim`() {
        val client = FakeEuropeanaClient { EuropeanaSearchOutcome.Failure }

        val outcome = service(client).search("watersnood van 1916")

        assertEquals(TopicSearchOutcome.EuropeanaOutage, outcome)
    }

    @Test
    fun `a slow europeana response beyond the overall deadline is an outage`() {
        val outcome = service(SlowEuropeanaClient(400), deadlineMillis = 50).search("watersnood van 1916")

        assertEquals(TopicSearchOutcome.EuropeanaOutage, outcome)
    }

    @Test
    fun `a failing wikidata context never blocks the europeana result`() {
        val client = FakeEuropeanaClient { EuropeanaSearchOutcome.Success(listOf(record)) }
        val throwingContext = TopicSearchWikidataContextSource { throw RuntimeException("boom") }

        val outcome = service(client, throwingContext).search("watersnood van 1916") as TopicSearchOutcome.Ready

        assertNull(outcome.answer.context)
        assertEquals(1, outcome.answer.records.size)
    }

    @Test
    fun `a repeated identical query within the cache ttl is not fetched from europeana again`() {
        val client = FakeEuropeanaClient { EuropeanaSearchOutcome.Success(listOf(record)) }
        val service = service(client)

        service.search("watersnood van 1916")
        service.search("watersnood van 1916")

        assertEquals(1, client.callCount)
    }
}
