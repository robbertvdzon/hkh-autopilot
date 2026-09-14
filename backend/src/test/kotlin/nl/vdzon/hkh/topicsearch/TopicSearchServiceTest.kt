package nl.vdzon.hkh.topicsearch

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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

    /** Verzetbare klok, zodat een cachehit op een later moment toetsbaar is. */
    private class MutableTopicSearchClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }

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
    fun `europeana query omits van before a year but keeps the heemskerk constraint`() {
        assertEquals("watersnood 1916 AND Heemskerk", buildEuropeanaTopicQuery("watersnood van 1916"))
        assertEquals("watersnood 1916? AND Heemskerk", buildEuropeanaTopicQuery("watersnood VAN 1916?"))
    }

    @Test
    fun `europeana query preserves meaningful articles and name particles`() {
        assertEquals("De Stijl AND Heemskerk", buildEuropeanaTopicQuery("De Stijl"))
        assertEquals("Vincent van Gogh AND Heemskerk", buildEuropeanaTopicQuery("Vincent van Gogh"))
        assertEquals("van de AND Heemskerk", buildEuropeanaTopicQuery("van de"))
    }

    @Test
    fun `semantically different terms cannot share a cache key through stop word removal`() {
        val client = FakeEuropeanaClient { EuropeanaSearchOutcome.Success(emptyList()) }
        val service = service(client)

        service.search("De Stijl")
        service.search("Stijl")
        service.search("Vincent van Gogh")
        service.search("Vincent Gogh")

        assertEquals(4, client.callCount)
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

    @Test
    fun `a cache hit keeps the original consultation moment instead of presenting itself as current`() {
        val clock = MutableTopicSearchClock(Instant.parse("2026-09-10T10:00:00Z"))
        val client = FakeEuropeanaClient { EuropeanaSearchOutcome.Success(listOf(record)) }
        val executor = Executors.newFixedThreadPool(2).also { executors += it }
        val service = TopicSearchService(
            europeanaClient = client,
            wikidataContextClient = TopicSearchWikidataContextSource { null },
            executor = executor,
            clock = clock,
            deadlineMillis = TOPIC_SEARCH_DEADLINE_MILLIS,
        )

        val first = service.search("watersnood van 1916") as TopicSearchOutcome.Ready
        clock.instant = Instant.parse("2026-09-10T10:20:00Z")
        val second = service.search("watersnood van 1916") as TopicSearchOutcome.Ready

        assertEquals(1, client.callCount)
        assertEquals(Instant.parse("2026-09-10T10:00:00Z"), first.answer.checkedAt)
        assertEquals(first.answer.checkedAt, second.answer.checkedAt)
    }
}
