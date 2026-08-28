package nl.vdzon.hkh.personsearch

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC)

private val nicolaasRecord = ArchivesShowRecord(
    archiveCode = "nha",
    identifier = "002ED0F3-F08C-4223-A5EA-BA385D04336E",
    personName = "Nicolaas Jacobus Sinnige",
    eventType = "Geboorte",
    eventDate = "1878-07-25",
    eventPlace = "Heemskerk",
    relations = listOf(
        ArchivesRelation("Vader", "Pieter Sinnige"),
        ArchivesRelation("Moeder", "Anna Geertruida Eenhuis"),
    ),
    institution = "Noord-Hollands Archief",
    sourceType = "Geboorteakte",
    archiveNumber = "123",
    registerNumber = "4",
    deedNumber = "56",
    recordNumber = "789",
    digitalOriginalUrl = null,
)

private class FakeArchivesOpenSearchClient(
    private val searchResult: ArchivesSearchOutcome,
    private val showResults: Map<String, ArchivesShowOutcome> = emptyMap(),
    private val onSearch: () -> Unit = {},
    private val onShow: () -> Unit = {},
) : ArchivesOpenSearchClient {
    var searchCalls = 0
        private set
    var showCalls = 0
        private set

    override fun search(name: String, start: Int, numberShow: Int): ArchivesSearchOutcome {
        searchCalls++
        onSearch()
        return searchResult
    }

    override fun show(archiveCode: String, identifier: String): ArchivesShowOutcome {
        showCalls++
        onShow()
        return showResults[identifier] ?: ArchivesShowOutcome.Failure
    }
}

private class FakeContextSource(private val context: PersonSearchWikidataContext? = null) : PersonSearchContextSource {
    override fun fetchContext(place: String): PersonSearchWikidataContext? = context
}

class PersonSearchServiceTest {

    private val executors = mutableListOf<ExecutorService>()

    @AfterTest
    fun shutdownExecutors() {
        executors.forEach { it.shutdownNow() }
    }

    private fun newExecutor(): ExecutorService = Executors.newFixedThreadPool(2).also { executors += it }

    private fun service(
        client: ArchivesOpenSearchClient,
        context: PersonSearchContextSource = FakeContextSource(),
        deadlineMillis: Long = PERSON_SEARCH_DEADLINE_MILLIS,
        jobStore: PersonSearchJobStore = PersonSearchJobStore(),
    ) = PersonSearchService(
        archivesClient = client,
        contextSource = context,
        answerBuilder = PersonSearchAnswerBuilder(),
        jobStore = jobStore,
        executor = newExecutor(),
        clock = fixedClock,
        deadlineMillis = deadlineMillis,
    )

    @Test
    fun `a supported answer is returned within budget for the controlled Nicolaas Jacobus Sinnige example`() {
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(
                numberFound = 1,
                results = listOf(ArchivesSearchResultItem("nha", "002ED0F3-F08C-4223-A5EA-BA385D04336E")),
            ),
            showResults = mapOf("002ED0F3-F08C-4223-A5EA-BA385D04336E" to ArchivesShowOutcome.Success(nicolaasRecord)),
        )

        val result = service(client).submit(
            "session-1",
            PersonSearchRequest(recognizedName = "Nicolaas Jacobus Sinnige", yearOrPeriod = "1878"),
        )

        assertEquals(PersonSearchStatus.SUPPORTED_ANSWER, result.status)
        val answer = (result.outcome as PersonSearchOutcome.SupportedAnswer).answer
        assertTrue(answer.disclaimer.contains("geen volledig levensverhaal"))
        assertEquals(2, answer.connections.size)
    }

    @Test
    fun `zero search results yield no results without calling show`() {
        val client = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Success(0, emptyList()))

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Onbekende Persoon"))

        assertEquals(PersonSearchStatus.NO_RESULTS, result.status)
        assertEquals(0, client.showCalls)
    }

    @Test
    fun `more than 100 results yield partial without calling show`() {
        val client = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Success(101, emptyList()))

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.PARTIAL, result.status)
        assertTrue((result.outcome as PersonSearchOutcome.Partial).refinementMessage.isNotBlank())
        assertEquals(0, client.showCalls)
    }

    @Test
    fun `a failed search yields source outage`() {
        val client = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Failure)

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.SOURCE_OUTAGE, result.status)
    }

    @Test
    fun `a failed show for a required candidate yields source outage with no archive claims`() {
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "X"))),
            showResults = mapOf("X" to ArchivesShowOutcome.Failure),
        )

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.SOURCE_OUTAGE, result.status)
    }

    @Test
    fun `results are deduplicated on archive_code and identifier before fetching show`() {
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(
                2,
                listOf(ArchivesSearchResultItem("nha", "X"), ArchivesSearchResultItem("nha", "X")),
            ),
            showResults = mapOf("X" to ArchivesShowOutcome.Success(nicolaasRecord)),
        )

        service(client).submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(1, client.showCalls)
    }

    @Test
    fun `a repeated submission with the same idempotency key does not consult the source again`() {
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "X"))),
            showResults = mapOf("X" to ArchivesShowOutcome.Success(nicolaasRecord)),
        )
        val jobStore = PersonSearchJobStore()
        val personSearchService = service(client, jobStore = jobStore)
        val request = PersonSearchRequest(recognizedName = "Nicolaas Jacobus Sinnige", yearOrPeriod = "1878")

        val first = personSearchService.submit("session-1", request)
        val second = personSearchService.submit("session-1", request)

        assertEquals(first.jobId, second.jobId)
        assertEquals(1, client.searchCalls)
    }

    @Test
    fun `concurrent submissions with the same idempotency key trigger only one source consultation`() {
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "X"))),
            showResults = mapOf("X" to ArchivesShowOutcome.Success(nicolaasRecord)),
        )
        val jobStore = PersonSearchJobStore()
        val personSearchService = service(client, jobStore = jobStore)
        val request = PersonSearchRequest(recognizedName = "Nicolaas Jacobus Sinnige", yearOrPeriod = "1878")
        val threadCount = 16
        val startBarrier = CyclicBarrier(threadCount)
        val submitterPool = Executors.newFixedThreadPool(threadCount).also { executors += it }

        val futures = (0 until threadCount).map {
            submitterPool.submit<PersonSearchSubmitResult> {
                startBarrier.await(5, TimeUnit.SECONDS)
                personSearchService.submit("session-1", request)
            }
        }
        val results = futures.map { it.get(5, TimeUnit.SECONDS) }

        assertEquals(1, results.map { it.jobId }.toSet().size)
        assertEquals(1, client.searchCalls)
    }

    @Test
    fun `a different session gets a different job even for an identical question`() {
        val client = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Success(0, emptyList()))
        val jobStore = PersonSearchJobStore()
        val personSearchService = service(client, jobStore = jobStore)
        val request = PersonSearchRequest(recognizedName = "Jansen")

        val first = personSearchService.submit("session-1", request)
        val second = personSearchService.submit("session-2", request)

        assertTrue(first.jobId != second.jobId)
        assertNull(jobStore.findByIdForSession(first.jobId, "session-2"))
        assertNotNull(jobStore.findByIdForSession(first.jobId, "session-1"))
    }

    @Test
    fun `a job that exceeds the two second budget returns running without cancelling the background work`() {
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "X"))),
            showResults = mapOf("X" to ArchivesShowOutcome.Success(nicolaasRecord)),
            onSearch = {
                startedLatch.countDown()
                assertTrue(releaseLatch.await(5, TimeUnit.SECONDS))
            },
        )
        val jobStore = PersonSearchJobStore()
        val personSearchService = service(client, deadlineMillis = 50, jobStore = jobStore)

        val result = personSearchService.submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.RUNNING, result.status)
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))
        releaseLatch.countDown()

        await(5000) { jobStore.findByIdForSession(result.jobId, "session-1")?.status == PersonSearchStatus.SUPPORTED_ANSWER }
        assertEquals(
            PersonSearchStatus.SUPPORTED_ANSWER,
            jobStore.findByIdForSession(result.jobId, "session-1")?.status,
        )
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
    }
}
