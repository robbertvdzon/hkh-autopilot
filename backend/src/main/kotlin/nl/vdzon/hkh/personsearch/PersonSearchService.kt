package nl.vdzon.hkh.personsearch

import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service

const val PERSON_SEARCH_NUMBER_SHOW = 100
const val PERSON_SEARCH_DEADLINE_MILLIS = 2000L

/** Uitkomst van een indiening: job-id, status en (indien terminaal binnen budget) de uitkomst. */
data class PersonSearchSubmitResult(
    val jobId: String,
    val status: PersonSearchStatus,
    val outcome: PersonSearchOutcome?,
)

@Configuration
class PersonSearchExecutorConfiguration {
    /** Gedeelde, begrensde executor voor achtergrondopdrachten; blijft doorlopen na een 2s-timeout. */
    @Bean(destroyMethod = "shutdown")
    fun personSearchExecutor(): ExecutorService = Executors.newFixedThreadPool(4)
}

/**
 * Orkestreert job-creatie, idempotentie en de synchrone uitvoering met een harde 2000ms-deadline.
 * De achtergrondberekening wordt vóór het wachten aan de executor aangeboden, dus een timeout
 * annuleert de taak niet: dezelfde job blijft onafhankelijk van dit request doorlopen.
 */
@Service
class PersonSearchService(
    private val archivesClient: ArchivesOpenSearchClient,
    private val contextSource: PersonSearchContextSource,
    private val answerBuilder: PersonSearchAnswerBuilder,
    private val jobStore: PersonSearchJobStore,
    private val executor: ExecutorService,
    private val clock: Clock = Clock.systemUTC(),
    private val deadlineMillis: Long = PERSON_SEARCH_DEADLINE_MILLIS,
) {

    fun submit(sessionId: String, request: PersonSearchRequest): PersonSearchSubmitResult {
        val idempotencyKey = personSearchIdempotencyKey(sessionId, request)
        val (job, created) = jobStore.createIfAbsent(idempotencyKey) {
            PersonSearchJob(
                id = newPersonSearchJobId(),
                sessionId = sessionId,
                idempotencyKey = idempotencyKey,
                status = PersonSearchStatus.RUNNING,
                outcome = null,
                createdAt = Instant.now(clock),
            )
        }
        if (!created) {
            return PersonSearchSubmitResult(job.id, job.status, job.outcome)
        }

        val future = CompletableFuture.supplyAsync({ runSearch(request) }, executor)
        future.whenComplete { outcome, throwable ->
            if (throwable != null) return@whenComplete
            jobStore.save(job.copy(status = outcome.toStatus(), outcome = outcome))
        }

        return try {
            val outcome = future.get(deadlineMillis, TimeUnit.MILLISECONDS)
            PersonSearchSubmitResult(job.id, outcome.toStatus(), outcome)
        } catch (_: TimeoutException) {
            PersonSearchSubmitResult(job.id, PersonSearchStatus.RUNNING, null)
        }
    }

    private fun runSearch(request: PersonSearchRequest): PersonSearchOutcome {
        val searchOutcome = archivesClient.search(
            name = request.searchNameQuery(),
            start = 0,
            numberShow = PERSON_SEARCH_NUMBER_SHOW,
        )

        return when (searchOutcome) {
            is ArchivesSearchOutcome.Failure -> PersonSearchOutcome.SourceOutage(context = fetchContext())
            is ArchivesSearchOutcome.Success -> handleSearchSuccess(searchOutcome)
        }
    }

    private fun handleSearchSuccess(searchOutcome: ArchivesSearchOutcome.Success): PersonSearchOutcome {
        if (searchOutcome.numberFound > PERSON_SEARCH_NUMBER_SHOW) {
            return PersonSearchOutcome.Partial(
                refinementMessage = "Er zijn meer dan $PERSON_SEARCH_NUMBER_SHOW mogelijke resultaten. " +
                    "Vul de naam aan, of geef een periode of gebeurtenistype op om te verfijnen.",
            )
        }

        val deduped = searchOutcome.results.deduplicated()
        if (deduped.isEmpty()) return PersonSearchOutcome.NoResults(context = fetchContext())

        val shows = deduped.map { archivesClient.show(it.archiveCode, it.identifier) }
        if (shows.any { it is ArchivesShowOutcome.Failure }) {
            return PersonSearchOutcome.SourceOutage(context = fetchContext())
        }

        val records = shows.filterIsInstance<ArchivesShowOutcome.Success>().map { it.record }
        val answer = answerBuilder.build(records, checkedAt = Instant.now(clock))
        return PersonSearchOutcome.SupportedAnswer(answer, context = fetchContext())
    }

    private fun fetchContext(): PersonSearchWikidataContext? = try {
        contextSource.fetchContext("Heemskerk")
    } catch (_: Exception) {
        null
    }
}
