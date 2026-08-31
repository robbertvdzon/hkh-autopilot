package nl.vdzon.hkh.placesearch

import java.time.Instant
import org.springframework.stereotype.Component

/** Reeds opgehaalde en label-opgeloste feiten van precies één Wikidata-item. */
data class PlaceEntityFacts(
    val qid: String,
    val label: String,
    val description: String?,
    val inceptionYear: String?,
    val architecturalStyleLabel: String?,
    val architectLabel: String?,
    val heritageStatusLabel: String?,
    val municipalityLabel: String?,
)

/**
 * Bouwt het `place-answer`-antwoord uitsluitend uit reeds gevalideerde Wikidata-claims van precies
 * één item (analoog aan `PersonSearchAnswerBuilder`, maar hier is er precies één bronitem in plaats
 * van één bron per record: elke feitelijke zin krijgt een eigen genummerde bronmarkering die naar
 * hetzelfde item verwijst).
 */
@Component
class PlaceSearchAnswerBuilder {

    fun build(facts: PlaceEntityFacts, checkedAt: Instant): PlaceSearchAnswer {
        val sources = mutableListOf<PlaceSearchSourceCitation>()
        val sentences = mutableListOf<PlaceSearchAnswerSentence>()
        var number = 0

        fun cite(text: String) {
            number++
            sources += PlaceSearchSourceCitation(number, facts.qid, wikidataItemLink(facts.qid), checkedAt)
            sentences += PlaceSearchAnswerSentence(text, listOf(number))
        }

        val introText = if (facts.description != null) {
            "${facts.label} is ${facts.description}."
        } else {
            "${facts.label}."
        }
        cite(introText)
        facts.inceptionYear?.let { cite("${facts.label} is opgericht of gebouwd in $it.") }
        facts.architecturalStyleLabel?.let { cite("${facts.label} heeft de architectuurstijl $it.") }
        facts.architectLabel?.let { cite("${facts.label} is ontworpen door $it.") }
        facts.heritageStatusLabel?.let { cite("${facts.label} heeft de erfgoedstatus $it.") }

        var contextSentence: PlaceSearchAnswerSentence? = null
        facts.municipalityLabel?.let { municipality ->
            number++
            sources += PlaceSearchSourceCitation(number, facts.qid, wikidataItemLink(facts.qid), checkedAt)
            contextSentence = PlaceSearchAnswerSentence("${facts.label} ligt in de gemeente $municipality.", listOf(number))
        }

        val disclaimer = "Dit is een actuele beschrijving van dit ene object uit Wikidata (${facts.qid}), " +
            "geen volledige geschiedschrijving van Heemskerk."

        return PlaceSearchAnswer(
            qid = facts.qid,
            label = facts.label,
            description = facts.description,
            sentences = sentences,
            contextSentence = contextSentence,
            sources = sources,
            images = emptyList(),
            disclaimer = disclaimer,
            checkedAt = checkedAt,
        )
    }
}
