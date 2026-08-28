package nl.vdzon.hkh.personsearch

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonSearchAnswerBuilderTest {

    private val checkedAt: Instant = Instant.parse("2026-08-28T10:00:00Z")

    private val nicolaasRecord = ArchivesShowRecord(
        archiveCode = "nha",
        identifier = "002ED0F3-F08C-4223-A5EA-BA385D04336E",
        personName = "Nicolaas Jacobus Sinnige",
        eventType = "Geboorte",
        eventDate = "1878-07-25",
        eventPlace = "Heemskerk",
        relations = listOf(
            ArchivesRelation("Vader", "Pieter Sinnige"),
            ArchivesRelation("Moeder", "Anna Geertruida Eenhuis"),
        ),
        institution = "Noord-Hollands Archief",
        sourceType = "Geboorteakte",
        archiveNumber = "123",
        registerNumber = "4",
        deedNumber = "56",
        recordNumber = "789",
        digitalOriginalUrl = null,
    )

    @Test
    fun `builds the controlled Nicolaas Jacobus Sinnige answer with numbered citations`() {
        val answer = PersonSearchAnswerBuilder().build(listOf(nicolaasRecord), checkedAt)

        assertEquals(
            listOf(1),
            answer.sentences.first { it.text.contains("is geboren op 25 juli 1878 in Heemskerk") }.sourceNumbers,
        )
        assertTrue(answer.sentences.any { it.text == "Pieter Sinnige was de vader van Nicolaas Jacobus Sinnige." })
        assertTrue(answer.sentences.any { it.text == "Anna Geertruida Eenhuis was de moeder van Nicolaas Jacobus Sinnige." })

        assertEquals(1, answer.sources.size)
        val source = answer.sources.single()
        assertEquals(1, source.number)
        assertEquals("Noord-Hollands Archief", source.institution)
        assertEquals("Geboorteakte", source.sourceType)
        assertEquals("123", source.archiveNumber)
        assertEquals("4", source.registerNumber)
        assertEquals("56", source.deedNumber)
        assertEquals("789", source.recordNumber)
        assertEquals("https://www.openarchieven.nl/nha:002ED0F3-F08C-4223-A5EA-BA385D04336E", source.openArchivesLink)
        assertEquals(checkedAt, source.checkedAt)

        assertEquals(
            listOf(
                PersonSearchConnectionOption("Vader", "Pieter Sinnige"),
                PersonSearchConnectionOption("Moeder", "Anna Geertruida Eenhuis"),
            ),
            answer.connections,
        )

        assertTrue(answer.disclaimer.contains("Deze ene geboorteakte"))
        assertTrue(answer.disclaimer.contains("geen volledig levensverhaal"))
        assertTrue(answer.disclaimer.contains("geen overzicht van alle gebeurtenissen in Heemskerk in 1878"))
    }

    @Test
    fun `caps followed connections at two even when a record exposes more relations`() {
        val record = nicolaasRecord.copy(
            relations = listOf(
                ArchivesRelation("Vader", "Pieter Sinnige"),
                ArchivesRelation("Moeder", "Anna Geertruida Eenhuis"),
                ArchivesRelation("Getuige", "Jan de Getuige"),
            ),
        )

        val answer = PersonSearchAnswerBuilder().build(listOf(record), checkedAt)

        assertEquals(2, answer.connections.size)
    }

    @Test
    fun `assigns increasing citation numbers per record and includes each in the openarchieven link`() {
        val second = nicolaasRecord.copy(identifier = "SECOND-IDENTIFIER", relations = emptyList())

        val answer = PersonSearchAnswerBuilder().build(listOf(nicolaasRecord, second), checkedAt)

        assertEquals(listOf(1, 2), answer.sources.map { it.number })
        assertTrue(answer.sources[1].openArchivesLink.endsWith("SECOND-IDENTIFIER"))
    }
}
