package nl.vdzon.hkh.externalverification

import java.time.Instant
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ExternalVerificationPublishGuardTest {

    @Test
    fun `does nothing for a verified outcome with a known license`() {
        ExternalVerificationPublishGuard.assertPublishable(
            outcome(
                ExternalVerificationStatus.VERIFIED,
                "ok",
                ExternalVerificationLicenseStatus.LICENSE_KNOWN,
                "CC0",
            ),
        )
    }

    @Test
    fun `refuses publication for an unverified outcome`() {
        val exception = assertFailsWith<ExternalVerificationPublishBlockedException> {
            ExternalVerificationPublishGuard.assertPublishable(
                outcome(
                    ExternalVerificationStatus.UNVERIFIED,
                    ExternalVerificationReasons.NOT_FOUND,
                    ExternalVerificationLicenseStatus.LICENSE_UNKNOWN,
                    null,
                ),
            )
        }
        kotlin.test.assertEquals(ExternalVerificationReasons.NOT_FOUND, exception.message)
    }

    @Test
    fun `refuses publication for a verified outcome with an unknown license`() {
        val exception = assertFailsWith<ExternalVerificationPublishBlockedException> {
            ExternalVerificationPublishGuard.assertPublishable(
                outcome(
                    ExternalVerificationStatus.VERIFIED,
                    "ok",
                    ExternalVerificationLicenseStatus.LICENSE_UNKNOWN,
                    null,
                ),
            )
        }
        kotlin.test.assertEquals(ExternalVerificationLicenseReasons.PUBLISH_BLOCKED, exception.message)
    }

    private fun outcome(
        status: ExternalVerificationStatus,
        reason: String,
        licenseStatus: ExternalVerificationLicenseStatus,
        licenseValue: String?,
    ) = ExternalVerificationOutcome(
        record = ExternalVerificationRecord(
            id = 1,
            localIdentifier = "HKH-2026-0010",
            externalUri = "http://opendata.archieven.nl/id/1234/abcd-ef01",
            matchedFields = emptyList(),
            status = status.name,
            checkedAt = Instant.now(),
            licenseStatus = licenseStatus.name,
            licenseValue = licenseValue,
            licenseCheckedAt = Instant.now(),
        ),
        reason = reason,
    )
}
