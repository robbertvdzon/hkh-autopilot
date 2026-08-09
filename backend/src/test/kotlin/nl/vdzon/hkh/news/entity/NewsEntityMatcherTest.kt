package nl.vdzon.hkh.news.entity

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class NewsEntityMatcherTest {
    private val gazetteer = NewsGazetteer.of(
        NewsEntityType.PLEK to listOf(
            GazetteerEntry("Slot Assumburg", listOf("Slot Assumburg", "kasteel Assumburg")),
            GazetteerEntry("Kerkweg", listOf("Kerkweg")),
        ),
        NewsEntityType.PERSOON to listOf(
            GazetteerEntry("Klaas Groen", listOf("Klaas Groen")),
        ),
        NewsEntityType.GEBEURTENIS to listOf(
            GazetteerEntry("Kermis", listOf("kermis")),
        ),
    )

    @Test
    fun `no match yields an empty list`() {
        val result = NewsEntityMatcher.match(gazetteer, "Titel zonder bekende namen", "Ook de samenvatting bevat niets bekends.")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `matching is case insensitive and diacritics normalized`() {
        val result = NewsEntityMatcher.match(gazetteer, "KERMIS in het dorp", "")
        assertEquals(listOf(MatchedNewsEntity(NewsEntityType.GEBEURTENIS, "Kermis")), result)
    }

    @Test
    fun `matching is whole word so a substring inside another word does not match`() {
        val result = NewsEntityMatcher.match(gazetteer, "Kermissenoverzicht van afgelopen jaren", "")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple aliases of the same entry dedup to a single canonical label`() {
        val result = NewsEntityMatcher.match(
            gazetteer,
            "Lezing over kasteel Assumburg",
            "Later in de tekst wordt ook Slot Assumburg genoemd.",
        )
        assertEquals(listOf(MatchedNewsEntity(NewsEntityType.PLEK, "Slot Assumburg")), result)
    }

    @Test
    fun `results are sorted by entity type then by first occurrence in the text`() {
        val text = "Klaas Groen vertelt over de kermis bij de Kerkweg en Slot Assumburg"
        val result = NewsEntityMatcher.match(gazetteer, text, "")

        assertEquals(
            listOf(
                MatchedNewsEntity(NewsEntityType.PLEK, "Kerkweg"),
                MatchedNewsEntity(NewsEntityType.PLEK, "Slot Assumburg"),
                MatchedNewsEntity(NewsEntityType.PERSOON, "Klaas Groen"),
                MatchedNewsEntity(NewsEntityType.GEBEURTENIS, "Kermis"),
            ),
            result,
        )
    }

    @Test
    fun `matching considers both title and message`() {
        val result = NewsEntityMatcher.match(gazetteer, "Titel over Kerkweg", "Samenvatting noemt Klaas Groen apart.")
        assertEquals(
            listOf(
                MatchedNewsEntity(NewsEntityType.PLEK, "Kerkweg"),
                MatchedNewsEntity(NewsEntityType.PERSOON, "Klaas Groen"),
            ),
            result,
        )
    }
}
