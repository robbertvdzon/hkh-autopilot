package nl.vdzon.hkh.externalverification

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class HistoricalMetadataContractTest {
    @Test
    fun `a missing server fetch time prevents full verification`() {
        val result = HistoricalMetadataContract.evaluate(validCandidate(fetchedAt = null))

        assertEquals(HistoricalMetadataVerificationStatus.UNVERIFIED, result.verificationStatus)
        assertEquals(HistoricalMetadataVerificationReasons.MISSING_REQUIRED_FIELD, result.verificationReason)
        assertNull(result.metadata)
    }

    @Test
    fun `a contradictory source value is retained only as a safe minimal result`() {
        val result = HistoricalMetadataContract.evaluate(validCandidate(containsContradictorySourceData = true))

        assertEquals(HistoricalMetadataVerificationReasons.CONTRADICTORY_SOURCE_DATA, result.verificationReason)
        assertEquals("source/item-1", result.sourceIdentifier)
        assertNull(result.metadata)
    }

    @Test
    fun `a missing source identifier may retain a separately known safe fallback but is not verified`() {
        val result = HistoricalMetadataContract.evaluate(
            validCandidate(sourceIdentifier = null, fallbackSourceIdentifier = "source/item-1"),
        )

        assertEquals(HistoricalMetadataVerificationStatus.UNVERIFIED, result.verificationStatus)
        assertEquals("source/item-1", result.sourceIdentifier)
        assertNull(result.metadata)
    }

    private fun validCandidate(
        sourceIdentifier: String? = "source/item-1",
        fetchedAt: Instant? = Instant.parse("2026-08-12T14:00:00Z"),
        containsContradictorySourceData: Boolean = false,
        fallbackSourceIdentifier: String? = null,
    ) = HistoricalMetadataCandidate(
        sourceIdentifier = sourceIdentifier,
        sourceLink = "http://opendata.archieven.nl/id/1000/item-1",
        holder = "Historical Kring Heemskerk",
        title = "Kaart van Heemskerk",
        description = null,
        dating = "1900",
        sourceVersion = "v1",
        snapshotId = null,
        fetchedAt = fetchedAt,
        metadataRightsStatus = MetadataRightsStatus.ALLOWED,
        objectMediaRightsStatus = ObjectMediaRightsStatus.UNKNOWN,
        privacyStatus = HistoricalMetadataPrivacyStatus.CLEAR,
        availabilityStatus = HistoricalMetadataAvailabilityStatus.AVAILABLE,
        containsContradictorySourceData = containsContradictorySourceData,
        fallbackSourceIdentifier = fallbackSourceIdentifier,
        fallbackSourceLink = null,
    )
}
