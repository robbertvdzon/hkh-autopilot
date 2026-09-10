package nl.vdzon.hkh.topicsearch

/**
 * Deterministische, puur functionele afleiding van de rights-URL naar een leesbare
 * licentie-/rechtenbadge (tekst, niet uitsluitend kleur). De exacte CC-licentievariant-tekst wordt
 * letterlijk afgeleid uit het pad-segment na `licenses/` in de rights-URL.
 */
fun deriveTopicSearchLicenseBadge(rightsUrl: String?): TopicSearchLicenseBadge {
    if (rightsUrl.isNullOrBlank()) return TopicSearchLicenseBadge("Rechten onbekend", null)
    return when {
        rightsUrl.contains("creativecommons.org/publicdomain/mark") ->
            TopicSearchLicenseBadge("Publiek domein", rightsUrl)

        rightsUrl.contains("creativecommons.org/licenses/") -> {
            val variant = rightsUrl.substringAfter("creativecommons.org/licenses/").substringBefore("/")
            val text = if (variant.isBlank()) {
                "Rechten onbekend"
            } else {
                "CC " + variant.split("-").joinToString("-") { it.uppercase() }
            }
            TopicSearchLicenseBadge(text, rightsUrl)
        }

        rightsUrl.contains("rightsstatements.org/vocab/InC") ->
            TopicSearchLicenseBadge("Rechten voorbehouden", rightsUrl)

        else -> TopicSearchLicenseBadge("Rechten onbekend", rightsUrl)
    }
}

/**
 * Toetst en bouwt een geldig record uit ruwe Europeana-velden: (titel OF beschrijving) EN
 * dataProvider EN een geldige bronverwijzing (`edmIsShownAt`, anders het Europeana-record zelf via
 * `guid`). Ontbreekt één van deze, dan is het record ongeldig (`null`) en telt het niet mee, ook
 * niet voor het getoonde totaal.
 */
fun buildTopicSearchRecordOrNull(
    titles: List<String>?,
    descriptions: List<String>?,
    dataProviders: List<String>?,
    edmIsShownAt: List<String>?,
    guid: String?,
    rights: List<String>?,
): TopicSearchRecord? {
    val title = titles?.firstOrNull { it.isNotBlank() } ?: descriptions?.firstOrNull { it.isNotBlank() } ?: return null
    val provider = dataProviders?.firstOrNull { it.isNotBlank() } ?: return null
    val sourceUrl = edmIsShownAt?.firstOrNull { it.isNotBlank() } ?: guid?.takeIf { it.isNotBlank() } ?: return null
    val rightsUrl = rights?.firstOrNull { it.isNotBlank() }
    return TopicSearchRecord(
        title = title,
        dataProvider = provider,
        license = deriveTopicSearchLicenseBadge(rightsUrl),
        sourceUrl = sourceUrl,
    )
}
