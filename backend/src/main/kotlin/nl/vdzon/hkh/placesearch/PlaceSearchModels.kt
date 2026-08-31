package nl.vdzon.hkh.placesearch

import java.time.Instant

/** Label van een kandidaat die binnen Heemskerk viel, gebruikt als verfijningsvoorstel bij >1 match. */
data class PlaceSearchCandidateSummary(val qid: String, val label: String)

/** Genummerde bronmarkering: verwijst altijd naar hetzelfde Wikidata-item (er is precies één bron). */
data class PlaceSearchSourceCitation(
    val number: Int,
    val qid: String,
    val wikidataLink: String,
    val checkedAt: Instant,
)

/** Eén feitelijke antwoordzin met de nummers van de bronmarkeringen erachter. */
data class PlaceSearchAnswerSentence(val text: String, val sourceNumbers: List<Int>)

/** Eén Commons-afbeelding, gededupliceerd op bestandsnaam. */
data class PlaceSearchImage(
    val url: String,
    val fileName: String,
    val license: String?,
    val filePageUrl: String,
)

/** Volledig antwoord voor `place-answer`. */
data class PlaceSearchAnswer(
    val qid: String,
    val label: String,
    val description: String?,
    val sentences: List<PlaceSearchAnswerSentence>,
    val contextSentence: PlaceSearchAnswerSentence?,
    val sources: List<PlaceSearchSourceCitation>,
    val images: List<PlaceSearchImage>,
    val disclaimer: String,
    val checkedAt: Instant,
)

/** Terminale uitkomst van een plek/gebouw-zoekopdracht: altijd synchroon binnen het 2000ms-budget. */
sealed interface PlaceSearchOutcome {
    /** Precies 1 match binnen Heemskerk. [commonsOutage] onderscheidt een mislukte Commons-aanroep
     * van een legitiem lege beeldgalerij (geen categorie/P18 of nul resultaten). */
    data class SupportedAnswer(val answer: PlaceSearchAnswer, val commonsOutage: Boolean) : PlaceSearchOutcome

    /** 0 of >1 match binnen Heemskerk; [candidates] is alleen gevuld bij >1 match. */
    data class NoMatch(val candidates: List<PlaceSearchCandidateSummary>) : PlaceSearchOutcome

    /** Wikidata-fout, timeout, ongeldige JSON, ontbrekend verplicht veld, of het 2000ms-budget overschreden. */
    data object WikidataOutage : PlaceSearchOutcome
}
