package nl.vdzon.hkh.historicalsearch

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClientResponseException
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
    @param:Value("\${hkh.historical.open-archieven-timeout:10s}")
    private val openArchievenTimeout: Duration,
    @param:Value("\${hkh.historical.open-archieven-cache-duration:30s}")
    private val openArchievenCacheDuration: Duration,
    @param:Value("\${hkh.historical.trusted-proxy-addresses:}")
    private val trustedProxyAddresses: String,
) {
    @Bean
    fun historicalSearchRateLimiter(): HistoricalSearchRateLimiter = FourPerSecondHistoricalRateLimiter()

    @Bean
    fun historicalSearchRequestBudget(): HistoricalSearchRequestBudget =
        SlidingWindowHistoricalSearchRequestBudget()

    @Bean
    fun openArchievenResponseCache(): OpenArchievenResponseCache =
        OpenArchievenResponseCache(ttl = openArchievenCacheDuration)

    @Bean
    fun historicalClientIpResolver(): HistoricalClientIpResolver = HistoricalClientIpResolver(
        trustedProxyAddresses = trustedProxyAddresses.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet(),
    )

    @Bean
    fun europeanaSearchAdapter(): EuropeanaSearchAdapter = EuropeanaSearchAdapter(
        restClient = RestClient.builder().baseUrl(europeanaBaseUrl).build(),
        wskey = europeanaWskey,
    )

    @Bean
    fun openArchievenSearchAdapter(rateLimiter: HistoricalSearchRateLimiter): OpenArchievenSearchAdapter =
        OpenArchievenSearchAdapter(
            restClient = openArchievenRestClient(openArchievenBaseUrl, openArchievenTimeout),
            rateLimiter = rateLimiter,
            configured = openArchievenBaseUrl.isNotBlank(),
            requestBudget = historicalSearchRequestBudget(),
            responseCache = openArchievenResponseCache(),
        )

    private fun openArchievenRestClient(baseUrl: String, timeout: Duration): RestClient {
        val requestFactory = JdkClientHttpRequestFactory()
        requestFactory.setReadTimeout(timeout)
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
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
                val place = item.contextField(500, "place", "where", "location", "dcCoverage", "dc:coverage", "edmPlaceLabel")
                val person = item.contextField(500, "who", "person", "dcCreator", "dc:creator")
                val event = item.contextField(500, "event", "type", "dcType", "dc:type")
                val startDate = item.consistentText(100, "dateStart", "year", "yearBegin", "edmTimespanLabel")
                val endDate = item.consistentText(100, "dateEnd", "yearEnd")
                val dates = normalizeDateRange(startDate, endDate)
                HistoricalSearchResult(
                    source = source,
                    sourceRecordId = id,
                    stableUrl = url,
                    title = item.consistentText(2_000, "title", "dcTitle", "dc:title"),
                    description = item.consistentText(2_000, "description", "dcDescription", "dc:description"),
                    place = place.value,
                    person = person.value,
                    event = event.value,
                    dateStart = dates?.first,
                    dateEnd = dates?.second,
                    institution = item.consistentText(2_000, "institution", "edmProvider", "provider", "dataProvider"),
                    rights = item.consistentText(500, "rights", "dcRights", "dc:rights"),
                    privacy = item.consistentText(500, "privacy", "privacyStatus", "personalDataStatus"),
                    retrievedAt = retrievedAt,
                    metadataRights = parseHistoricalRights(item.consistentText(500, "metadataRights", "metadataRightsStatus")),
                    objectMediaRights = parseHistoricalRights(item.consistentText(500, "objectRights", "mediaRights", "objectMediaRightsStatus")),
                    privacyStatus = explicitPrivacy(item.consistentText(500, "privacyStatus", "privacy")),
                    placeStatus = place.status,
                    personStatus = person.status,
                    eventStatus = event.status,
                    relationships = item.explicitRelationships(),
                ).failClosedMetadata()
            }
        val total = root.firstInt("totalResults", "total", "count") ?: items.size
        return HistoricalSearchPage(
            source, items, total.coerceAtLeast(0), HistoricalTechnicalStatus.AVAILABLE, consumed = rawItems.size,
        )
    }

    private fun unavailable(at: Instant, status: HistoricalTechnicalStatus = HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE) =
        HistoricalSearchPage(source, emptyList(), 0, status, "Europeana kon niet worden bevraagd.")

    private fun explicitPrivacy(value: String?): HistoricalPrivacyStatus = when (value?.trim()?.uppercase()) {
        "CLEAR", "PUBLIC", "NO_PERSONAL_DATA", "NO_PERSONS" -> HistoricalPrivacyStatus.CLEAR
        "BLOCKED", "RESTRICTED", "PERSONAL_DATA", "LIVING_PERSON" -> HistoricalPrivacyStatus.BLOCKED
        else -> HistoricalPrivacyStatus.UNKNOWN
    }
}

private fun Exception.transportStatus(): HistoricalTechnicalStatus = when {
    this is RestClientResponseException -> HistoricalTechnicalStatus.HTTP_ERROR
    hasCause { it is SocketTimeoutException || it is HttpTimeoutException || it is TimeoutException } ->
        HistoricalTechnicalStatus.TIMEOUT
    else -> HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE
}

private fun Throwable.hasCause(predicate: (Throwable) -> Boolean): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (predicate(current)) return true
        current = current.cause
    }
    return false
}

class OpenArchievenSearchAdapter(
    private val restClient: RestClient,
    private val rateLimiter: HistoricalSearchRateLimiter,
    private val clock: Clock = Clock.systemUTC(),
    private val objectMapper: ObjectMapper = JsonMapper.builder().build(),
    private val configured: Boolean = true,
    private val requestBudget: HistoricalSearchRequestBudget = AllowAllHistoricalSearchRequestBudget,
    private val responseCache: OpenArchievenResponseCache = OpenArchievenResponseCache(clock = clock),
    private val retrySleeper: (Duration) -> Unit = { duration ->
        if (!duration.isZero) Thread.sleep(duration.toMillis())
    },
) : HistoricalSearchAdapter {
    override val source: HistoricalSearchSource = HistoricalSearchSource.OPEN_ARCHIEVEN
    private val log = LoggerFactory.getLogger(OpenArchievenSearchAdapter::class.java)
    private val inFlight = mutableMapOf<OpenArchievenCacheKey, CompletableFuture<HistoricalSearchPage>>()

    /** Exposed for contract tests; the digest is the only representation retained by the cache. */
    fun cacheKeyFor(query: HistoricalSearchQuery): OpenArchievenCacheKey {
        val normalized = listOf(
            "source=${source.name}",
            "language=nl",
            "text=${cacheContextValue(query.text)}",
            "place=${cacheContextValue(query.place)}",
            "person=${cacheContextValue(query.person)}",
            "event=${cacheContextValue(query.event)}",
            "fromYear=${query.fromYear ?: "<null>"}",
            "toYear=${query.toYear ?: "<null>"}",
            "archive_code=${if (isHeemskerkSearch(query)) "hee" else "<none>"}",
        ).joinToString("|")
        return OpenArchievenCacheKey(
            source = source,
            normalizedContextDigest = sha256Hex(normalized),
            start = query.start,
            limit = query.limit.coerceAtMost(100),
            language = "nl",
        )
    }

    override fun search(query: HistoricalSearchQuery): HistoricalSearchPage {
        val key = cacheKeyFor(query)
        var leader = false
        val future = synchronized(inFlight) {
            responseCache.get(key)?.let { return it }
            inFlight[key]?.let { return@synchronized it }
            leader = true
            CompletableFuture<HistoricalSearchPage>().also { inFlight[key] = it }
        }
        if (!leader) return await(future)

        try {
            val page = searchUncached(query)
            responseCache.put(key, page)
            future.complete(page)
            return page
        } catch (exception: Exception) {
            future.completeExceptionally(exception)
            throw exception
        } finally {
            synchronized(inFlight) {
                if (inFlight[key] === future) inFlight.remove(key)
            }
        }
    }

    private fun searchUncached(query: HistoricalSearchQuery): HistoricalSearchPage {
        val startedAt = System.nanoTime()
        var outcome: HistoricalTechnicalStatus? = null
        var httpStatusClass: String? = null
        var processedResultCount: Int? = null
        var actualAttempts = 0
        var intermediateAttemptLogged = false

        return try {
            val page = searchPage(
                query = query,
                onHttpStatus = { statusCode -> httpStatusClass = statusCode.toHttpStatusClass() },
                onAttempt = { actualAttempts++ },
                onRetryScheduled = {
                    intermediateAttemptLogged = true
                    logDiagnostic(
                        outcome = HistoricalTechnicalStatus.RATE_LIMITED,
                        durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                        httpStatusClass = "4xx",
                        processedResultCount = null,
                    )
                },
            )
            outcome = page.status
            processedResultCount = page.results.size.takeIf {
                page.status == HistoricalTechnicalStatus.AVAILABLE
            }
            page
        } catch (exception: HistoricalSearchRequestBudgetExceededException) {
            throw exception
        } catch (_: Exception) {
            outcome = HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE
            unavailable(Instant.now(clock), HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE)
        } finally {
            if (!intermediateAttemptLogged || actualAttempts > 1 || actualAttempts == 0) {
                logDiagnostic(
                    outcome = outcome ?: HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE,
                    durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                    httpStatusClass = httpStatusClass,
                    processedResultCount = processedResultCount,
                )
            }
        }
    }

    private fun await(future: CompletableFuture<HistoricalSearchPage>): HistoricalSearchPage = try {
        future.get()
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IllegalStateException("Open Archieven-aanvraag onderbroken")
    } catch (exception: java.util.concurrent.ExecutionException) {
        throw (exception.cause ?: exception)
    }

    private fun searchPage(
        query: HistoricalSearchQuery,
        onHttpStatus: (Int) -> Unit,
        onAttempt: () -> Unit,
        onRetryScheduled: () -> Unit,
    ): HistoricalSearchPage {
        if (!configured) {
            return HistoricalSearchPage(source, emptyList(), 0, HistoricalTechnicalStatus.DISABLED,
                "Open Archieven is niet geconfigureerd.")
        }
        val name = buildNameQuery(query)
            ?: return HistoricalSearchPage(source, emptyList(), 0, HistoricalTechnicalStatus.INVALID_RESPONSE,
                "Open Archieven vereist een zoekterm, persoon of gebeurtenis.")
        var retry = false
        while (true) {
            if (!reserveAttempt(retry)) {
                return unavailable(Instant.now(clock), HistoricalTechnicalStatus.RATE_LIMITED)
            }
            rateLimiter.awaitPermit()
            val fetchedAt = Instant.now(clock)
            onAttempt()
            val response = try {
                restClient.get().uri { builder ->
                    var uri = UriComponentsBuilder.fromUri(builder.build()).path("/records/search.json")
                        .queryParam("name", name)
                        .queryParam("number_show", query.limit.coerceAtMost(100))
                        .queryParam("start", query.start)
                    if (isHeemskerkSearch(query)) uri = uri.queryParam("archive_code", "hee")
                    query.place?.let { uri = uri.queryParam("eventplace", it) }
                    uri.build().toUri()
                }.headers { headers -> headers.set(HttpHeaders.USER_AGENT, USER_AGENT) }
                    .exchange { _, clientResponse ->
                        OpenArchievenHttpResponse(
                            statusCode = clientResponse.statusCode.value(),
                            body = clientResponse.body.readAllBytes().toString(
                                clientResponse.headers.contentType?.charset ?: StandardCharsets.UTF_8,
                            ),
                            retryAfter = clientResponse.headers.getFirst("Retry-After"),
                        )
                    }
            } catch (exception: Exception) {
                return unavailable(fetchedAt, exception.transportStatus())
            }
            onHttpStatus(response.statusCode)
            if (response.statusCode == 429) {
                if (!retry) {
                    retryAfter(response.retryAfter)?.let {
                        onRetryScheduled()
                        retrySleeper(it)
                        retry = true
                        continue
                    }
                }
                return unavailable(fetchedAt, HistoricalTechnicalStatus.RATE_LIMITED)
            }
            if (response.statusCode !in 200..299) {
                return unavailable(fetchedAt, HistoricalTechnicalStatus.HTTP_ERROR)
            }
            val body = response.body
            if (body.isBlank()) return unavailable(fetchedAt, HistoricalTechnicalStatus.INVALID_JSON)
            val root = try {
                objectMapper.readTree(body)
            } catch (_: Exception) {
                return unavailable(fetchedAt, HistoricalTechnicalStatus.INVALID_JSON)
            }
            return try {
                parse(root, fetchedAt)
            } catch (_: Exception) {
                unavailable(fetchedAt, HistoricalTechnicalStatus.MISSING_REQUIRED_FIELDS)
            }
        }
    }

    private fun reserveAttempt(retry: Boolean): Boolean {
        val identity = HistoricalSearchRequestContext.current()
        val budget = identity?.budget ?: requestBudget
        val clientIp = identity?.clientIp ?: "unknown"
        if (budget.tryAcquire(clientIp)) return true
        if (retry) return false
        throw HistoricalSearchRequestBudgetExceededException()
    }

    private fun cacheContextValue(value: String?): String = value
        ?.let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFKC) }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.lowercase(java.util.Locale.ROOT)
        ?.takeIf(String::isNotEmpty)
        ?: "<null>"

    private fun retryAfter(raw: String?): Duration? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val seconds = value.toLongOrNull()
        if (seconds != null) return seconds.takeIf { it in 0..2 }?.let(Duration::ofSeconds)
        val retryAt = runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull() ?: return null
        val duration = Duration.between(clock.instant(), retryAt)
        return duration.takeIf { !it.isNegative && it <= Duration.ofSeconds(2) }
    }

    private fun logDiagnostic(
        outcome: HistoricalTechnicalStatus,
        durationMs: Long,
        httpStatusClass: String?,
        processedResultCount: Int?,
    ) {
        log.info(
            "event=OPEN_ARCHIEVEN_SEARCH source=OPEN_ARCHIEVEN outcome={} durationMs={} httpStatusClass={} processedResultCount={}",
            outcome.name,
            durationMs,
            httpStatusClass ?: "",
            processedResultCount?.toString() ?: "",
        )
    }

    private fun isHeemskerkSearch(query: HistoricalSearchQuery): Boolean =
        listOf(query.text, query.place, query.person)
            .any { normalizeHistoricalPlace(it) == "heemskerk" }

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

    private fun parse(root: JsonNode, retrievedAt: Instant): HistoricalSearchPage {
        if (root.get("error_code") != null || root.get("error_description") != null) {
            return HistoricalSearchPage(
                source = source,
                results = emptyList(),
                total = 0,
                status = HistoricalTechnicalStatus.MISSING_REQUIRED_FIELDS,
            )
        }
        val response = root.get("response")?.takeIf(JsonNode::isObject)
            ?: error("Open Archieven-respons bevat geen response-object")
        val docsNode = response.get("docs")
        require(docsNode?.isArray == true) { "Open Archieven-respons bevat geen resultaatarray" }
        val rawDocs = docsNode.asIterable().toList()
        val total = response.requiredNonNegativeInt("number_found")
        require(rawDocs.isEmpty() == (total == 0)) {
            "Open Archieven-respons bevat een tegenstrijdige resultaat telling"
        }
        require(total >= rawDocs.size) {
            "Open Archieven-respons bevat een resultaat telling kleiner dan de pagina"
        }
        val results = rawDocs.map { item ->
            require(item.isObject) { "Open Archieven-respons bevat een ongeldig resultaat" }
            val sourceName = item.requiredString("source_name", 500)
                ?: error("Open Archieven-resultaat bevat geen source_name")
            val uuid = item.requiredString("uuid", 500)?.asOpenArchievenUuid()
                ?: error("Open Archieven-resultaat bevat geen geldige uuid")
            val url = item.requiredString("original_source_url", 2_000)?.asHttpUrl()
                ?: error("Open Archieven-resultaat bevat geen geldige original_source_url")
            val stableIdentifier = "hee:$uuid"
            val place = item.contextField(500, "eventplace", "place", "location", "event_place")
            val person = item.contextField(500, "personname", "person", "name")
            val event = item.contextField(500, "eventtype", "_eventtype", "event")
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
                sourceRecordId = stableIdentifier,
                stableUrl = url,
                    title = item.consistentText(2_000, "title", "sourcetype", "eventtype", "_eventtype"),
                    description = item.consistentText(2_000, "description", "source", "archive"),
                    place = place.value,
                    person = person.value,
                    event = event.value,
                dateStart = dates?.first,
                dateEnd = dates?.second,
                institution = item.consistentText(2_000, "archive_org", "archive", "institution"),
                rights = item.consistentText(500, "rights", "license"),
                privacy = item.consistentText(500, "privacy", "privacyStatus"),
                retrievedAt = retrievedAt,
                    metadataRights = parseHistoricalRights(item.consistentText(500, "metadataRights", "metadataRightsStatus")),
                    objectMediaRights = parseHistoricalRights(item.consistentText(500, "objectRights", "objectMediaRightsStatus")),
                    privacyStatus = explicitPrivacy(item.consistentText(500, "privacyStatus", "privacy")),
                    placeStatus = place.status,
                    personStatus = person.status,
                    eventStatus = event.status,
                    relationships = item.explicitRelationships(),
                    sourceName = sourceName,
                    stableIdentifier = stableIdentifier,
                    originalSourceUrl = url,
            ).failClosedMetadata()
        }
        return HistoricalSearchPage(
            source, results, total.coerceAtLeast(0), HistoricalTechnicalStatus.AVAILABLE, consumed = rawDocs.size,
        )
    }

    private fun unavailable(at: Instant, status: HistoricalTechnicalStatus = HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE) =
        HistoricalSearchPage(source, emptyList(), 0, status, "Open Archieven kon niet worden bevraagd.")

    private fun explicitPrivacy(value: String?): HistoricalPrivacyStatus = when (value?.trim()?.uppercase()) {
        "CLEAR", "PUBLIC", "NO_PERSONAL_DATA", "NO_PERSONS" -> HistoricalPrivacyStatus.CLEAR
        "BLOCKED", "RESTRICTED", "PERSONAL_DATA", "LIVING_PERSON" -> HistoricalPrivacyStatus.BLOCKED
        else -> HistoricalPrivacyStatus.UNKNOWN
    }
}

private data class OpenArchievenHttpResponse(
    val statusCode: Int,
    val body: String,
    val retryAfter: String?,
)

private fun Int.toHttpStatusClass(): String? = takeIf { it in 100..599 }?.let { "${it / 100}xx" }

/** Maps only the controlled rights values supplied by the result itself. */
private fun parseHistoricalRights(value: String?): HistoricalRightsStatus = when (value?.trim()) {
    "ALLOWED" -> HistoricalRightsStatus.ALLOWED
    "RESTRICTED" -> HistoricalRightsStatus.RESTRICTED
    else -> HistoricalRightsStatus.UNKNOWN
}

private fun JsonNode.consistentText(maxLength: Int, vararg names: String): String? {
    val values = names.flatMap { name -> scalarTexts(get(name)) }
    val nonBlank = values.map(String::trim).filter(String::isNotBlank)
    if (nonBlank.any { it.length > maxLength || it.any(Char::isISOControl) }) return null
    return nonBlank.distinct().singleOrNull()
}

private fun String?.asOpenArchievenUuid(): String? = asSafeText(500)?.takeIf {
    it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,498}"))
}

private fun JsonNode.requiredString(name: String, maxLength: Int): String? =
    get(name)?.takeIf(JsonNode::isString)?.asString()?.asSafeText(maxLength)

private fun JsonNode.requiredNonNegativeInt(name: String): Int {
    val node = get(name)
    require(node?.isNumber == true) { "Open Archieven-respons bevat geen geldige $name" }
    val value = node.asString().toIntOrNull()
    require(value != null && value >= 0) { "Open Archieven-respons bevat geen geldige $name" }
    return value
}

/** Maps only the provider's explicitly named relationship collection. */
private fun JsonNode.explicitRelationships(): List<HistoricalSearchRelationship> {
    val relationNodes = get("relationships")?.takeIf(JsonNode::isArray)?.asIterable() ?: return emptyList()
    return relationNodes.mapNotNull { relation ->
        if (!relation.isObject) return@mapNotNull null
        val type = relation.scalarField("type", 500) ?: return@mapNotNull null
        val source = relation.get("source")?.takeIf(JsonNode::isObject)
            ?.scalarField("name", 2_000)
            ?: return@mapNotNull null
        val target = relation.get("target")?.takeIf(JsonNode::isObject) ?: return@mapNotNull null
        val targetName = target.scalarField("name", 2_000) ?: return@mapNotNull null
        val targetUri = target.scalarField("uri", 2_000)?.asHttpUrl() ?: return@mapNotNull null
        val targetLink = target.scalarField("link", 2_000)?.asHttpUrl() ?: return@mapNotNull null
        HistoricalSearchRelationship(
            type = type,
            source = HistoricalRelationshipSource(source),
            target = HistoricalRelationshipTarget(targetName, targetUri, targetLink),
        )
    }
}

private fun JsonNode.scalarField(name: String, maxLength: Int): String? =
    scalarTexts(get(name)).singleOrNull()?.asSafeText(maxLength)

private fun JsonNode.contextField(maxLength: Int, vararg names: String): HistoricalContextField {
    val values = names.flatMap { name -> scalarTexts(get(name)) }
        .map(String::trim)
        .filter(String::isNotBlank)
    if (values.isEmpty()) return HistoricalContextField(null, HistoricalContextStatus.MISSING)
    if (values.any { it.length > maxLength || it.any(Char::isISOControl) }) {
        return HistoricalContextField(null, HistoricalContextStatus.UNCERTAIN)
    }
    val distinct = values.distinct()
    return if (distinct.size == 1) {
        HistoricalContextField(distinct.single(), HistoricalContextStatus.AVAILABLE)
    } else {
        HistoricalContextField(null, HistoricalContextStatus.UNCERTAIN)
    }
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
