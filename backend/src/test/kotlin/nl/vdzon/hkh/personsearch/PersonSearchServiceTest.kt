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

    private fun newExecutor(threads: Int = 2): ExecutorService = Executors.newFixedThreadPool(threads).also { executors += it }

    private fun service(
        client: ArchivesOpenSearchClient,
        context: PersonSearchContextSource = FakeContextSource(),
        deadlineMillis: Long = PERSON_SEARCH_DEADLINE_MILLIS,
        jobStore: PersonSearchJobStore = testJobStore(fixedClock),
        executor: ExecutorService = newExecutor(),
    ) = PersonSearchService(
        archivesClient = client,
        contextSource = context,
        answerBuilder = PersonSearchAnswerBuilder(),
        jobStore = jobStore,
        executor = executor,
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

        assertEquals(PersonSearchStatus.READY, result.status)
        val answer = result.payload!!.answer!!
        assertTrue(answer.disclaimer.contains("geen volledig levensverhaal"))
        assertEquals(2, answer.connections.size)
    }

    @Test
    fun `zero search results yield no evidence without calling show`() {
        val client = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Success(0, emptyList()))

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Onbekende Persoon"))

        assertEquals(PersonSearchStatus.NO_EVIDENCE, result.status)
        assertEquals(0, client.showCalls)
    }

    @Test
    fun `more than 100 results yield partial without calling show`() {
        val client = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Success(101, emptyList()))

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.PARTIAL, result.status)
        assertTrue(result.payload!!.refinementMessage!!.isNotBlank())
        assertEquals(0, client.showCalls)
    }

    @Test
    fun `a failed search yields failed`() {
        val client = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Failure)

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.FAILED, result.status)
    }

    @Test
    fun `a failed show for a required candidate yields failed with no archive claims`() {
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "X"))),
            showResults = mapOf("X" to ArchivesShowOutcome.Failure),
        )

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.FAILED, result.status)
    }

    @Test
    fun `partial show failures among ten or more candidates still yield ready with only the successful records`() {
        val candidateIds = (1..10).map { "CANDIDATE-$it" }
        val failedIds = setOf("CANDIDATE-2", "CANDIDATE-5", "CANDIDATE-9")
        val showResults = candidateIds.associateWith { id ->
            if (id in failedIds) {
                ArchivesShowOutcome.Failure
            } else {
                ArchivesShowOutcome.Success(nicolaasRecord.copy(identifier = id))
            }
        }
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(
                numberFound = candidateIds.size,
                results = candidateIds.map { ArchivesSearchResultItem("nha", it) },
            ),
            showResults = showResults,
        )

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.READY, result.status)
        val answer = result.payload!!.answer!!
        assertEquals(candidateIds.size - failedIds.size, answer.sources.size)
        assertTrue(
            answer.sources.none { source -> failedIds.any { source.openArchivesLink.endsWith(it) } },
        )
        assertTrue(
            answer.disclaimer.contains(
                "${failedIds.size} van de ${candidateIds.size} gevonden kandidaten konden niet worden " +
                    "geverifieerd en zijn buiten beschouwing gelaten.",
            ),
        )
    }

    @Test
    fun `show failures for every candidate among ten or more still yield failed`() {
        val candidateIds = (1..10).map { "CANDIDATE-$it" }
        val showResults = candidateIds.associateWith { ArchivesShowOutcome.Failure }
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(
                numberFound = candidateIds.size,
                results = candidateIds.map { ArchivesSearchResultItem("nha", it) },
            ),
            showResults = showResults,
        )

        val result = service(client).submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.FAILED, result.status)
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
        val jobStore = testJobStore(fixedClock)
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
        val jobStore = testJobStore(fixedClock)
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
        val jobStore = testJobStore(fixedClock)
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
        val jobStore = testJobStore(fixedClock)
        val personSearchService = service(client, deadlineMillis = 50, jobStore = jobStore)

        val result = personSearchService.submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        assertEquals(PersonSearchStatus.RUNNING, result.status)
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))
        releaseLatch.countDown()

        await(5000) { jobStore.findByIdForSession(result.jobId, "session-1")?.status == PersonSearchStatus.READY }
        assertEquals(PersonSearchStatus.READY, jobStore.findByIdForSession(result.jobId, "session-1")?.status)
    }

    @Test
    fun `a job that has not yet reached the executor is QUEUED`() {
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val occupier = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(0, emptyList()),
            onSearch = {
                startedLatch.countDown()
                assertTrue(releaseLatch.await(5, TimeUnit.SECONDS))
            },
        )
        val realClient = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Success(0, emptyList()))
        val jobStore = testJobStore(fixedClock)
        val singleThreadExecutor = newExecutor(threads = 1)
        val occupierService = service(occupier, deadlineMillis = 20, jobStore = jobStore, executor = singleThreadExecutor)
        val realService = service(realClient, deadlineMillis = 20, jobStore = jobStore, executor = singleThreadExecutor)

        occupierService.submit("session-1", PersonSearchRequest(recognizedName = "Bezet"))
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))

        val queuedResult = realService.submit("session-1", PersonSearchRequest(recognizedName = "Wachtend"))
        assertEquals(PersonSearchStatus.QUEUED, queuedResult.status)

        releaseLatch.countDown()
    }

    @Test
    fun `open archieven and wikidata consultation statuses succeed for a supported answer`() {
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "X"))),
            showResults = mapOf("X" to ArchivesShowOutcome.Success(nicolaasRecord)),
        )
        val jobStore = testJobStore(fixedClock)
        val personSearchService = service(
            client,
            context = FakeContextSource(PersonSearchWikidataContext("Heemskerk", null)),
            jobStore = jobStore,
        )

        val result = personSearchService.submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        val job = jobStore.findByIdForSession(result.jobId, "session-1")!!
        assertEquals(PersonSearchSourceConsultationStatus.SUCCEEDED, job.openArchievenStatus)
        assertEquals(PersonSearchSourceConsultationStatus.SUCCEEDED, job.wikidataStatus)
    }

    @Test
    fun `open archieven consultation status fails when the search fails`() {
        val client = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Failure)
        val jobStore = testJobStore(fixedClock)
        val personSearchService = service(client, jobStore = jobStore)

        val result = personSearchService.submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))

        val job = jobStore.findByIdForSession(result.jobId, "session-1")!!
        assertEquals(PersonSearchSourceConsultationStatus.FAILED, job.openArchievenStatus)
    }

    @Test
    fun `cancelling a queued job prevents the archive from ever being contacted`() {
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val occupier = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(0, emptyList()),
            onSearch = {
                startedLatch.countDown()
                assertTrue(releaseLatch.await(5, TimeUnit.SECONDS))
            },
        )
        val realClient = FakeArchivesOpenSearchClient(ArchivesSearchOutcome.Success(0, emptyList()))
        val jobStore = testJobStore(fixedClock)
        val singleThreadExecutor = newExecutor(threads = 1)
        val occupierService = service(occupier, deadlineMillis = 20, jobStore = jobStore, executor = singleThreadExecutor)
        val realService = service(realClient, deadlineMillis = 20, jobStore = jobStore, executor = singleThreadExecutor)

        occupierService.submit("session-1", PersonSearchRequest(recognizedName = "Bezet"))
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))
        val queued = realService.submit("session-1", PersonSearchRequest(recognizedName = "Wachtend"))
        assertEquals(PersonSearchStatus.QUEUED, queued.status)

        val cancelled = realService.cancel(queued.jobId, "session-1")
        assertEquals(PersonSearchStatus.CANCELLED, cancelled?.job?.status)

        releaseLatch.countDown()
        Thread.sleep(300)

        assertEquals(0, realClient.searchCalls)
        assertEquals(PersonSearchStatus.CANCELLED, jobStore.findByIdForSession(queued.jobId, "session-1")?.status)
    }

    @Test
    fun `cancelling mid-execution stops the show calls that would otherwise follow`() {
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val jobStore = testJobStore(fixedClock)
        val client = FakeArchivesOpenSearchClient(
            searchResult = ArchivesSearchOutcome.Success(
                2,
                listOf(ArchivesSearchResultItem("nha", "X"), ArchivesSearchResultItem("nha", "Y")),
            ),
            showResults = mapOf(
                "X" to ArchivesShowOutcome.Success(nicolaasRecord),
                "Y" to ArchivesShowOutcome.Success(nicolaasRecord.copy(identifier = "Y")),
            ),
            onSearch = {
                startedLatch.countDown()
                assertTrue(releaseLatch.await(5, TimeUnit.SECONDS))
            },
        )
        val personSearchService = service(client, deadlineMillis = 20, jobStore = jobStore)

        val submitted = personSearchService.submit("session-1", PersonSearchRequest(recognizedName = "Jansen"))
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))
        personSearchService.cancel(submitted.jobId, "session-1")
        releaseLatch.countDown()

        await(2000) { jobStore.findByIdForSession(submitted.jobId, "session-1")?.status == PersonSearchStatus.CANCELLED }
        Thread.sleep(200)

        assertEquals(0, client.showCalls)
        assertEquals(PersonSearchStatus.CANCELLED, jobStore.findByIdForSession(submitted.jobId, "session-1")?.status)
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
    }
}
