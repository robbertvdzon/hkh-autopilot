package nl.vdzon.hkh.recordintake

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Dekt alle statusovergangen van [RecordPublicStatusResolver], inclusief het zelfherstellende
 * gedrag: een live herclassificatie naar Blocked degradeert de weergave zonder `confirmedBy`/
 * `confirmedAt` te wissen, en een latere Processable-herclassificatie herstelt automatisch de
 * CONFIRMED-weergave op basis van diezelfde bewaarde bevestiging.
 */
class RecordPublicStatusResolverTest {

    private val resolver = RecordPublicStatusResolver()
    private val confirmedAt = Instant.parse("2026-08-01T10:00:00Z")

    @Test
    fun `no record at all yields NO_INTAKE`() {
        val view = resolver.resolve(null)

        assertEquals(RecordPublicStatus.NO_INTAKE, view.status)
        assertNull(view.name)
    }

    @Test
    fun `a record without archive fields yields SAVED_WITHOUT_SOURCE`() {
        val record = record(archiveName = null, archiveSourceUri = null, confirmedBy = null, confirmedAt = null)

        assertEquals(RecordPublicStatus.SAVED_WITHOUT_SOURCE, resolver.resolve(record).status)
    }

    @Test
    fun `archive data present but not yet confirmed by an administrator yields SAVED_WITHOUT_SOURCE`() {
        val record = record(confirmedBy = null, confirmedAt = null)

        assertEquals(RecordPublicStatus.SAVED_WITHOUT_SOURCE, resolver.resolve(record).status)
    }

    @Test
    fun `confirmed archive data with a processable live reclassification yields CONFIRMED with year only dates`() {
        val record = record(archiveBirthDate = "1900-01-01", archiveDeathDate = "1980-05-05")

        val view = resolver.resolve(record)

        assertEquals(RecordPublicStatus.CONFIRMED, view.status)
        assertEquals("Jan Jansen", view.name)
        assertEquals("1900", view.birthYear)
        assertEquals("1980", view.deathYear)
        assertEquals("CC0", view.license)
        assertEquals("http://opendata.archieven.nl/id/1000/jan", view.sourceUri)
        assertEquals(confirmedAt, view.confirmedAt)
    }

    @Test
    fun `a live reclassification to Blocked degrades a confirmed record to the neutral status`() {
        val record = record(deceasedStatus = "levend")

        assertEquals(RecordPublicStatus.SAVED_WITHOUT_SOURCE, resolver.resolve(record).status)
    }

    @Test
    fun `self healing - a record blocked at request time becomes CONFIRMED again once processable, without re-confirming`() {
        val blockedRecord = record(deceasedStatus = "levend")
        assertEquals(RecordPublicStatus.SAVED_WITHOUT_SOURCE, resolver.resolve(blockedRecord).status)

        val healedRecord = blockedRecord.copy(deceasedStatus = "overleden")
        val view = resolver.resolve(healedRecord)

        assertEquals(RecordPublicStatus.CONFIRMED, view.status)
        assertEquals(confirmedAt, view.confirmedAt)
    }

    private fun record(
        deceasedStatus: String = "overleden",
        archiveName: String? = "Jan Jansen",
        archiveBirthDate: String? = "1900-01-01",
        archiveDeathDate: String? = "1980-05-05",
        archiveSourceUri: String? = "http://opendata.archieven.nl/id/1000/jan",
        confirmedBy: String? = "admin@example.com",
        confirmedAt: Instant? = this.confirmedAt,
    ) = RecordIntakeRecord(
        id = 1L,
        localIdentifier = "HKH-2026-0001",
        status = RECORD_INTAKE_STATUS_INTERN_CONCEPT,
        createdAt = Instant.parse("2026-07-01T10:00:00Z"),
        deceasedStatus = deceasedStatus,
        nextOfKinConfirmed = null,
        archiveName = archiveName,
        archiveBirthDate = archiveBirthDate,
        archiveDeathDate = archiveDeathDate,
        archiveLicense = "CC0",
        archiveSourceUri = archiveSourceUri,
        archiveFetchedAt = Instant.parse("2026-07-01T10:05:00Z"),
        confirmedBy = confirmedBy,
        confirmedAt = confirmedAt,
    )
}
