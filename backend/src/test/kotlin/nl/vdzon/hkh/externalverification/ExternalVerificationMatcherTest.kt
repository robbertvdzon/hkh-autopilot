package nl.vdzon.hkh.externalverification

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ExternalVerificationMatcherTest {

    @Test
    fun `matching fixture one - name and both dates identical yields verified`() {
        val local = ExternalVerificationRequest(
            localIdentifier = "HKH-2026-0010",
            name = "Jan Jansen",
            birthDate = "1900-01-01",
            deathDate = "1980-05-05",
            adtid = "1234",
            guid = "abcd-ef01",
        )
        val fetched = ArchiveFetchResult.Found(ArchiveRecordFields("Jan Jansen", "1900-01-01", "1980-05-05"))

        val result = ExternalVerificationMatcher.match(local, fetched)

        assertEquals(ExternalVerificationStatus.VERIFIED, result.status)
        assertTrue(result.verified)
        assertEquals(
            listOf(
                ExternalVerificationMatchableFields.NAME,
                ExternalVerificationMatchableFields.BIRTH_DATE,
                ExternalVerificationMatchableFields.DEATH_DATE,
            ),
            result.matchedFields,
        )
        assertEquals(ExternalVerificationReasons.VERIFIED, result.reason)
    }

    @Test
    fun `matching fixture two - different person, casing and whitespace differences are ignored`() {
        val local = ExternalVerificationRequest(
            localIdentifier = "HKH-2026-0011",
            name = "  Maria de Vries ",
            birthDate = "1875-11-20",
            deathDate = "1950-02-14",
            adtid = "5678",
            guid = "9900-aa11",
        )
        val fetched = ArchiveFetchResult.Found(ArchiveRecordFields("MARIA DE VRIES", "1875-11-20", "1950-02-14"))

        val result = ExternalVerificationMatcher.match(local, fetched)

        assertEquals(ExternalVerificationStatus.VERIFIED, result.status)
    }

    @Test
    fun `a non existent or invalid guid is unverified`() {
        val local = ExternalVerificationRequest(
            localIdentifier = "HKH-2026-0012",
            name = "Piet Pietersen",
            birthDate = "1901-01-01",
            deathDate = "1975-01-01",
            adtid = "0000",
            guid = "not-a-real-guid",
        )

        val result = ExternalVerificationMatcher.match(local, ArchiveFetchResult.NotFound)

        assertEquals(ExternalVerificationStatus.UNVERIFIED, result.status)
        assertTrue(result.matchedFields.isEmpty())
        assertEquals(ExternalVerificationReasons.NOT_FOUND, result.reason)
    }

    @Test
    fun `a partial field match is still unverified`() {
        val local = ExternalVerificationRequest(
            localIdentifier = "HKH-2026-0013",
            name = "Klaas Klaassen",
            birthDate = "1900-01-01",
            deathDate = "1980-05-05",
            adtid = "1111",
            guid = "2222",
        )
        val fetched = ArchiveFetchResult.Found(ArchiveRecordFields("Klaas Klaassen", "1899-12-31", "1980-05-05"))

        val result = ExternalVerificationMatcher.match(local, fetched)

        assertEquals(ExternalVerificationStatus.UNVERIFIED, result.status)
        assertEquals(
            listOf(ExternalVerificationMatchableFields.NAME, ExternalVerificationMatchableFields.DEATH_DATE),
            result.matchedFields,
        )
        assertEquals(ExternalVerificationReasons.FIELDS_DO_NOT_MATCH, result.reason)
    }

    @Test
    fun `an archive endpoint that requires authentication is unverified`() {
        val local = ExternalVerificationRequest(
            localIdentifier = "HKH-2026-0014",
            name = "Klaas Klaassen",
            birthDate = "1900-01-01",
            deathDate = "1980-05-05",
            adtid = "1111",
            guid = "2222",
        )

        val result = ExternalVerificationMatcher.match(local, ArchiveFetchResult.AuthenticationRequired)

        assertEquals(ExternalVerificationStatus.UNVERIFIED, result.status)
        assertEquals(ExternalVerificationReasons.AUTHENTICATION_REQUIRED, result.reason)
    }
}
