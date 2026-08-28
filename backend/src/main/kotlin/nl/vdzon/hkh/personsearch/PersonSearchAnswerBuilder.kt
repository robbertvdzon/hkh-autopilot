package nl.vdzon.hkh.personsearch

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import org.springframework.stereotype.Component

private val dutchDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl"))

private fun formatDutchDate(isoDate: String): String = try {
    LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE).format(dutchDateFormatter)
} catch (_: DateTimeParseException) {
    isoDate
}

private fun eventYear(isoDate: String): String = try {
    LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE).year.toString()
} catch (_: DateTimeParseException) {
    isoDate.take(4)
}

private fun eventVerbSentence(record: ArchivesShowRecord): String {
    val date = formatDutchDate(record.eventDate)
    return when (record.eventType.trim().lowercase(Locale.forLanguageTag("nl"))) {
        "geboorte" -> "${record.personName} is geboren op $date in ${record.eventPlace}."
        "huwelijk" -> "${record.personName} is getrouwd op $date in ${record.eventPlace}."
        "overlijden" -> "${record.personName} is overleden op $date in ${record.eventPlace}."
        "doop" -> "${record.personName} is gedoopt op $date in ${record.eventPlace}."
        "bevolkingsregistratie" -> "${record.personName} staat geregistreerd op $date in ${record.eventPlace}."
        else -> "${record.personName} — ${record.eventType} op $date in ${record.eventPlace}."
    }
}

private fun eventNoun(eventType: String): String = when (eventType.trim().lowercase(Locale.forLanguageTag("nl"))) {
    "geboorte" -> "geboorteakte"
    "huwelijk" -> "huwelijksakte"
    "overlijden" -> "overlijdensakte"
    "doop" -> "doopakte"
    "bevolkingsregistratie" -> "bevolkingsregistratie"
    else -> "akte"
}

/**
 * Bouwt het `supported-answer`-antwoord uitsluitend uit gevalideerde Show-records (`Person`,
 * `Event`, `RelationEP`, `Source`). Iedere feitelijke zin krijgt direct erachter een genummerde
 * bronmarkering; vervolgsporen (max. twee) komen uit de `RelationEP` van het eerste record, in
 * de volgorde waarin ze daar voorkomen.
 */
@Component
class PersonSearchAnswerBuilder {

    fun build(records: List<ArchivesShowRecord>, checkedAt: Instant): PersonSearchAnswer {
        require(records.isNotEmpty()) { "Er is minstens één gevalideerd Show-record nodig." }

        val sources = records.mapIndexed { index, record ->
            val number = index + 1
            PersonSearchSourceCitation(
                number = number,
                institution = record.institution,
                sourceType = record.sourceType,
                archiveCode = record.archiveCode,
                identifier = record.identifier,
                archiveNumber = record.archiveNumber,
                registerNumber = record.registerNumber,
                deedNumber = record.deedNumber,
                recordNumber = record.recordNumber,
                openArchivesLink = "https://www.openarchieven.nl/${record.archiveCode}:${record.identifier}",
                digitalOriginalLink = record.digitalOriginalUrl,
                checkedAt = checkedAt,
            )
        }

        val sentences = mutableListOf<PersonSearchAnswerSentence>()
        records.forEachIndexed { index, record ->
            val number = index + 1
            sentences += PersonSearchAnswerSentence(eventVerbSentence(record), listOf(number))
            record.relations.forEach { relation ->
                sentences += PersonSearchAnswerSentence(
                    "${relation.personName} was de ${relation.role.lowercase(Locale.forLanguageTag("nl"))} van ${record.personName}.",
                    listOf(number),
                )
            }
        }

        val connections = records.first().relations
            .take(2)
            .map { PersonSearchConnectionOption(role = it.role, personName = it.personName) }

        val disclaimer = buildDisclaimer(records)

        return PersonSearchAnswer(sentences, sources, connections, disclaimer)
    }

    private fun buildDisclaimer(records: List<ArchivesShowRecord>): String {
        val years = records.map { eventYear(it.eventDate) }.distinct()
        val yearPhrase = if (years.size == 1) years.first() else years.joinToString(", ")
        return if (records.size == 1) {
            val record = records.first()
            "Deze ene ${eventNoun(record.eventType)} is geen volledig levensverhaal van ${record.personName} " +
                "en geen overzicht van alle gebeurtenissen in Heemskerk in $yearPhrase."
        } else {
            "Deze ${records.size} bronnen zijn samen geen volledig levensverhaal en geen overzicht van alle " +
                "gebeurtenissen in Heemskerk in $yearPhrase."
        }
    }
}
