package nl.vdzon.hkh.recordintake.api

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import nl.vdzon.hkh.externalverification.ArchiveFetchResult
import nl.vdzon.hkh.externalverification.ArchivesNlClient
import nl.vdzon.hkh.privacyclassification.DeceasedStatus
import nl.vdzon.hkh.recordintake.PRIVACY_CLASSIFICATION_BLOCKED_CODE
import nl.vdzon.hkh.recordintake.RECORD_INTAKE_EXTERNAL_LINK_STATUS_CONCEPT
import nl.vdzon.hkh.recordintake.RECORD_INTAKE_STATUS_INTERN_CONCEPT
import nl.vdzon.hkh.recordintake.RecordIntake
import nl.vdzon.hkh.recordintake.RecordIntakeExternalArchiveDataToStore
import nl.vdzon.hkh.recordintake.RecordIntakeExternalLink
import nl.vdzon.hkh.recordintake.RecordIntakeExternalLinkInput
import nl.vdzon.hkh.recordintake.RecordIntakeRecord
import nl.vdzon.hkh.recordintake.RecordIntakeService
import nl.vdzon.hkh.recordintake.RecordIntakeStore
import nl.vdzon.hkh.recordintake.RecordIntakeTokenIdentity
import nl.vdzon.hkh.recordintake.RecordIntakeTokenVerifier
import nl.vdzon.hkh.recordintake.RecordIntakeValidator
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class RecordIntakeControllerTest {

    private val validator = RecordIntakeValidator()

    @Test
    fun `stores exactly one intern concept record for a valid request`() {
        val store = RecordingStore()
        val controller = controller(store = store)

        val response = controller.intake(validRequest(), "Bearer valid-token")

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body as RecordIntakeResponse
        assertEquals(RECORD_INTAKE_STATUS_INTERN_CONCEPT, body.status)
        assertNull(body.externalLink)
        assertEquals(1, store.createdRecords.size)
        assertEquals(0, store.createdLinks.size)
    }

    @Test
    fun `creates the optional external link only when all three fields are valid`() {
        val store = RecordingStore()
        val controller = controller(store = store)
        val request = validRequest().copy(
            externalLink = RecordIntakeExternalLinkRequest(
                durableUrl = "https://noord-hollandsarchief.nl/record/1",
                linkRationale = "Zelfde herkomst.",
                uncertainty = "laag",
            ),
        )

        val response = controller.intake(request, "Bearer valid-token")

        val body = response.body as RecordIntakeResponse
        assertEquals(RECORD_INTAKE_EXTERNAL_LINK_STATUS_CONCEPT, body.externalLink?.status)
        assertEquals(1, store.createdLinks.size)
    }

    @Test
    fun `an incomplete external link is silently skipped without blocking the record`() {
        val store = RecordingStore()
        val controller = controller(store = store)
        val request = validRequest().copy(
            externalLink = RecordIntakeExternalLinkRequest(durableUrl = "https://example.org/record/1", linkRationale = null, uncertainty = "laag"),
        )

        val response = controller.intake(request, "Bearer valid-token")

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull((response.body as RecordIntakeResponse).externalLink)
        assertEquals(0, store.createdLinks.size)
    }

    @Test
    fun `rejects an incomplete request with machine readable field errors without storing anything`() {
        val store = RecordingStore()
        val controller = controller(store = store)

        val response = controller.intake(validRequest().copy(localIdentifier = null), "Bearer valid-token")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as RecordIntakeFieldErrorsResponse
        assertEquals(listOf("localIdentifier"), body.fieldErrors)
        assertEquals(0, store.createdRecords.size)
    }

    @Test
    fun `blocks possible personal data without storing anything`() {
        val store = RecordingStore()
        val controller = controller(store = store)

        val response = controller.intake(
            validRequest().copy(privacyClassification = "mogelijk persoonsgegevens"),
            "Bearer valid-token",
        )

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals(
            RecordIntakeRejectionResponse(PRIVACY_CLASSIFICATION_BLOCKED_CODE),
            response.body,
        )
        assertEquals(0, store.createdRecords.size)
    }

    @Test
    fun `blocks personal data without storing anything`() {
        val store = RecordingStore()
        val controller = controller(store = store)

        val response = controller.intake(
            validRequest().copy(privacyClassification = "persoonsgegevens"),
            "Bearer valid-token",
        )

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals(0, store.createdRecords.size)
    }

    @Test
    fun `fails closed with 401 when no authorization header is present`() {
        val controller = controller()

        val exception = assertFailsWith<ResponseStatusException> { controller.intake(validRequest(), null) }
        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
    }

    @Test
    fun `fails closed with 401 when the token is rejected, before any validation runs`() {
        val controller = controller(
            tokenVerifier = RecordIntakeTokenVerifier { throw ResponseStatusException(HttpStatus.UNAUTHORIZED) },
        )

        val exception = assertFailsWith<ResponseStatusException> {
            controller.intake(validRequest().copy(localIdentifier = null), "Bearer invalid-token")
        }
        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
    }

    private fun validRequest() = RecordIntakeRequest(
        localIdentifier = "HKH-2026-0001",
        title = "Poldermolen De Eendracht",
        dating = "circa 1890",
        provenance = "Streekarchief Waterland",
        rightsStatus = "publicatie toegestaan",
        privacyClassification = "geen persoonsgegevens",
        accessUrl = "https://collectie.hkh-autopilot.local/records/hkh-2026-0001",
    )

    private fun controller(
        tokenVerifier: RecordIntakeTokenVerifier = RecordIntakeTokenVerifier { RecordIntakeTokenIdentity("collection-manager-1") },
        store: RecordIntakeStore = RecordingStore(),
        archivesNlClient: ArchivesNlClient = ArchivesNlClient { _, _, _ -> ArchiveFetchResult.NotFound },
    ) = RecordIntakeController(tokenVerifier, validator, RecordIntakeService(store, validator, archivesNlClient))

    private class RecordingStore : RecordIntakeStore {
        val createdRecords = mutableListOf<RecordIntake>()
        val createdLinks = mutableListOf<Pair<Long, RecordIntakeExternalLinkInput>>()
        private var nextId = 1L

        override fun create(intake: RecordIntake, archiveData: RecordIntakeExternalArchiveDataToStore?): RecordIntakeRecord {
            createdRecords += intake
            return RecordIntakeRecord(
                id = nextId++,
                localIdentifier = intake.localIdentifier!!.trim(),
                status = RECORD_INTAKE_STATUS_INTERN_CONCEPT,
                createdAt = Instant.now(),
                deceasedStatus = DeceasedStatus.parse(intake.deceasedStatus).wireValue,
                nextOfKinConfirmed = intake.nextOfKinConfirmed,
                archiveName = null,
                archiveBirthDate = null,
                archiveDeathDate = null,
                archiveLicense = null,
                archiveSourceUri = null,
                archiveFetchedAt = null,
            )
        }

        override fun createExternalLink(recordIntakeId: Long, link: RecordIntakeExternalLinkInput): RecordIntakeExternalLink {
            createdLinks += recordIntakeId to link
            return RecordIntakeExternalLink(
                id = nextId++,
                recordIntakeId = recordIntakeId,
                status = RECORD_INTAKE_EXTERNAL_LINK_STATUS_CONCEPT,
                createdAt = Instant.now(),
            )
        }

        override fun findByLocalIdentifier(localIdentifier: String): RecordIntakeRecord? =
            createdRecords.lastOrNull { it.localIdentifier?.trim() == localIdentifier }?.let {
                RecordIntakeRecord(
                    id = 1L,
                    localIdentifier = localIdentifier,
                    status = RECORD_INTAKE_STATUS_INTERN_CONCEPT,
                    createdAt = Instant.now(),
                    deceasedStatus = DeceasedStatus.parse(it.deceasedStatus).wireValue,
                    nextOfKinConfirmed = it.nextOfKinConfirmed,
                    archiveName = null,
                    archiveBirthDate = null,
                    archiveDeathDate = null,
                    archiveLicense = null,
                    archiveSourceUri = null,
                    archiveFetchedAt = null,
                )
            }

        override fun confirm(localIdentifier: String, confirmedBy: String, confirmedAt: Instant): RecordIntakeRecord? = null
    }
}
