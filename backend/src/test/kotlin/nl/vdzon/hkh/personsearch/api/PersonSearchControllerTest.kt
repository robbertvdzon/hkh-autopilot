package nl.vdzon.hkh.personsearch.api

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.vdzon.hkh.personsearch.ArchivesOpenSearchClient
import nl.vdzon.hkh.personsearch.ArchivesRelation
import nl.vdzon.hkh.personsearch.ArchivesSearchOutcome
import nl.vdzon.hkh.personsearch.ArchivesSearchResultItem
import nl.vdzon.hkh.personsearch.ArchivesShowOutcome
import nl.vdzon.hkh.personsearch.ArchivesShowRecord
import nl.vdzon.hkh.personsearch.PERSON_SEARCH_DEADLINE_MILLIS
import nl.vdzon.hkh.personsearch.PersonSearchAnswerBuilder
import nl.vdzon.hkh.personsearch.PersonSearchContextSource
import nl.vdzon.hkh.personsearch.PersonSearchJobStore
import nl.vdzon.hkh.personsearch.PersonSearchService
import nl.vdzon.hkh.personsearch.PersonSearchSessionResolver
import nl.vdzon.hkh.personsearch.PersonSearchWikidataContext
import nl.vdzon.hkh.personsearch.testJobStore
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class PersonSearchControllerTest {

    private val executors = mutableListOf<ExecutorService>()

    @AfterTest
    fun shutdownExecutors() {
        executors.forEach { it.shutdownNow() }
    }

    private val nicolaasRecord = ArchivesShowRecord(
        archiveCode = "nha",
        identifier = "002ED0F3-F08C-4223-A5EA-BA385D04336E",
        personName = "Nicolaas Jacobus Sinnige",
        eventType = "Geboorte",
        eventDate = "1878-07-25",
        eventPlace = "Heemskerk",
        relations = listOf(ArchivesRelation("Vader", "Pieter Sinnige"), ArchivesRelation("Moeder", "Anna Geertruida Eenhuis")),
        institution = "Noord-Hollands Archief",
        sourceType = "Geboorteakte",
        archiveNumber = "123",
        registerNumber = "4",
        deedNumber = "56",
        recordNumber = "789",
        digitalOriginalUrl = null,
    )

    private fun controller(
        client: ArchivesOpenSearchClient,
        jobStore: PersonSearchJobStore = testJobStore(),
        deadlineMillis: Long = PERSON_SEARCH_DEADLINE_MILLIS,
    ): PersonSearchController {
        val executor = Executors.newFixedThreadPool(2).also { executors += it }
        val service = PersonSearchService(
            archivesClient = client,
            contextSource = PersonSearchContextSource { PersonSearchWikidataContext("Heemskerk", "gemeente") },
            answerBuilder = PersonSearchAnswerBuilder(),
            jobStore = jobStore,
            executor = executor,
            clock = Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC),
            deadlineMillis = deadlineMillis,
        )
        return PersonSearchController(PersonSearchSessionResolver(), service)
    }

    @Test
    fun `rejects a request without a recognized name and never contacts the archive`() {
        var searchCalled = false
        val client = object : ArchivesOpenSearchClient {
            override fun search(name: String, start: Int, numberShow: Int): ArchivesSearchOutcome {
                searchCalled = true
                return ArchivesSearchOutcome.Success(0, emptyList())
            }

            override fun show(archiveCode: String, identifier: String) = ArchivesShowOutcome.Failure
        }

        val response = controller(client).submit(
            PersonSearchApiRequest(recognizedName = "  "),
            MockHttpServletRequest(),
            MockHttpServletResponse(),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(!searchCalled)
    }

    @Test
    fun `issues a session cookie and returns the controlled Nicolaas Jacobus Sinnige answer`() {
        val client = fakeClient(
            ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "002ED0F3-F08C-4223-A5EA-BA385D04336E"))),
            mapOf("002ED0F3-F08C-4223-A5EA-BA385D04336E" to ArchivesShowOutcome.Success(nicolaasRecord)),
        )
        val httpRequest = MockHttpServletRequest()
        val httpResponse = MockHttpServletResponse()

        val response = controller(client).submit(
            PersonSearchApiRequest(recognizedName = "Nicolaas Jacobus Sinnige", yearOrPeriod = "1878"),
            httpRequest,
            httpResponse,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as PersonSearchApiResponse
        assertEquals("READY", body.status)
        assertTrue(body.answer!!.disclaimer.contains("geen volledig levensverhaal"))
        assertEquals(2, body.answer!!.connections.size)
        assertEquals("Heemskerk", body.context?.label)
        assertTrue(httpResponse.getHeader("Set-Cookie")?.contains("hkh_person_search_session") == true)
    }

    @Test
    fun `a session cookie already present on the request is reused rather than replaced`() {
        val client = fakeClient(ArchivesSearchOutcome.Success(0, emptyList()))
        val httpRequest = MockHttpServletRequest()
        httpRequest.setCookies(jakarta.servlet.http.Cookie("hkh_person_search_session", "existing-session"))
        val httpResponse = MockHttpServletResponse()

        controller(client).submit(PersonSearchApiRequest(recognizedName = "Jansen"), httpRequest, httpResponse)

        assertEquals(null, httpResponse.getHeader("Set-Cookie"))
    }

    @Test
    fun `status endpoint reports progress and only reveals the answer once terminal, using only the ordinary executor`() {
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val client = fakeClient(
            ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "002ED0F3-F08C-4223-A5EA-BA385D04336E"))),
            mapOf("002ED0F3-F08C-4223-A5EA-BA385D04336E" to ArchivesShowOutcome.Success(nicolaasRecord)),
            onSearch = {
                startedLatch.countDown()
                assertTrue(releaseLatch.await(5, TimeUnit.SECONDS))
            },
        )
        val personSearchController = controller(client, deadlineMillis = 50)
        val cookieRequest = MockHttpServletRequest()
        val cookieResponse = MockHttpServletResponse()

        val submitResponse = personSearchController.submit(
            PersonSearchApiRequest(recognizedName = "Nicolaas Jacobus Sinnige", yearOrPeriod = "1878"),
            cookieRequest,
            cookieResponse,
        )
        val jobId = (submitResponse.body as PersonSearchApiResponse).jobId
        val sessionId = extractSessionCookie(cookieResponse)
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))

        val runningBody = personSearchController.status(jobId, requestWithCookie(sessionId), MockHttpServletResponse()).body
            as PersonSearchStatusResponse
        assertTrue(runningBody.status == "QUEUED" || runningBody.status == "RUNNING")
        assertEquals(null, runningBody.answer)

        releaseLatch.countDown()
        val readyBody = awaitStatus(personSearchController, jobId, sessionId) { it.status == "READY" }
        assertTrue(readyBody.answer!!.disclaimer.contains("geen volledig levensverhaal"))
        assertTrue(readyBody.openArchievenStatus == "SUCCEEDED")
        assertTrue(readyBody.wikidataStatus == "SUCCEEDED")
    }

    @Test
    fun `a status request from another session behaves as if the job does not exist`() {
        val client = fakeClient(ArchivesSearchOutcome.Success(0, emptyList()))
        val personSearchController = controller(client)
        val cookieRequest = MockHttpServletRequest()
        val cookieResponse = MockHttpServletResponse()

        val submitResponse = personSearchController.submit(
            PersonSearchApiRequest(recognizedName = "Jansen"),
            cookieRequest,
            cookieResponse,
        )
        val jobId = (submitResponse.body as PersonSearchApiResponse).jobId

        val otherSessionResponse = personSearchController.status(jobId, requestWithCookie("other-session"), MockHttpServletResponse())

        assertEquals(HttpStatus.NOT_FOUND, otherSessionResponse.statusCode)
    }

    @Test
    fun `cancel sets CANCELLED, wipes the payload and blocks further status from revealing an answer`() {
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        var showCalled = false
        val client = fakeClient(
            ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "002ED0F3-F08C-4223-A5EA-BA385D04336E"))),
            mapOf("002ED0F3-F08C-4223-A5EA-BA385D04336E" to ArchivesShowOutcome.Success(nicolaasRecord)),
            onSearch = {
                startedLatch.countDown()
                assertTrue(releaseLatch.await(5, TimeUnit.SECONDS))
            },
            onShow = { showCalled = true },
        )
        val personSearchController = controller(client, deadlineMillis = 20)
        val cookieRequest = MockHttpServletRequest()
        val cookieResponse = MockHttpServletResponse()

        val submitResponse = personSearchController.submit(
            PersonSearchApiRequest(recognizedName = "Jansen"),
            cookieRequest,
            cookieResponse,
        )
        val jobId = (submitResponse.body as PersonSearchApiResponse).jobId
        val sessionId = extractSessionCookie(cookieResponse)
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))

        val cancelResponse = personSearchController.cancel(jobId, requestWithCookie(sessionId), MockHttpServletResponse())
        assertEquals("CANCELLED", (cancelResponse.body as PersonSearchStatusResponse).status)

        releaseLatch.countDown()
        Thread.sleep(300)

        assertTrue(!showCalled)
        val finalStatus = personSearchController.status(jobId, requestWithCookie(sessionId), MockHttpServletResponse()).body
            as PersonSearchStatusResponse
        assertEquals("CANCELLED", finalStatus.status)
        assertEquals(null, finalStatus.answer)
    }

    @Test
    fun `session indicator counts running and ready-unopened jobs, and open marks a READY job opened`() {
        val client = fakeClient(
            ArchivesSearchOutcome.Success(1, listOf(ArchivesSearchResultItem("nha", "002ED0F3-F08C-4223-A5EA-BA385D04336E"))),
            mapOf("002ED0F3-F08C-4223-A5EA-BA385D04336E" to ArchivesShowOutcome.Success(nicolaasRecord)),
        )
        val personSearchController = controller(client)
        val cookieRequest = MockHttpServletRequest()
        val cookieResponse = MockHttpServletResponse()

        val submitResponse = personSearchController.submit(
            PersonSearchApiRequest(recognizedName = "Jansen"),
            cookieRequest,
            cookieResponse,
        )
        val jobId = (submitResponse.body as PersonSearchApiResponse).jobId
        val sessionId = extractSessionCookie(cookieResponse)

        val beforeOpen = personSearchController.session(requestWithCookie(sessionId), MockHttpServletResponse()).body
            as PersonSearchSessionIndicatorResponse
        assertEquals(0, beforeOpen.runningCount)
        assertEquals(1, beforeOpen.readyUnopenedCount)

        personSearchController.open(jobId, requestWithCookie(sessionId), MockHttpServletResponse())

        val afterOpen = personSearchController.session(requestWithCookie(sessionId), MockHttpServletResponse()).body
            as PersonSearchSessionIndicatorResponse
        assertEquals(0, afterOpen.readyUnopenedCount)
    }

    private fun extractSessionCookie(response: MockHttpServletResponse): String {
        val header = response.getHeader("Set-Cookie")!!
        return header.substringAfter("hkh_person_search_session=").substringBefore(";")
    }

    private fun requestWithCookie(sessionId: String): MockHttpServletRequest {
        val request = MockHttpServletRequest()
        request.setCookies(jakarta.servlet.http.Cookie("hkh_person_search_session", sessionId))
        return request
    }

    private fun awaitStatus(
        controller: PersonSearchController,
        jobId: String,
        sessionId: String,
        condition: (PersonSearchStatusResponse) -> Boolean,
    ): PersonSearchStatusResponse {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            val body = controller.status(jobId, requestWithCookie(sessionId), MockHttpServletResponse()).body as PersonSearchStatusResponse
            if (condition(body)) return body
            Thread.sleep(10)
        }
        error("timed out waiting for status condition")
    }

    private fun fakeClient(
        searchResult: ArchivesSearchOutcome,
        showResults: Map<String, ArchivesShowOutcome> = emptyMap(),
        onSearch: () -> Unit = {},
        onShow: () -> Unit = {},
    ) = object : ArchivesOpenSearchClient {
        override fun search(name: String, start: Int, numberShow: Int): ArchivesSearchOutcome {
            onSearch()
            return searchResult
        }
        override fun show(archiveCode: String, identifier: String): ArchivesShowOutcome {
            onShow()
            return showResults[identifier] ?: ArchivesShowOutcome.Failure
        }
    }
}
