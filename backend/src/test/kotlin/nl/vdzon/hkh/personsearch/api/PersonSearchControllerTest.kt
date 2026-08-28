package nl.vdzon.hkh.personsearch.api

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
import nl.vdzon.hkh.personsearch.PersonSearchAnswerBuilder
import nl.vdzon.hkh.personsearch.PersonSearchContextSource
import nl.vdzon.hkh.personsearch.PersonSearchJobStore
import nl.vdzon.hkh.personsearch.PersonSearchService
import nl.vdzon.hkh.personsearch.PersonSearchSessionResolver
import nl.vdzon.hkh.personsearch.PersonSearchWikidataContext
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

    private fun controller(client: ArchivesOpenSearchClient): PersonSearchController {
        val executor = Executors.newFixedThreadPool(2).also { executors += it }
        val service = PersonSearchService(
            archivesClient = client,
            contextSource = PersonSearchContextSource { PersonSearchWikidataContext("Heemskerk", "gemeente") },
            answerBuilder = PersonSearchAnswerBuilder(),
            jobStore = PersonSearchJobStore(),
            executor = executor,
            clock = Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC),
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
        assertEquals("SUPPORTED_ANSWER", body.status)
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

    private fun fakeClient(
        searchResult: ArchivesSearchOutcome,
        showResults: Map<String, ArchivesShowOutcome> = emptyMap(),
    ) = object : ArchivesOpenSearchClient {
        override fun search(name: String, start: Int, numberShow: Int) = searchResult
        override fun show(archiveCode: String, identifier: String) = showResults[identifier] ?: ArchivesShowOutcome.Failure
    }
}
