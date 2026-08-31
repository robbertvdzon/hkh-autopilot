package nl.vdzon.hkh.placesearch

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service

const val PLACE_SEARCH_DEADLINE_MILLIS = 2000L
private val WIKIDATA_CACHE_TTL: Duration = Duration.ofMinutes(5)
private val COMMONS_CACHE_TTL: Duration = Duration.ofMinutes(5)

@Configuration
class PlaceSearchExecutorConfiguration {
    /** Losse, kleine executor: deze route is synchroon en kent geen achtergrondtaken. */
    @Bean(destroyMethod = "shutdown")
    fun placeSearchExecutor(): ExecutorService = Executors.newFixedThreadPool(2)
}

/**
 * Orkestreert de synchrone plek/gebouw-zoekopdracht binnen een harde 2000ms-totaalbudget (Wikidata
 * + eventueel Commons), zonder de sessiegebonden achtergrondjob-infrastructuur van `personsearch`.
 * Fail-closed: elke fout of het overschrijden van het budget levert [PlaceSearchOutcome.WikidataOutage]
 * op, nooit een gedeeltelijk geconstrueerd antwoord.
 */
@Service
open class PlaceSearchService(
    private val wikidataClient: PlaceSearchWikidataClient,
    private val commonsClient: PlaceSearchCommonsClient,
    private val answerBuilder: PlaceSearchAnswerBuilder,
    @param:Qualifier("placeSearchExecutor") private val executor: ExecutorService,
    private val clock: Clock = Clock.systemUTC(),
    private val deadlineMillis: Long = PLACE_SEARCH_DEADLINE_MILLIS,
) {
    private val entityCache = PlaceSearchCache<String, WikidataEntity>(WIKIDATA_CACHE_TTL, clock)
    private val imageCache = PlaceSearchCache<String, List<PlaceSearchImage>>(COMMONS_CACHE_TTL, clock)

    open fun search(candidateTerm: String): PlaceSearchOutcome {
        val future = executor.submit(Callable { performSearch(candidateTerm) })
        return try {
            future.get(deadlineMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            PlaceSearchOutcome.WikidataOutage
        } catch (_: Exception) {
            PlaceSearchOutcome.WikidataOutage
        }
    }

    private fun performSearch(candidateTerm: String): PlaceSearchOutcome {
        val matches = try {
            findHeemskerkMatches(candidateTerm)
        } catch (_: Exception) {
            return PlaceSearchOutcome.WikidataOutage
        }

        if (matches.size != 1) {
            val candidates = matches.mapNotNull { match ->
                match.entity.labelOrNull()?.let { PlaceSearchCandidateSummary(match.qid, it) }
            }
            return PlaceSearchOutcome.NoMatch(candidates)
        }

        val match = matches.single()
        val label = match.entity.labelOrNull() ?: return PlaceSearchOutcome.WikidataOutage
        val checkedAt = Instant.now(clock)

        val facts = PlaceEntityFacts(
            qid = match.qid,
            label = label,
            description = match.entity.descriptionOrNull(),
            inceptionYear = wikidataClient.inceptionYear(match.entity),
            architecturalStyleLabel = wikidataClient.architecturalStyleQid(match.entity)?.let(::resolveCachedLabel),
            architectLabel = wikidataClient.architectQid(match.entity)?.let(::resolveCachedLabel),
            heritageStatusLabel = wikidataClient.heritageDesignationQid(match.entity)?.let(::resolveCachedLabel),
            municipalityLabel = if (match.viaMunicipalityLink) PLACE_SEARCH_HEEMSKERK_LABEL else null,
        )
        val answer = answerBuilder.build(facts, checkedAt)

        val images = try {
            fetchImages(match.entity)
        } catch (_: Exception) {
            null
        }
        return PlaceSearchOutcome.SupportedAnswer(
            answer = answer.copy(images = images.orEmpty()),
            commonsOutage = images == null,
        )
    }

    private data class HeemskerkCandidate(val qid: String, val entity: WikidataEntity, val viaMunicipalityLink: Boolean)

    private fun findHeemskerkMatches(term: String): List<HeemskerkCandidate> {
        val ids = wikidataClient.searchCandidateIds(term).take(5)
        return ids.mapNotNull { qid ->
            val entity = getCachedEntity(qid) ?: return@mapNotNull null
            val match = wikidataClient.evaluateHeemskerkMatch(entity)
            if (match.matched) HeemskerkCandidate(qid, entity, match.viaMunicipalityLink) else null
        }
    }

    private fun getCachedEntity(qid: String): WikidataEntity? =
        entityCache.getOrPut(qid) { wikidataClient.fetchEntity(qid) }

    private fun resolveCachedLabel(qid: String): String? = getCachedEntity(qid)?.labelOrNull()

    /** `null` betekent een mislukte Commons-raadpleging; een lege lijst is een legitiem leeg resultaat. */
    private fun fetchImages(entity: WikidataEntity): List<PlaceSearchImage>? {
        val category = wikidataClient.commonsCategory(entity)
        if (category != null) {
            return imageCache.getOrPut("category:$category") { commonsClient.fetchCategoryImages(category) }
        }
        val fileName = wikidataClient.imageFileName(entity) ?: return emptyList()
        return imageCache.getOrPut("file:$fileName") { commonsClient.fetchSingleFile(fileName) }
    }
}
