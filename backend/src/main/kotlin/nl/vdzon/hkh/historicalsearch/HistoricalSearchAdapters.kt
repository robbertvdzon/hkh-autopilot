package nl.vdzon.hkh.historicalsearch

import java.time.Clock
import java.time.Instant
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
                "Europeana is tijdelijk niet beschikbaar.")
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
        val items = root.get("items")?.takeIf(JsonNode::isArray)?.asIterable()?.toList().orEmpty()
            .filter(JsonNode::isObject)
            .mapNotNull { item ->
                val url = item.firstText("guid", "edm:isShownAt", "edmIsShownAt", "link", "url", "recordUrl").asHttpUrl()
                    ?: return@mapNotNull null
                val id = item.firstText("id", "identifier", "sourceRecordId", "about").asSafeText()
                    ?: return@mapNotNull null
                HistoricalSearchResult(
                    source = source,
                    sourceRecordId = id,
                    stableUrl = url,
                    title = item.firstText("title", "dcTitle", "dc:title").asSafeText(),
                    description = item.firstText("description", "dcDescription", "dc:description").asSafeText(),
                    person = item.firstText("who", "person", "dcCreator", "dc:creator").asSafeText(500),
                    event = item.firstText("event", "type", "dcType", "dc:type").asSafeText(500),
                    dateStart = item.firstText("dateStart", "year", "yearBegin", "edmTimespanLabel").asSafeText(100),
                    dateEnd = item.firstText("dateEnd", "yearEnd").asSafeText(100),
                    institution = item.firstText("institution", "edmProvider", "provider", "dataProvider").asSafeText(),
                    rights = item.firstText("rights", "dcRights", "dc:rights").asSafeText(500),
                    privacy = item.firstText("privacy", "privacyStatus", "personalDataStatus").asSafeText(500),
                    retrievedAt = retrievedAt,
                    metadataRights = explicitRights(item.firstText("metadataRights", "metadataRightsStatus")),
                    objectMediaRights = explicitRights(item.firstText("objectRights", "mediaRights", "objectMediaRightsStatus")),
                    privacyStatus = explicitPrivacy(item.firstText("privacyStatus", "privacy")),
                )
            }
        val total = root.firstInt("totalResults", "total", "count") ?: items.size
        return HistoricalSearchPage(source, items, total.coerceAtLeast(0), HistoricalTechnicalStatus.AVAILABLE)
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
) : HistoricalSearchAdapter {
    override val source: HistoricalSearchSource = HistoricalSearchSource.OPEN_ARCHIEVEN

    override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
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
        val response = root.get("response") ?: root
        val docs = response.get("docs")?.takeIf(JsonNode::isArray)?.asIterable()?.toList().orEmpty()
            .filter(JsonNode::isObject)
        val results = docs.mapNotNull { item ->
            val url = item.firstText("url", "stableUrl", "uri", "link").asHttpUrl() ?: return@mapNotNull null
            val id = item.firstText("identifier", "id", "pid", "sourceRecordId").asSafeText() ?: return@mapNotNull null
            val eventDate = item.get("eventdate")
            HistoricalSearchResult(
                source = source,
                sourceRecordId = id,
                stableUrl = url,
                title = item.firstText("title", "sourcetype", "eventtype", "_eventtype").asSafeText(),
                description = item.firstText("description", "source", "archive").asSafeText(),
                person = item.firstText("personname", "person", "name").asSafeText(500),
                event = item.firstText("eventtype", "_eventtype", "event").asSafeText(500),
                dateStart = eventDate?.let { it.firstText("year", "dateStart") }.asSafeText(100)
                    ?: item.firstText("dateStart", "year").asSafeText(100),
                dateEnd = eventDate?.let { it.firstText("yearEnd", "dateEnd") }.asSafeText(100)
                    ?: item.firstText("dateEnd").asSafeText(100),
                institution = item.firstText("archive_org", "archive", "institution").asSafeText(),
                rights = item.firstText("rights", "license").asSafeText(500),
                privacy = item.firstText("privacy", "privacyStatus").asSafeText(500),
                retrievedAt = retrievedAt,
                metadataRights = explicitRights(item.firstText("metadataRights", "metadataRightsStatus")),
                objectMediaRights = explicitRights(item.firstText("objectRights", "mediaRights", "objectMediaRightsStatus")),
                privacyStatus = explicitPrivacy(item.firstText("privacyStatus", "privacy")),
            )
        }
        val total = response.firstInt("number_found", "numberFound", "total") ?: results.size
        return HistoricalSearchPage(source, results, total.coerceAtLeast(0), HistoricalTechnicalStatus.AVAILABLE)
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

private fun JsonNode.firstText(vararg names: String): String? = names.asSequence()
    .mapNotNull { name -> scalarText(get(name)) }
    .firstOrNull()

private fun JsonNode.firstInt(vararg names: String): Int? = names.asSequence()
    .mapNotNull { name -> get(name)?.let { node -> node.asString().toIntOrNull() } }
    .firstOrNull()

private fun scalarText(node: JsonNode?): String? = when {
    node == null || node.isNull -> null
    node.isArray -> node.asIterable().mapNotNull(::scalarText).firstOrNull()
    node.isObject -> scalarText(node.get("@value") ?: node.get("value") ?: node.get("label"))
    else -> node.asString().takeIf(String::isNotBlank)
}
