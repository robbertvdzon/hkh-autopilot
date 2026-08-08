package nl.vdzon.hkh.externalverification

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ExternalVerificationLicenseEvaluatorTest {

    @Test
    fun `fixture with a visible license yields license known with the license value`() {
        val fetched = ArchiveFetchResult.Found(
            ArchiveRecordFields("Jan Jansen", "1900-01-01", "1980-05-05", license = "CC0"),
        )

        val result = ExternalVerificationLicenseEvaluator.evaluate(fetched)

        assertEquals(ExternalVerificationLicenseStatus.LICENSE_KNOWN, result.status)
        assertEquals("CC0", result.licenseValue)
        assertEquals(true, result.known)
    }

    @Test
    fun `fixture without a license field yields license unknown`() {
        val fetched = ArchiveFetchResult.Found(
            ArchiveRecordFields("Piet Pietersen", "1901-01-01", "1975-01-01", license = null),
        )

        val result = ExternalVerificationLicenseEvaluator.evaluate(fetched)

        assertEquals(ExternalVerificationLicenseStatus.LICENSE_UNKNOWN, result.status)
        assertEquals(null, result.licenseValue)
        assertEquals(false, result.known)
    }

    @Test
    fun `a blank license value is treated as unknown`() {
        val fetched = ArchiveFetchResult.Found(
            ArchiveRecordFields("Piet Pietersen", "1901-01-01", "1975-01-01", license = "   "),
        )

        val result = ExternalVerificationLicenseEvaluator.evaluate(fetched)

        assertEquals(ExternalVerificationLicenseStatus.LICENSE_UNKNOWN, result.status)
    }

    @Test
    fun `a not found archive record yields license unknown`() {
        val result = ExternalVerificationLicenseEvaluator.evaluate(ArchiveFetchResult.NotFound)

        assertEquals(ExternalVerificationLicenseStatus.LICENSE_UNKNOWN, result.status)
    }

    @Test
    fun `an endpoint that requires authentication yields license unknown`() {
        val result = ExternalVerificationLicenseEvaluator.evaluate(ArchiveFetchResult.AuthenticationRequired)

        assertEquals(ExternalVerificationLicenseStatus.LICENSE_UNKNOWN, result.status)
    }

    @Test
    fun `two records from the same collection can have independent license outcomes`() {
        val withLicense = ExternalVerificationLicenseEvaluator.evaluate(
            ArchiveFetchResult.Found(ArchiveRecordFields("Jan Jansen", "1900-01-01", "1980-05-05", license = "CC0")),
        )
        val withoutLicense = ExternalVerificationLicenseEvaluator.evaluate(
            ArchiveFetchResult.Found(ArchiveRecordFields("Jan Jansen", "1900-01-01", "1980-05-05", license = null)),
        )

        assertEquals(ExternalVerificationLicenseStatus.LICENSE_KNOWN, withLicense.status)
        assertEquals("CC0", withLicense.licenseValue)
        assertEquals(ExternalVerificationLicenseStatus.LICENSE_UNKNOWN, withoutLicense.status)
        assertEquals(null, withoutLicense.licenseValue)
    }

    @Test
    fun `a known license status always carries a non blank license value`() {
        assertFailsWith<IllegalArgumentException> {
            ExternalVerificationLicenseResult(ExternalVerificationLicenseStatus.LICENSE_KNOWN, null)
        }
    }

    @Test
    fun `an unknown license status never carries a license value`() {
        assertFailsWith<IllegalArgumentException> {
            ExternalVerificationLicenseResult(ExternalVerificationLicenseStatus.LICENSE_UNKNOWN, "CC0")
        }
    }
}
