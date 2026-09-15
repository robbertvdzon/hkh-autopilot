package nl.vdzon.hkh.topicsearch

import java.net.URI

/**
 * Deterministische, puur functionele afleiding van de rights-URL naar een leesbare
 * licentie-/rechtenbadge (tekst, niet uitsluitend kleur). De exacte CC-licentievariant-tekst wordt
 * letterlijk afgeleid uit het pad-segment na `licenses/` in de rights-URL.
 */
fun deriveTopicSearchLicenseBadge(rightsUrl: String?): TopicSearchLicenseBadge {
    if (rightsUrl.isNullOrBlank()) return TopicSearchLicenseBadge("Rechten onbekend", null)
    val normalizedUrl = rightsUrl.lowercase()
    return when {
        normalizedUrl.contains("creativecommons.org/publicdomain/mark") ||
            normalizedUrl.contains("creativecommons.org/publicdomain/zero") ->
            TopicSearchLicenseBadge("Publiek domein", rightsUrl)

        normalizedUrl.contains("creativecommons.org/licenses/") -> {
            val variant = normalizedUrl.substringAfter("creativecommons.org/licenses/").substringBefore("/")
            val text = if (variant.isBlank()) {
                "Rechten onbekend"
            } else {
                "CC " + variant.split("-").joinToString("-") { it.uppercase() }
            }
            TopicSearchLicenseBadge(text, rightsUrl)
        }

        normalizedUrl.contains("rightsstatements.org/") && normalizedUrl.contains("inc") ->
            TopicSearchLicenseBadge("Rechten voorbehouden", rightsUrl)

        normalizedUrl.contains("europeana.eu/rights/rr-") ->
            TopicSearchLicenseBadge("Rechten voorbehouden", rightsUrl)

        else -> TopicSearchLicenseBadge("Rechten onbekend", rightsUrl)
    }
}

/**
 * Europeana zet in de `guid` van een record tracking-parameters, waaronder de gebruikte API-key als
 * `utm_campaign`. Die guid gaat als bronlink naar de publieke API-respons, de DOM, browserhistorie
 * en referrer-/proxylogs. De fallback gebruikt daarom uitsluitend het sleutelvrije deel van de URL:
 * schema, host en pad, zonder querystring en zonder fragment.
 */
private fun String.withoutQueryAndFragment(): String = substringBefore('#').substringBefore('?')

private fun String.isAbsoluteHttpUrl(): Boolean = try {
    val uri = URI(this)
    uri.isAbsolute && (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
        !uri.host.isNullOrBlank()
} catch (_: Exception) {
    false
}

/**
 * Toetst en bouwt een geldig record uit ruwe Europeana-velden: (titel OF beschrijving) EN
 * dataProvider EN een geldige bronverwijzing (`edmIsShownAt`, anders het Europeana-record zelf via
 * `guid` zonder diens tracking-querystring, zie [withoutQueryAndFragment]). Ontbreekt één van deze,
 * dan is het record ongeldig (`null`) en telt het niet mee, ook niet voor het getoonde totaal.
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
    val sourceUrl = edmIsShownAt?.firstOrNull { it.isAbsoluteHttpUrl() }
        ?: guid?.withoutQueryAndFragment()?.takeIf { it.isAbsoluteHttpUrl() }
        ?: return null
    val rightsUrl = rights?.firstOrNull { it.isNotBlank() }
    return TopicSearchRecord(
        title = title,
        dataProvider = provider,
        license = deriveTopicSearchLicenseBadge(rightsUrl),
        sourceUrl = sourceUrl,
    )
}
