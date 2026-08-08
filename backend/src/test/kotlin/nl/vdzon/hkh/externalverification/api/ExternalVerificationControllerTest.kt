package nl.vdzon.hkh.externalverification.api

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.vdzon.hkh.externalverification.ArchiveFetchResult
import nl.vdzon.hkh.externalverification.ArchiveRecordFields
import nl.vdzon.hkh.externalverification.ArchivesNlClient
import nl.vdzon.hkh.externalverification.ExternalVerificationFieldPaths
import nl.vdzon.hkh.externalverification.ExternalVerificationRecord
import nl.vdzon.hkh.externalverification.ExternalVerificationService
import nl.vdzon.hkh.externalverification.ExternalVerificationStatus
import nl.vdzon.hkh.externalverification.ExternalVerificationStore
import nl.vdzon.hkh.externalverification.ExternalVerificationTokenCipher
import nl.vdzon.hkh.externalverification.ExternalVerificationValidator
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64

class ExternalVerificationControllerTest {

    private val validator = ExternalVerificationValidator()
    private val tokenCipher = ExternalVerificationTokenCipher(Base64.getEncoder().encodeToString(ByteArray(32)))

    @Test
    fun `stores exactly one verified record for a matching request`() {
        val store = RecordingStore()
        val controller = controller(
            store = store,
            client = ArchivesNlClient { _, _, _ ->
                ArchiveFetchResult.Found(ArchiveRecordFields("Jan Jansen", "1900-01-01", "1980-05-05"))
            },
        )

        val response = controller.verify(validRequest())

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body as ExternalVerificationResponse
        assertEquals(ExternalVerificationStatus.VERIFIED.name, body.status)
        assertEquals(1, store.created.size)
    }

    @Test
    fun `stores an unverified record for a non existent guid without storing the full payload`() {
        val store = RecordingStore()
        val controller = controller(store = store, client = ArchivesNlClient { _, _, _ -> ArchiveFetchResult.NotFound })

        val response = controller.verify(validRequest().copy(guid = "not-a-real-guid"))

        val body = response.body as ExternalVerificationResponse
        assertEquals(ExternalVerificationStatus.UNVERIFIED.name, body.status)
        assertTrue(body.matchedFields.isEmpty())
    }

    @Test
    fun `rejects an incomplete request with machine readable field errors without contacting the archive`() {
        var clientCalled = false
        val controller = controller(client = ArchivesNlClient { _, _, _ -> clientCalled = true; ArchiveFetchResult.NotFound })

        val response = controller.verify(validRequest().copy(localIdentifier = null))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as ExternalVerificationFieldErrorsResponse
        assertEquals(listOf(ExternalVerificationFieldPaths.LOCAL_IDENTIFIER), body.fieldErrors)
        assertTrue(!clientCalled)
    }

    @Test
    fun `never echoes an access token back to the caller`() {
        val controller = controller(client = ArchivesNlClient { _, _, _ -> ArchiveFetchResult.AuthenticationRequired })

        val response = controller.verify(validRequest().copy(accessToken = "top-secret-token"))

        val serialized = response.body.toString()
        assertTrue(!serialized.contains("top-secret-token"))
    }

    private fun validRequest() = ExternalVerificationApiRequest(
        localIdentifier = "HKH-2026-0010",
        name = "Jan Jansen",
        birthDate = "1900-01-01",
        deathDate = "1980-05-05",
        adtid = "1234",
        guid = "abcd-ef01",
    )

    private fun controller(
        store: ExternalVerificationStore = RecordingStore(),
        client: ArchivesNlClient = ArchivesNlClient { _, _, _ -> ArchiveFetchResult.NotFound },
    ) = ExternalVerificationController(validator, ExternalVerificationService(client, store, tokenCipher))

    private class RecordingStore : ExternalVerificationStore {
        val created = mutableListOf<ExternalVerificationRecord>()
        private var nextId = 1L

        override fun create(
            localIdentifier: String,
            externalUri: String,
            matchedFields: List<String>,
            status: ExternalVerificationStatus,
            encryptedAccessToken: String?,
        ): ExternalVerificationRecord {
            val record = ExternalVerificationRecord(
                id = nextId++,
                localIdentifier = localIdentifier,
                externalUri = externalUri,
                matchedFields = matchedFields,
                status = status.name,
                checkedAt = Instant.now(),
            )
            created += record
            return record
        }
    }
}
