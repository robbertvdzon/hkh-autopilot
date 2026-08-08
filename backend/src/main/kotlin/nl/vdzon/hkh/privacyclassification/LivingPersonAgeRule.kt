package nl.vdzon.hkh.privacyclassification

import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Machineleesbare uitkomst van [LivingPersonAgeRule] voor één genoemde persoon. */
enum class PersonAgeStatus {
    LIKELY_LIVING,
    DECEASED,
    UNKNOWN_FAILCLOSED,
}

/**
 * Bepaalt per genoemde persoon (hoofdpersoon of familielid) of deze vermoedelijk nog leeft,
 * volgens de FamilySearch 110/95-jaarregel: een extern gedocumenteerde vuistregel uit de
 * genealogiepraktijk (FamilySearch), *geen* wettelijke AVG-norm. De regel: een persoon is
 * vermoedelijk levend wanneer er geen overlijdens- of begrafenisdatum bekend is, én de persoon
 * jonger dan 110 jaar zou zijn (op basis van de geboortedatum), óf minder dan 95 jaar geleden
 * trouwde of een kind kreeg. Ontbrekende of onleesbare datumvelden leiden altijd tot een
 * fail-closed [PersonAgeStatus.UNKNOWN_FAILCLOSED]-uitkomst, nooit tot een automatische
 * vrijgave.
 *
 * Bekende beperking: datumvelden worden verwacht in ISO-8601 (`yyyy-MM-dd`); een datum met
 * uitsluitend een jaartal (`yyyy`) wordt ook geparsed, met 1 januari als impliciete dag. Fijnere
 * event-granulariteit (bijvoorbeeld enkel jaar-en-maand) wordt niet ondersteund en levert een
 * onparsbare, dus fail-closed uitkomst op.
 *
 * De tijdsbron ([clock]) is injecteerbaar zodat de regel deterministisch getest kan worden; in
 * productie is dit de systeemklok op het moment van classificeren.
 */
class LivingPersonAgeRule(private val clock: Clock = Clock.systemDefaultZone()) {

    fun evaluate(person: NamedPerson): PersonAgeStatus {
        val deathDate = parsedOrNull(person.deathDate) ?: return PersonAgeStatus.UNKNOWN_FAILCLOSED
        val burialDate = parsedOrNull(person.burialDate) ?: return PersonAgeStatus.UNKNOWN_FAILCLOSED
        if (deathDate.date != null || burialDate.date != null) {
            return PersonAgeStatus.DECEASED
        }

        val birthDate = parsedOrNull(person.birthDate) ?: return PersonAgeStatus.UNKNOWN_FAILCLOSED
        val marriageDate = parsedOrNull(person.marriageDate) ?: return PersonAgeStatus.UNKNOWN_FAILCLOSED
        val childBirthDate = parsedOrNull(person.childBirthDate) ?: return PersonAgeStatus.UNKNOWN_FAILCLOSED

        val today = LocalDate.now(clock)

        if (birthDate.date != null && yearsAgo(birthDate.date, today) <= 110) {
            return PersonAgeStatus.LIKELY_LIVING
        }

        val recentMarriageOrChild = listOfNotNull(marriageDate.date, childBirthDate.date)
            .any { yearsAgo(it, today) <= 95 }
        if (recentMarriageOrChild) {
            return PersonAgeStatus.LIKELY_LIVING
        }

        if (birthDate.date != null || marriageDate.date != null || childBirthDate.date != null) {
            return PersonAgeStatus.DECEASED
        }

        return PersonAgeStatus.UNKNOWN_FAILCLOSED
    }

    private fun yearsAgo(date: LocalDate, reference: LocalDate): Int = Period.between(date, reference).years

    /**
     * Parseert [raw] naar een optioneel geparsede datum. Retourneert `null` (fail-closed) wanneer
     * [raw] gezet is maar niet als geldige datum te parsen is; retourneert een lege [ParsedDate]
     * wanneer [raw] ontbreekt.
     */
    private fun parsedOrNull(raw: String?): ParsedDate? {
        val value = raw?.trim()
        if (value.isNullOrEmpty()) return ParsedDate(null)
        return try {
            ParsedDate(LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE))
        } catch (isoFailure: DateTimeParseException) {
            try {
                ParsedDate(LocalDate.of(value.toInt(), 1, 1))
            } catch (yearFailure: NumberFormatException) {
                null
            } catch (yearFailure: DateTimeException) {
                null
            }
        }
    }

    private data class ParsedDate(val date: LocalDate?)
}
