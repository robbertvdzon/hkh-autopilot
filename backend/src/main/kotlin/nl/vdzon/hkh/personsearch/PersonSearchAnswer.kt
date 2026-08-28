package nl.vdzon.hkh.personsearch

import java.time.Instant

/** Genummerde bronmarkering direct achter een feitelijke antwoordzin. */
data class PersonSearchSourceCitation(
    val number: Int,
    val institution: String,
    val sourceType: String,
    val archiveCode: String,
    val identifier: String,
    val archiveNumber: String?,
    val registerNumber: String?,
    val deedNumber: String?,
    val recordNumber: String,
    val openArchivesLink: String,
    val digitalOriginalLink: String?,
    val checkedAt: Instant,
)

/** Eén feitelijke antwoordzin met de nummers van de bronmarkeringen die erachter staan. */
data class PersonSearchAnswerSentence(val text: String, val sourceNumbers: List<Int>)

/** Een vervolgspoor (`followed-connection`) naar een rol/persoon uit hetzelfde Show-record. */
data class PersonSearchConnectionOption(val role: String, val personName: String)

/** Wikidata-contextinformatie; draagt nooit zelfstandig een archiefbewering. */
data class PersonSearchWikidataContext(val label: String, val description: String?)

/** Volledig antwoord voor `supported-answer`. */
data class PersonSearchAnswer(
    val sentences: List<PersonSearchAnswerSentence>,
    val sources: List<PersonSearchSourceCitation>,
    val connections: List<PersonSearchConnectionOption>,
    val disclaimer: String,
)

/**
 * Worker-onafhankelijk statuscontract: uitvoerbaar door een gewone achtergrondworker (de gedeelde
 * executor), zonder afhankelijkheid van Agent Runtime. `QUEUED` geldt vanaf het indienen tot de
 * achtergrondtaak daadwerkelijk start; `CANCELLED` volgt op een expliciete stopactie; `EXPIRED`
 * volgt op retentie-opschoning (zie [PersonSearchJobStore]).
 */
enum class PersonSearchStatus {
    QUEUED,
    RUNNING,
    READY,
    NO_EVIDENCE,
    PARTIAL,
    FAILED,
    CANCELLED,
    EXPIRED,
}

/** Consultatiestatus van één externe bron (Open Archieven of Wikidata) voor een job. */
enum class PersonSearchSourceConsultationStatus {
    NOT_STARTED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
}

/** Terminale of niet-terminale uitkomst van een persoonszoekjob. */
sealed interface PersonSearchOutcome {
    val context: PersonSearchWikidataContext?

    data class SupportedAnswer(val answer: PersonSearchAnswer, override val context: PersonSearchWikidataContext?) :
        PersonSearchOutcome

    data class NoResults(override val context: PersonSearchWikidataContext?) : PersonSearchOutcome

    data class Partial(val refinementMessage: String, override val context: PersonSearchWikidataContext? = null) :
        PersonSearchOutcome

    data class SourceOutage(override val context: PersonSearchWikidataContext?) : PersonSearchOutcome
}

fun PersonSearchOutcome.toStatus(): PersonSearchStatus = when (this) {
    is PersonSearchOutcome.SupportedAnswer -> PersonSearchStatus.READY
    is PersonSearchOutcome.NoResults -> PersonSearchStatus.NO_EVIDENCE
    is PersonSearchOutcome.Partial -> PersonSearchStatus.PARTIAL
    is PersonSearchOutcome.SourceOutage -> PersonSearchStatus.FAILED
}
