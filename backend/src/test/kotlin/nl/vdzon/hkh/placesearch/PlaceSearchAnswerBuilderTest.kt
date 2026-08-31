package nl.vdzon.hkh.placesearch

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceSearchAnswerBuilderTest {

    private val checkedAt: Instant = Instant.parse("2026-08-31T10:00:00Z")

    @Test
    fun `every non-null fact becomes its own numbered sentence citing the same qid`() {
        val facts = PlaceEntityFacts(
            qid = "Q1968571",
            label = "Kasteel Assumburg",
            description = "kasteel in Heemskerk",
            inceptionYear = "1546",
            architecturalStyleLabel = "gotiek",
            architectLabel = null,
            heritageStatusLabel = "rijksmonument",
            municipalityLabel = "Heemskerk",
        )

        val answer = PlaceSearchAnswerBuilder().build(facts, checkedAt)

        assertEquals(4, answer.sentences.size)
        assertEquals(5, answer.contextSentence?.sourceNumbers?.single())
        assertTrue(answer.sentences.any { it.text.contains("1546") })
        assertTrue(answer.sentences.any { it.text.contains("gotiek") })
        assertTrue(answer.sentences.any { it.text.contains("rijksmonument") })
        assertEquals("Kasteel Assumburg ligt in de gemeente Heemskerk.", answer.contextSentence?.text)
        assertEquals(5, answer.sources.size)
        val allNumbers = (answer.sentences.flatMap { it.sourceNumbers } + answer.contextSentence!!.sourceNumbers).sorted()
        assertEquals(listOf(1, 2, 3, 4, 5), allNumbers)
        answer.sources.forEach {
            assertEquals("Q1968571", it.qid)
            assertEquals("https://www.wikidata.org/wiki/Q1968571", it.wikidataLink)
            assertEquals(checkedAt, it.checkedAt)
        }
        assertTrue(answer.disclaimer.contains("Q1968571"))
    }

    @Test
    fun `always has at least an intro sentence even without any optional fact`() {
        val facts = PlaceEntityFacts(
            qid = "Q9",
            label = "Iets",
            description = null,
            inceptionYear = null,
            architecturalStyleLabel = null,
            architectLabel = null,
            heritageStatusLabel = null,
            municipalityLabel = null,
        )

        val answer = PlaceSearchAnswerBuilder().build(facts, checkedAt)

        assertEquals(1, answer.sentences.size)
        assertEquals("Iets.", answer.sentences.single().text)
        assertNull(answer.contextSentence)
        assertEquals(1, answer.sources.size)
    }
}
