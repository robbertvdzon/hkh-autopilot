package nl.vdzon.hkh.historicalsearch

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

private const val USER_AGENT = "HKH-Autopilot-HistoricalSearch/1.0"

@Configuration
class HistoricalSearchConfiguration(
    @param:Value("\${hkh.historical.europeana-base-url:https://api.europeana.eu}")
    private val europeanaBaseUrl: String,
    @param:Value("\${hkh.historical.europeana-wskey:}")
    private val europeanaWskey: String,
    @param:Value("\${hkh.historical.open-archieven-base-url:https://api.openarchieven.nl/1.1}")
    private val openArchievenBaseUrl: String,
) {
    @Bean
    fun historicalSearchRateLimiter(): HistoricalSearchRateLimiter = FourPerSecondHistoricalRateLimiter()

    @Bean
    fun europeanaSearchAdapter(): EuropeanaSearchAdapter = EuropeanaSearchAdapter(
        restClient = RestClient.builder().baseUrl(europeanaBaseUrl).build(),
        wskey = europeanaWskey,
    )

    @Bean
    fun openArchievenSearchAdapter(rateLimiter: HistoricalSearchRateLimiter): OpenArchievenSearchAdapter =
        OpenArchievenSearchAdapter(
            restClient = RestClient.builder().baseUrl(openArchievenBaseUrl).build(),
            rateLimiter = rateLimiter,
            configured = openArchievenBaseUrl.isNotBlank(),
        )
}

fun interface HistoricalSearchRateLimiter {
    fun awaitPermit()
}

class FourPerSecondHistoricalRateLimiter(
    private val intervalNanos: Long = 251_000_000L,
    private val nowNanos: () -> Long = System::nanoTime,
    private val sleepNanos: (Long) -> Unit = { nanos ->
        Thread.sleep(nanos / 1_000_000L, (nanos % 1_000_000L).toInt())
    },
) : HistoricalSearchRateLimiter {
    private var nextPermitAt = Long.MIN_VALUE

    override fun awaitPermit() {
        synchronized(this) {
            val now = nowNanos()
            val next = maxOf(now, nextPermitAt)
            val wait = next - now
            if (wait > 0) sleepNanos(wait)
            val afterWait = nowNanos()
            nextPermitAt = maxOf(afterWait, next) + intervalNanos
        }
    }
}

class EuropeanaSearchAdapter(
    private val restClient: RestClient,
    private val wskey: String,
    private val clock: Clock = Clock.systemUTC(),
    private val objectMapper: ObjectMapper = JsonMapper.builder().build(),
) : HistoricalSearchAdapter {
    override val source: HistoricalSearchSource = HistoricalSearchSource.EUROPEANA

    override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
        if (wskey.isBlank()) {
            return HistoricalSearchPage(source, emptyList(), 0, HistoricalTechnicalStatus.DISABLED,
                "Europeana is niet geconfigureerd.")
        }
        val q = listOfNotNull(query.text, query.event).joinToString(" ").takeIf(String::isNotBlank)
            ?: query.person ?: query.place
            ?: return HistoricalSearchPage(source, emptyList(), 0, HistoricalTechnicalStatus.INVALID_RESPONSE,
                "Europeana vereist een vrije zoekterm, persoon, plek of gebeurtenis.")
        val qfs = buildList {
            query.person?.let { add("who:$it") }
            query.place?.let { add("where:$it") }
            if (query.fromYear != null && query.toYear != null) add("YEAR:[${query.fromYear} TO ${query.toYear}]")
            else if (query.fromYear != null) add("YEAR:[${query.fromYear} TO *]")
            else if (query.toYear != null) add("YEAR:[* TO ${query.toYear}]")
        }
        val fetchedAt = Instant.now(clock)
        val response = runCatching {
            restClient.get().uri { builder ->
                var uri = UriComponentsBuilder.fromUri(builder.build()).path("/record/v2/search.json")
                    .queryParam("wskey", wskey)
                    .queryParam("query", q)
                    .queryParam("rows", query.limit)
                    .queryParam("start", query.start)
                qfs.forEach { uri = uri.queryParam("qf", it) }
                uri.build().toUri()
            }.headers { headers -> headers.set(HttpHeaders.USER_AGENT, USER_AGENT) }
                .retrieve().body(String::class.java)
        }.getOrNull() ?: return unavailable(fetchedAt)
        if (response.isBlank()) return unavailable(fetchedAt, HistoricalTechnicalStatus.INVALID_RESPONSE)
        return runCatching { parse(response, fetchedAt) }.getOrElse {
            unavailable(fetchedAt, HistoricalTechnicalStatus.INVALID_RESPONSE)
        }
    }

    private fun parse(body: String, retrievedAt: Instant): HistoricalSearchPage {
        val root = objectMapper.readTree(body)
        require(root.isObject) { "Europeana-respons is geen JSON-object" }
        val itemsNode = root.get("items")
        require(itemsNode?.isArray == true) { "Europeana-respons bevat geen resultaatarray" }
        val rawItems = itemsNode.asIterable().toList()
        val items = rawItems
            .filter(JsonNode::isObject)
            .mapNotNull { item ->
                val url = item.consistentText(2_000, "guid", "edm:isShownAt", "edmIsShownAt", "link", "url", "recordUrl").asHttpUrl()
                    ?: return@mapNotNull null
                val id = item.consistentText(2_000, "id", "identifier", "sourceRecordId", "about").asSafeText()
                    ?: return@mapNotNull null
                val startDate = item.consistentText(100, "dateStart", "year", "yearBegin", "edmTimespanLabel")
                val endDate = item.consistentText(100, "dateEnd", "yearEnd")
                val dates = normalizeDateRange(startDate, endDate)
                HistoricalSearchResult(
                    source = source,
                    sourceRecordId = id,
                    stableUrl = url,
                    title = item.consistentText(2_000, "title", "dcTitle", "dc:title"),
                    description = item.consistentText(2_000, "description", "dcDescription", "dc:description"),
                    person = item.consistentText(500, "who", "person", "dcCreator", "dc:creator"),
                    event = item.consistentText(500, "event", "type", "dcType", "dc:type"),
                    dateStart = dates?.first,
                    dateEnd = dates?.second,
                    institution = item.consistentText(2_000, "institution", "edmProvider", "provider", "dataProvider"),
                    rights = item.consistentText(500, "rights", "dcRights", "dc:rights"),
                    privacy = item.consistentText(500, "privacy", "privacyStatus", "personalDataStatus"),
                    retrievedAt = retrievedAt,
                    metadataRights = explicitRights(item.consistentText(500, "metadataRights", "metadataRightsStatus")),
                    objectMediaRights = explicitRights(item.consistentText(500, "objectRights", "mediaRights", "objectMediaRightsStatus")),
                    privacyStatus = explicitPrivacy(item.consistentText(500, "privacyStatus", "privacy")),
                ).failClosedMetadata()
            }
        val total = root.firstInt("totalResults", "total", "count") ?: items.size
        return HistoricalSearchPage(
            source, items, total.coerceAtLeast(0), HistoricalTechnicalStatus.AVAILABLE, consumed = rawItems.size,
        )
    }

    private fun unavailable(at: Instant, status: HistoricalTechnicalStatus = HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE) =
        HistoricalSearchPage(source, emptyList(), 0, status, "Europeana kon niet worden bevraagd.")

    private fun explicitRights(value: String?): HistoricalRightsStatus = when (value?.trim()?.uppercase()) {
        "ALLOWED", "OPEN", "PUBLIC", "PERMITTED" -> HistoricalRightsStatus.ALLOWED
        "RESTRICTED", "CLOSED", "LIMITED" -> HistoricalRightsStatus.RESTRICTED
        else -> HistoricalRightsStatus.UNKNOWN
    }

    private fun explicitPrivacy(value: String?): HistoricalPrivacyStatus = when (value?.trim()?.uppercase()) {
        "CLEAR", "PUBLIC", "NO_PERSONAL_DATA", "NO_PERSONS" -> HistoricalPrivacyStatus.CLEAR
        "BLOCKED", "RESTRICTED", "PERSONAL_DATA", "LIVING_PERSON" -> HistoricalPrivacyStatus.BLOCKED
        else -> HistoricalPrivacyStatus.UNKNOWN
    }
}

class OpenArchievenSearchAdapter(
    private val restClient: RestClient,
    private val rateLimiter: HistoricalSearchRateLimiter,
    private val clock: Clock = Clock.systemUTC(),
    private val objectMapper: ObjectMapper = JsonMapper.builder().build(),
    private val configured: Boolean = true,
) : HistoricalSearchAdapter {
    override val source: HistoricalSearchSource = HistoricalSearchSource.OPEN_ARCHIEVEN

    override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
        if (!configured) {
            return HistoricalSearchPage(source, emptyList(), 0, HistoricalTechnicalStatus.DISABLED,
                "Open Archieven is niet geconfigureerd.")
        }
        val name = buildNameQuery(query)
            ?: return HistoricalSearchPage(source, emptyList(), 0, HistoricalTechnicalStatus.INVALID_RESPONSE,
                "Open Archieven vereist een zoekterm, persoon of gebeurtenis.")
        rateLimiter.awaitPermit()
        val fetchedAt = Instant.now(clock)
        val response = runCatching {
            restClient.get().uri { builder ->
                UriComponentsBuilder.fromUri(builder.build()).path("/records/search.json")
                    .queryParam("name", name)
                    .queryParam("number_show", query.limit.coerceAtMost(100))
                    .queryParam("start", query.start)
                    .let { uri ->
                        query.place?.let { uri.queryParam("eventplace", it) } ?: uri
                    }
                    .build().toUri()
            }.headers { headers -> headers.set(HttpHeaders.USER_AGENT, USER_AGENT) }
                .retrieve().body(String::class.java)
        }.getOrNull() ?: return unavailable(fetchedAt)
        if (response.isBlank()) return unavailable(fetchedAt, HistoricalTechnicalStatus.INVALID_RESPONSE)
        return runCatching { parse(response, fetchedAt) }.getOrElse {
            unavailable(fetchedAt, HistoricalTechnicalStatus.INVALID_RESPONSE)
        }
    }

    private fun buildNameQuery(query: HistoricalSearchQuery): String? {
        val primary = query.person ?: query.text ?: query.place
        val term = when {
            primary != null && query.event != null -> "$primary ~${query.event}"
            primary != null -> primary
            query.event != null -> "~${query.event}"
            else -> null
        }
        if (term == null) return null
        val year = when {
            query.fromYear != null && query.toYear != null -> " ${query.fromYear}-${query.toYear}"
            query.fromYear != null -> " ${query.fromYear}-"
            query.toYear != null -> " -${query.toYear}"
            else -> ""
        }
        return (term + year).trim()
    }

    private fun parse(body: String, retrievedAt: Instant): HistoricalSearchPage {
        val root = objectMapper.readTree(body)
        if (root.get("error_code") != null || root.get("error_description") != null) {
            return HistoricalSearchPage(
                source = source,
                results = emptyList(),
                total = 0,
                status = HistoricalTechnicalStatus.INVALID_RESPONSE,
                message = "Open Archieven retourneerde een foutrespons.",
            )
        }
        val response = root.get("response") ?: root
        require(response.isObject) { "Open Archieven-respons bevat geen response-object" }
        val docsNode = response.get("docs")
        require(docsNode?.isArray == true) { "Open Archieven-respons bevat geen resultaatarray" }
        val rawDocs = docsNode.asIterable().toList()
        val docs = rawDocs.filter(JsonNode::isObject)
        val results = docs.mapNotNull { item ->
            val url = item.consistentText(2_000, "url", "stableUrl", "uri", "link").asHttpUrl() ?: return@mapNotNull null
            val id = item.consistentText(2_000, "identifier", "id", "pid", "sourceRecordId").asSafeText()
                ?: return@mapNotNull null
            val eventDate = item.get("eventdate")
            val startDate = if (eventDate != null) {
                eventDate.consistentText(100, "year", "dateStart")
            } else {
                item.consistentText(100, "dateStart", "year")
            }
            val endDate = if (eventDate != null) {
                eventDate.consistentText(100, "yearEnd", "dateEnd")
            } else {
                item.consistentText(100, "dateEnd")
            }
            val dates = normalizeDateRange(startDate, endDate)
            HistoricalSearchResult(
                source = source,
                sourceRecordId = id,
                stableUrl = url,
                title = item.consistentText(2_000, "title", "sourcetype", "eventtype", "_eventtype"),
                description = item.consistentText(2_000, "description", "source", "archive"),
                person = item.consistentText(500, "personname", "person", "name"),
                event = item.consistentText(500, "eventtype", "_eventtype", "event"),
                dateStart = dates?.first,
                dateEnd = dates?.second,
                institution = item.consistentText(2_000, "archive_org", "archive", "institution"),
                rights = item.consistentText(500, "rights", "license"),
                privacy = item.consistentText(500, "privacy", "privacyStatus"),
                retrievedAt = retrievedAt,
                metadataRights = explicitRights(item.consistentText(500, "metadataRights", "metadataRightsStatus")),
                objectMediaRights = explicitRights(item.consistentText(500, "objectRights", "objectMediaRightsStatus")),
                privacyStatus = explicitPrivacy(item.consistentText(500, "privacyStatus", "privacy")),
            ).failClosedMetadata()
        }
        val total = response.firstInt("number_found", "numberFound", "total") ?: results.size
        return HistoricalSearchPage(
            source, results, total.coerceAtLeast(0), HistoricalTechnicalStatus.AVAILABLE, consumed = rawDocs.size,
        )
    }

    private fun unavailable(at: Instant, status: HistoricalTechnicalStatus = HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE) =
        HistoricalSearchPage(source, emptyList(), 0, status, "Open Archieven kon niet worden bevraagd.")

    private fun explicitRights(value: String?): HistoricalRightsStatus = when (value?.trim()?.uppercase()) {
        "ALLOWED", "OPEN", "PUBLIC", "PERMITTED" -> HistoricalRightsStatus.ALLOWED
        "RESTRICTED", "CLOSED", "LIMITED" -> HistoricalRightsStatus.RESTRICTED
        else -> HistoricalRightsStatus.UNKNOWN
    }

    private fun explicitPrivacy(value: String?): HistoricalPrivacyStatus = when (value?.trim()?.uppercase()) {
        "CLEAR", "PUBLIC", "NO_PERSONAL_DATA", "NO_PERSONS" -> HistoricalPrivacyStatus.CLEAR
        "BLOCKED", "RESTRICTED", "PERSONAL_DATA", "LIVING_PERSON" -> HistoricalPrivacyStatus.BLOCKED
        else -> HistoricalPrivacyStatus.UNKNOWN
    }
}

private fun JsonNode.consistentText(maxLength: Int, vararg names: String): String? {
    val values = names.flatMap { name -> scalarTexts(get(name)) }
    val nonBlank = values.map(String::trim).filter(String::isNotBlank)
    if (nonBlank.any { it.length > maxLength || it.any(Char::isISOControl) }) return null
    return nonBlank.distinct().singleOrNull()
}

private fun JsonNode.firstInt(vararg names: String): Int? = names.asSequence()
    .mapNotNull { name -> get(name)?.let { node -> node.asString().toIntOrNull() } }
    .firstOrNull()

private fun scalarTexts(node: JsonNode?): List<String> = when {
    node == null || node.isNull -> emptyList()
    node.isArray -> node.asIterable().flatMap(::scalarTexts)
    node.isObject -> scalarTexts(node.get("@value") ?: node.get("value") ?: node.get("label"))
    else -> listOf(node.asString())
}

private fun normalizeDateRange(start: String?, end: String?): Pair<String?, String?>? {
    val normalizedStart = start?.let { normalizeDate(it) ?: return null }
    val normalizedEnd = end?.let { normalizeDate(it) ?: return null }
    if (normalizedStart == null && normalizedEnd == null) return null
    if (normalizedStart != null && normalizedEnd != null && normalizedStart.comparable > normalizedEnd.comparable) return null
    return normalizedStart?.value to normalizedEnd?.value
}

private data class NormalizedDate(val value: String, val comparable: LocalDate)

private fun normalizeDate(value: String): NormalizedDate? {
    val cleaned = value.trim()
    if (Regex("\\d{4}").matches(cleaned)) {
        return NormalizedDate(cleaned, LocalDate.of(cleaned.toInt(), 1, 1))
    }
    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(cleaned)) return null
    return runCatching { NormalizedDate(cleaned, LocalDate.parse(cleaned)) }.getOrNull()
}
