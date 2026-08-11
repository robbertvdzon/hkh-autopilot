package nl.vdzon.hkh.recordintake

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkh.externalverification.ArchiveFetchResult
import nl.vdzon.hkh.externalverification.ArchiveRecordFields
import nl.vdzon.hkh.externalverification.ArchivesNlClient

/**
 * Dekt de fail-closed dubbele classificatieregel van [RecordIntakeService] voor alle
 * AC-combinaties: Processable-Processable (opslaan), Processable-Blocked (weigeren, contradictie
 * expliciet benoemd), Blocked-overig, ONBEKEND-overig, een ongeldige/onbereikbare koppeling en
 * 'sla op zonder externe brongegevens'. Gebruikt een geheugenstore en een instelbare fake
 * [ArchivesNlClient] zodat deze tests zonder database of netwerkverkeer draaien.
 */
class RecordIntakeServiceTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC)
    private val validator = RecordIntakeValidator()

    @Test
    fun `both local and external classification processable stores the external core fields`() {
        val store = InMemoryStore()
        val service = service(store, foundClient("Jan Jansen", "1900-01-01", "1980-05-05", "CC0"))

        val result = service.create(intake(confirmExternalArchiveData = true))

        assertTrue(result.externalArchiveOutcome!!.stored)
        val stored = store.lastArchiveData!!
        assertEquals("Jan Jansen", stored.name)
        assertEquals("1900-01-01", stored.birthDate)
        assertEquals("1980-05-05", stored.deathDate)
        assertEquals("CC0", stored.license)
        assertEquals("http://opendata.archieven.nl/id/1000/verified-jan", stored.sourceUri)
        assertEquals(Instant.now(fixedClock), stored.fetchedAt)
    }

    @Test
    fun `external data without a death date blocks storage despite a confirmed local deceased status`() {
        val store = InMemoryStore()
        // Geen sterftedatum gevonden ondanks lokaal bevestigd overlijden (AC 5).
        val service = service(store, foundClient(name = "Jan Jansen", birthDate = "1900-01-01", deathDate = null))

        val result = service.create(intake(confirmExternalArchiveData = true))

        assertFalse(result.externalArchiveOutcome!!.stored)
        assertTrue(result.externalArchiveOutcome!!.reason.contains("Externe classificatie is geblokkeerd"))
        val stored = store.lastArchiveData!!
        assertNull(stored.name)
        assertNull(stored.birthDate)
        assertNull(stored.deathDate)
        // Licentie/bron-URI/ophaaldatum zijn niet-persoonsgebonden en blijven wel bewaard.
        assertEquals("http://opendata.archieven.nl/id/1000/verified-jan", stored.sourceUri)
        assertEquals(Instant.now(fixedClock), stored.fetchedAt)
    }

    @Test
    fun `a blocked local classification refuses storage even when the external data is processable`() {
        val store = InMemoryStore()
        val service = service(store, foundClient("Jan Jansen", "1900-01-01", "1980-05-05", "CC0"))

        val result = service.create(
            intake(confirmExternalArchiveData = true, deceasedStatus = "levend"),
        )

        assertFalse(result.externalArchiveOutcome!!.stored)
        assertTrue(result.externalArchiveOutcome!!.reason.contains("Lokale classificatie is geblokkeerd"))
        val stored = store.lastArchiveData!!
        assertNull(stored.name)
        assertEquals("CC0", stored.license)
    }

    @Test
    fun `an unknown local deceased status refuses storage`() {
        val store = InMemoryStore()
        val service = service(store, foundClient("Jan Jansen", "1900-01-01", "1980-05-05", "CC0"))

        val result = service.create(intake(confirmExternalArchiveData = true, deceasedStatus = null))

        assertFalse(result.externalArchiveOutcome!!.stored)
        assertNull(store.lastArchiveData!!.name)
    }

    @Test
    fun `a confirmed next of kin blocks local classification and refuses storage`() {
        val store = InMemoryStore()
        val service = service(store, foundClient("Jan Jansen", "1900-01-01", "1980-05-05", "CC0"))

        val result = service.create(
            intake(confirmExternalArchiveData = true, nextOfKinConfirmed = true),
        )

        assertFalse(result.externalArchiveOutcome!!.stored)
        assertNull(store.lastArchiveData!!.name)
    }

    @Test
    fun `an invalid durable url refuses storage without ever contacting the archive`() {
        val store = InMemoryStore()
        val client = ArchivesNlClient { _, _, _ -> error("should never be called for a non-matching url") }
        val service = service(store, client)

        val result = service.create(
            intake(confirmExternalArchiveData = true, durableUrl = "https://example.org/not-archieven-nl"),
        )

        assertFalse(result.externalArchiveOutcome!!.stored)
        assertNull(store.lastArchiveData)
    }

    @Test
    fun `an unreachable or non matching archive record refuses storage and stores no archive fields`() {
        val store = InMemoryStore()
        val service = service(store, ArchivesNlClient { _, _, _ -> ArchiveFetchResult.NotFound })

        val result = service.create(intake(confirmExternalArchiveData = true))

        assertFalse(result.externalArchiveOutcome!!.stored)
        assertNull(store.lastArchiveData)
    }

    @Test
    fun `saving without external archive data never contacts the archive or stores any archive fields`() {
        val store = InMemoryStore()
        val client = ArchivesNlClient { _, _, _ -> error("should never be called") }
        val service = service(store, client)

        val result = service.create(intake(confirmExternalArchiveData = false))

        assertNull(result.externalArchiveOutcome)
        assertNull(store.lastArchiveData)
    }

    @Test
    fun `deceased status and next of kin confirmation are always stored regardless of external data`() {
        val store = InMemoryStore()
        val service = service(store, ArchivesNlClient { _, _, _ -> ArchiveFetchResult.NotFound })

        service.create(intake(confirmExternalArchiveData = false, deceasedStatus = "overleden", nextOfKinConfirmed = false))

        assertEquals("overleden", store.lastRecord!!.deceasedStatus)
        assertEquals(false, store.lastRecord!!.nextOfKinConfirmed)
    }

    private fun service(store: RecordIntakeStore, client: ArchivesNlClient) =
        RecordIntakeService(store, validator, client, clock = fixedClock)

    private fun foundClient(name: String?, birthDate: String?, deathDate: String?, license: String? = null) =
        ArchivesNlClient { _, _, _ -> ArchiveFetchResult.Found(ArchiveRecordFields(name, birthDate, deathDate, license)) }

    private fun intake(
        confirmExternalArchiveData: Boolean,
        deceasedStatus: String? = "overleden",
        nextOfKinConfirmed: Boolean? = null,
        durableUrl: String = "http://opendata.archieven.nl/id/1000/verified-jan",
    ) = RecordIntake(
        localIdentifier = "HKH-2026-0001",
        title = "Poldermolen De Eendracht",
        dating = "circa 1890",
        provenance = "Streekarchief Waterland",
        rightsStatus = "publicatie toegestaan",
        privacyClassification = "geen persoonsgegevens",
        accessUrl = "https://collectie.hkh-autopilot.local/records/hkh-2026-0001",
        externalLink = RecordIntakeExternalLinkInput(durableUrl = durableUrl),
        deceasedStatus = deceasedStatus,
        nextOfKinConfirmed = nextOfKinConfirmed,
        confirmExternalArchiveData = confirmExternalArchiveData,
    )

    private class InMemoryStore : RecordIntakeStore {
        var lastRecord: RecordIntakeRecord? = null
        var lastArchiveData: RecordIntakeExternalArchiveDataToStore? = null
        private var nextId = 1L

        override fun create(intake: RecordIntake, archiveData: RecordIntakeExternalArchiveDataToStore?): RecordIntakeRecord {
            lastArchiveData = archiveData
            val record = RecordIntakeRecord(
                id = nextId++,
                localIdentifier = intake.localIdentifier!!.trim(),
                status = RECORD_INTAKE_STATUS_INTERN_CONCEPT,
                createdAt = Instant.now(),
                deceasedStatus = intake.deceasedStatus?.trim()?.lowercase() ?: "onbekend",
                nextOfKinConfirmed = intake.nextOfKinConfirmed,
                archiveName = archiveData?.name,
                archiveBirthDate = archiveData?.birthDate,
                archiveDeathDate = archiveData?.deathDate,
                archiveLicense = archiveData?.license,
                archiveSourceUri = archiveData?.sourceUri,
                archiveFetchedAt = archiveData?.fetchedAt,
            )
            lastRecord = record
            return record
        }

        override fun createExternalLink(recordIntakeId: Long, link: RecordIntakeExternalLinkInput): RecordIntakeExternalLink =
            RecordIntakeExternalLink(
                id = nextId++,
                recordIntakeId = recordIntakeId,
                status = RECORD_INTAKE_EXTERNAL_LINK_STATUS_CONCEPT,
                createdAt = Instant.now(),
            )

        override fun findByLocalIdentifier(localIdentifier: String): RecordIntakeRecord? =
            lastRecord?.takeIf { it.localIdentifier == localIdentifier }

        override fun confirm(localIdentifier: String, confirmedBy: String, confirmedAt: Instant): RecordIntakeRecord? {
            val record = lastRecord?.takeIf { it.localIdentifier == localIdentifier } ?: return null
            val confirmed = record.copy(confirmedBy = confirmedBy, confirmedAt = confirmedAt)
            lastRecord = confirmed
            return confirmed
        }
    }
}
