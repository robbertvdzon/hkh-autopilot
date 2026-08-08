package nl.vdzon.hkh.externalverification

import java.time.Instant
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ExternalVerificationPublishGuardTest {

    @Test
    fun `does nothing for a verified outcome`() {
        ExternalVerificationPublishGuard.assertPublishable(outcome(ExternalVerificationStatus.VERIFIED, "ok"))
    }

    @Test
    fun `refuses publication for an unverified outcome`() {
        val exception = assertFailsWith<ExternalVerificationPublishBlockedException> {
            ExternalVerificationPublishGuard.assertPublishable(
                outcome(ExternalVerificationStatus.UNVERIFIED, ExternalVerificationReasons.NOT_FOUND),
            )
        }
        kotlin.test.assertEquals(ExternalVerificationReasons.NOT_FOUND, exception.message)
    }

    private fun outcome(status: ExternalVerificationStatus, reason: String) = ExternalVerificationOutcome(
        record = ExternalVerificationRecord(
            id = 1,
            localIdentifier = "HKH-2026-0010",
            externalUri = "http://opendata.archieven.nl/id/1234/abcd-ef01",
            matchedFields = emptyList(),
            status = status.name,
            checkedAt = Instant.now(),
        ),
        reason = reason,
    )
}
