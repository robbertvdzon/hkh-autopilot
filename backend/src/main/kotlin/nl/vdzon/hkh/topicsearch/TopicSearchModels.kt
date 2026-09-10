package nl.vdzon.hkh.topicsearch

import java.time.Instant

/** Leesbare licentie-/rechtenbadge: tekst, niet uitsluitend kleur; `url` verwijst naar de ruwe rights-URL of null wanneer die ontbreekt. */
data class TopicSearchLicenseBadge(val text: String, val url: String?)

/** Eén geldig Europeana-record: (titel OF beschrijving), dataProvider en een geldige bronverwijzing zijn al gevalideerd. */
data class TopicSearchRecord(
    val title: String,
    val dataProvider: String,
    val license: TopicSearchLicenseBadge,
    val sourceUrl: String,
)

/** Losstaand, apart gelabeld 'Context'-blok; draagt nooit zelfstandig een bewering over Heemskerk. */
data class TopicSearchContext(val label: String, val description: String?)

/** Volledig antwoord voor `topic-results`: elk record een apart kaartje, geen samenvattende zin. */
data class TopicSearchAnswer(
    val topicSearchTerm: String,
    val records: List<TopicSearchRecord>,
    val context: TopicSearchContext?,
    val checkedAt: Instant,
)

/** Terminale uitkomst van een onderwerp/voorwerp/gebeurtenis-zoekopdracht: altijd synchroon binnen het 2000ms-budget. */
sealed interface TopicSearchOutcome {
    /** Minstens 1 geldig Europeana-record. */
    data class Ready(val answer: TopicSearchAnswer) : TopicSearchOutcome

    /** Europeana bereikbaar, maar nul geldige records. */
    data class Empty(val checkedAt: Instant, val refinementSuggestions: List<String>) : TopicSearchOutcome

    /** Niet-2xx, time-out, ongeldige JSON van Europeana, of een ontbrekende/lege API-key (configuratiefout). */
    data object EuropeanaOutage : TopicSearchOutcome
}
