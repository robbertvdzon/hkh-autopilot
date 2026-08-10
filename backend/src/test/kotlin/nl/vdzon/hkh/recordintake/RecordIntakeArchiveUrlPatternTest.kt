package nl.vdzon.hkh.recordintake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordIntakeArchiveUrlPatternTest {

    @Test
    fun `a matching url yields the adtid and guid`() {
        val reference = RecordIntakeArchiveUrlPattern.parse("http://opendata.archieven.nl/id/1000/verified-jan")

        assertEquals(ArchiveUrlReference(adtid = "1000", guid = "verified-jan"), reference)
    }

    @Test
    fun `null or blank input does not match`() {
        assertNull(RecordIntakeArchiveUrlPattern.parse(null))
        assertNull(RecordIntakeArchiveUrlPattern.parse("  "))
    }

    @Test
    fun `a different host does not match`() {
        assertNull(RecordIntakeArchiveUrlPattern.parse("http://example.org/id/1000/verified-jan"))
    }

    @Test
    fun `https instead of http does not match`() {
        assertNull(RecordIntakeArchiveUrlPattern.parse("https://opendata.archieven.nl/id/1000/verified-jan"))
    }

    @Test
    fun `a missing guid segment does not match`() {
        assertNull(RecordIntakeArchiveUrlPattern.parse("http://opendata.archieven.nl/id/1000"))
    }

    @Test
    fun `a trailing slash does not match`() {
        assertNull(RecordIntakeArchiveUrlPattern.parse("http://opendata.archieven.nl/id/1000/verified-jan/"))
    }

    @Test
    fun `a completely unrelated url does not match`() {
        assertNull(RecordIntakeArchiveUrlPattern.parse("https://noord-hollandsarchief.nl/record/1"))
    }
}
