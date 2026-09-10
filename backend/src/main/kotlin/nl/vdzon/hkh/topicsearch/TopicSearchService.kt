package nl.vdzon.hkh.topicsearch

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

const val TOPIC_SEARCH_DEADLINE_MILLIS = 2000L
private val TOPIC_SEARCH_CACHE_TTL: Duration = Duration.ofMinutes(30)

private val TOPIC_SEARCH_REFINEMENT_SUGGESTIONS = listOf(
    "Gebruik een breder of algemener trefwoord.",
    "Voeg een jaartal of periode toe aan je vraag.",
    "Controleer de spelling van de zoekterm.",
)

@Configuration
class TopicSearchExecutorConfiguration {
    /** Losse, kleine executor: deze route is synchroon en kent geen achtergrondtaken. */
    @Bean(destroyMethod = "shutdown")
    fun topicSearchExecutor(): ExecutorService = Executors.newFixedThreadPool(2)
}

/**
 * Orkestreert de synchrone onderwerp/voorwerp/gebeurtenis-zoekopdracht binnen een harde
 * 2000ms-totaalbudget (Europeana en, uitsluitend bij minstens 1 geldig record, Wikidata), zonder de
 * sessiegebonden achtergrondjob-infrastructuur van `personsearch`. Fail-closed: elke fout of het
 * overschrijden van het budget levert [TopicSearchOutcome.EuropeanaOutage] op, nooit een gedeeltelijk
 * geconstrueerd antwoord. Een Wikidata-fout blokkeert nooit de Europeana-resultaten.
 */
@Service
open class TopicSearchService(
    private val europeanaClient: ArchivesEuropeanaClient,
    private val wikidataContextClient: TopicSearchWikidataContextSource,
    @param:Qualifier("topicSearchExecutor") private val executor: ExecutorService,
    private val clock: Clock = Clock.systemUTC(),
    private val deadlineMillis: Long = TOPIC_SEARCH_DEADLINE_MILLIS,
) {
    private val recordsCache = TopicSearchCache<String, List<TopicSearchRecord>>(TOPIC_SEARCH_CACHE_TTL, clock)

    open fun search(topicSearchTerm: String): TopicSearchOutcome {
        val future = executor.submit(Callable { performSearch(topicSearchTerm) })
        return try {
            future.get(deadlineMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            TopicSearchOutcome.EuropeanaOutage
        } catch (_: Exception) {
            TopicSearchOutcome.EuropeanaOutage
        }
    }

    private fun performSearch(topicSearchTerm: String): TopicSearchOutcome {
        val query = "$topicSearchTerm AND Heemskerk"
        val validRecords = recordsCache.getOrPut(query) {
            when (val outcome = europeanaClient.search(query)) {
                is EuropeanaSearchOutcome.Success -> outcome.validRecords
                EuropeanaSearchOutcome.Failure -> null
            }
        } ?: return TopicSearchOutcome.EuropeanaOutage

        val checkedAt = Instant.now(clock)
        if (validRecords.isEmpty()) {
            return TopicSearchOutcome.Empty(checkedAt, TOPIC_SEARCH_REFINEMENT_SUGGESTIONS)
        }

        val context = try {
            wikidataContextClient.fetchContext(topicSearchTerm)
        } catch (_: Exception) {
            null
        }

        return TopicSearchOutcome.Ready(
            TopicSearchAnswer(
                topicSearchTerm = topicSearchTerm,
                records = validRecords,
                context = context,
                checkedAt = checkedAt,
            ),
        )
    }
}
