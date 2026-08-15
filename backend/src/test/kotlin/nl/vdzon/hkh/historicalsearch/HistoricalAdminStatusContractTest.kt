package nl.vdzon.hkh.historicalsearch

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HistoricalAdminStatusContractTest {
    @Test
    fun `complete explicit provider metadata confirms source and public release`() {
        val status = HistoricalAdminStatusContract.evaluate(completeResult())

        assertEquals(HistoricalAdminStatus.CONFIRMED, status.sourceVerification.status)
        assertEquals(HistoricalAdminStatus.CONFIRMED, status.metadataRights.status)
        assertEquals(HistoricalAdminStatus.CONFIRMED, status.privacy.status)
        assertEquals(HistoricalAdminStatus.CONFIRMED, status.publicRelease.status)
        assertTrue(status.publicRelease.reason.isNotBlank())
    }

    @Test
    fun `missing rights remains unknown and can never confirm public release`() {
        val status = HistoricalAdminStatusContract.evaluate(
            completeResult().copy(metadataRights = HistoricalRightsStatus.UNKNOWN),
        )

        assertEquals(HistoricalAdminStatus.UNKNOWN, status.metadataRights.status)
        assertNotEquals(HistoricalAdminStatus.CONFIRMED, status.publicRelease.status)
        assertTrue(status.metadataRights.reason.isNotBlank())
    }

    @Test
    fun `missing privacy remains unknown and can never confirm public release`() {
        val status = HistoricalAdminStatusContract.evaluate(
            completeResult().copy(privacyStatus = HistoricalPrivacyStatus.UNKNOWN),
        )

        assertEquals(HistoricalAdminStatus.UNKNOWN, status.privacy.status)
        assertNotEquals(HistoricalAdminStatus.CONFIRMED, status.publicRelease.status)
        assertTrue(status.privacy.reason.isNotBlank())
    }

    @Test
    fun `restricted or blocked explicit values are rejected`() {
        val restricted = HistoricalAdminStatusContract.evaluate(
            completeResult().copy(metadataRights = HistoricalRightsStatus.RESTRICTED),
        )
        val blocked = HistoricalAdminStatusContract.evaluate(
            completeResult().copy(privacyStatus = HistoricalPrivacyStatus.BLOCKED),
        )

        assertEquals(HistoricalAdminStatus.REJECTED, restricted.metadataRights.status)
        assertEquals(HistoricalAdminStatus.REJECTED, blocked.privacy.status)
        assertEquals(HistoricalAdminStatus.REJECTED, restricted.publicRelease.status)
        assertEquals(HistoricalAdminStatus.REJECTED, blocked.publicRelease.status)
    }

    @Test
    fun `invalid or contradictory technical metadata is rejected and unsafe identity is withheld`() {
        val result = completeResult().copy(
            sourceName = "\u0000raw-provider-payload",
            stableIdentifier = "",
            originalSourceUrl = "javascript:alert(1)",
            technicalStatus = HistoricalTechnicalStatus.INVALID_RESPONSE,
        )

        val status = HistoricalAdminStatusContract.evaluate(result)

        assertEquals(HistoricalAdminStatus.REJECTED, status.sourceVerification.status)
        assertEquals(HistoricalAdminStatus.REJECTED, status.publicRelease.status)
        assertEquals(null, HistoricalAdminStatusContract.safeSourceName(result))
        assertEquals(null, HistoricalAdminStatusContract.safeStableIdentifier(result))
        assertEquals(null, HistoricalAdminStatusContract.safeOriginalSourceUrl(result))
    }

    @Test
    fun `stable identifier that contradicts source record id is rejected and withheld`() {
        val result = completeResult().copy(stableIdentifier = "hee:record-2")

        val status = HistoricalAdminStatusContract.evaluate(result)

        assertEquals(HistoricalAdminStatus.REJECTED, status.sourceVerification.status)
        assertEquals(HistoricalAdminStatus.REJECTED, status.publicRelease.status)
        assertEquals(null, HistoricalAdminStatusContract.safeStableIdentifier(result))
    }

    @Test
    fun `original source url that contradicts stable url is rejected and withheld`() {
        val result = completeResult().copy(originalSourceUrl = "https://source.example/record-2")

        val status = HistoricalAdminStatusContract.evaluate(result)

        assertEquals(HistoricalAdminStatus.REJECTED, status.sourceVerification.status)
        assertEquals(HistoricalAdminStatus.REJECTED, status.publicRelease.status)
        assertEquals(null, HistoricalAdminStatusContract.safeOriginalSourceUrl(result))
    }

    @Test
    fun `object media rights stay separate and do not grant release`() {
        val status = HistoricalAdminStatusContract.evaluate(
            completeResult().copy(objectMediaRights = HistoricalRightsStatus.RESTRICTED),
        )

        assertEquals(HistoricalAdminStatus.REJECTED, status.objectMediaRights.status)
        assertEquals(HistoricalAdminStatus.CONFIRMED, status.publicRelease.status)
        assertTrue(status.objectMediaRights.reason.isNotBlank())
    }

    @Test
    fun `all four status values are representable`() {
        assertEquals(
            setOf("CONFIRMED", "UNKNOWN", "REJECTED", "NOT_APPLICABLE"),
            HistoricalAdminStatus.entries.map(HistoricalAdminStatus::name).toSet(),
        )
    }

    private fun completeResult() = HistoricalSearchResult(
        source = HistoricalSearchSource.OPEN_ARCHIEVEN,
        sourceRecordId = "hee:record-1",
        stableUrl = "https://source.example/record-1",
        title = "Historische titel",
        description = "Historische beschrijving",
        place = null,
        person = null,
        event = null,
        dateStart = null,
        dateEnd = null,
        institution = null,
        rights = "PUBLIC",
        privacy = "CLEAR",
        retrievedAt = Instant.parse("2026-01-01T00:00:00Z"),
        technicalStatus = HistoricalTechnicalStatus.AVAILABLE,
        metadataRights = HistoricalRightsStatus.ALLOWED,
        objectMediaRights = HistoricalRightsStatus.ALLOWED,
        privacyStatus = HistoricalPrivacyStatus.CLEAR,
        sourceName = "Open Archieven",
        stableIdentifier = "hee:record-1",
        originalSourceUrl = "https://source.example/record-1",
    )
}
