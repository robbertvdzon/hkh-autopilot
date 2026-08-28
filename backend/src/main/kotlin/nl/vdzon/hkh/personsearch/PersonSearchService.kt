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

/** Uitkomst van een indiening: job-id, status en (indien terminaal binnen budget) de payload. */
data class PersonSearchSubmitResult(
    val jobId: String,
    val status: PersonSearchStatus,
    val payload: PersonSearchStoredPayload?,
)

/** Uitkomst van een statusaanvraag/stopactie/openactie: job plus ontsleutelde vraag en payload. */
data class PersonSearchStatusResult(
    val job: PersonSearchJob,
    val originalQuery: String,
    val payload: PersonSearchStoredPayload?,
)

@Configuration
class PersonSearchExecutorConfiguration {
    /** Gedeelde, begrensde executor voor achtergrondopdrachten; blijft doorlopen na een 2s-timeout. */
    @Bean(destroyMethod = "shutdown")
    fun personSearchExecutor(): ExecutorService = Executors.newFixedThreadPool(4)
}

/**
 * Orkestreert job-creatie, idempotentie en de synchrone uitvoering met een harde 2000ms-deadline,
 * uitvoerbaar door een gewone gedeelde executor (geen Agent Runtime). De achtergrondberekening
 * wordt vóór het wachten aan de executor aangeboden, dus een timeout annuleert de taak niet:
 * dezelfde job blijft onafhankelijk van dit request doorlopen (`QUEUED` tot de taak start,
 * `RUNNING` daarna). Een expliciete stopactie ([cancel]) blokkeert nieuwe uitgaande bronaanroepen
 * en verwijdert de tijdelijke payload direct.
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
        jobStore.touchSessionActivity(sessionId)
        val idempotencyKey = personSearchIdempotencyKey(sessionId, request)
        val (job, created) = jobStore.createIfAbsent(idempotencyKey) {
            val now = Instant.now(clock)
            PersonSearchJob(
                id = newPersonSearchJobId(),
                sessionId = sessionId,
                idempotencyKey = idempotencyKey,
                status = PersonSearchStatus.QUEUED,
                encryptedOriginalQuery = jobStore.encryptOriginalQuery(request.originalQuery),
                createdAt = now,
                updatedAt = now,
            )
        }
        if (!created) {
            return PersonSearchSubmitResult(job.id, job.status, jobStore.decryptOutcome(job))
        }

        val future = CompletableFuture.supplyAsync({ runSearch(job.id, request) }, executor)
        future.whenComplete { outcome, throwable ->
            if (throwable != null || outcome == null) return@whenComplete
            persistOutcome(job.id, outcome)
        }

        return try {
            val outcome = future.get(deadlineMillis, TimeUnit.MILLISECONDS)
            if (outcome == null) {
                val current = jobStore.findByIdForSession(job.id, job.sessionId) ?: job
                PersonSearchSubmitResult(current.id, current.status, null)
            } else {
                // Persisted here too (not only in whenComplete above): whenComplete's dependent
                // stage is not guaranteed to have run yet by the time future.get() returns, so a
                // caller that immediately polls status/session-indicator must already see the
                // terminal state that this very call is about to report.
                persistOutcome(job.id, outcome)
                PersonSearchSubmitResult(job.id, outcome.toStatus(), outcome.toStoredPayload())
            }
        } catch (_: TimeoutException) {
            val current = jobStore.findByIdForSession(job.id, job.sessionId) ?: job
            PersonSearchSubmitResult(current.id, current.status, null)
        }
    }

    /** Idempotent: een job die al terminaal is (bv. door de andere aanroeper) wordt niet overschreven. */
    private fun persistOutcome(jobId: String, outcome: PersonSearchOutcome) {
        val current = jobStore.findById(jobId) ?: return
        if (current.isTerminal) return
        jobStore.save(
            current.copy(
                status = outcome.toStatus(),
                encryptedOutcome = jobStore.encryptOutcome(outcome),
                updatedAt = Instant.now(clock),
            ),
        )
    }

    /** Sessie-isolatie fail-closed: `null` voor een onbekende job of een job van een andere sessie. */
    fun status(jobId: String, sessionId: String): PersonSearchStatusResult? {
        jobStore.touchSessionActivity(sessionId)
        val job = jobStore.findByIdForSession(jobId, sessionId) ?: return null
        return toStatusResult(job)
    }

    fun cancel(jobId: String, sessionId: String): PersonSearchStatusResult? {
        jobStore.touchSessionActivity(sessionId)
        val job = jobStore.cancel(jobId, sessionId) ?: return null
        return toStatusResult(job)
    }

    fun open(jobId: String, sessionId: String): PersonSearchStatusResult? {
        jobStore.touchSessionActivity(sessionId)
        val job = jobStore.markOpened(jobId, sessionId) ?: return null
        return toStatusResult(job)
    }

    fun sessionIndicator(sessionId: String): PersonSearchSessionIndicator {
        jobStore.touchSessionActivity(sessionId)
        return jobStore.sessionIndicator(sessionId)
    }

    private fun toStatusResult(job: PersonSearchJob): PersonSearchStatusResult =
        PersonSearchStatusResult(job, jobStore.decryptOriginalQuery(job), jobStore.decryptOutcome(job))

    /** Retourneert `null` wanneer de job inmiddels gestopt/opgeschoond is: er wordt dan niets bewaard. */
    private fun runSearch(jobId: String, request: PersonSearchRequest): PersonSearchOutcome? {
        markRunning(jobId)
        if (jobStore.isCancelled(jobId)) return null

        updateOpenArchievenStatus(jobId, PersonSearchSourceConsultationStatus.IN_PROGRESS)
        val searchOutcome = archivesClient.search(
            name = request.searchNameQuery(),
            start = 0,
            numberShow = PERSON_SEARCH_NUMBER_SHOW,
        )
        if (jobStore.isCancelled(jobId)) return null

        return when (searchOutcome) {
            is ArchivesSearchOutcome.Failure -> {
                updateOpenArchievenStatus(jobId, PersonSearchSourceConsultationStatus.FAILED)
                PersonSearchOutcome.SourceOutage(context = fetchContextTracked(jobId))
            }
            is ArchivesSearchOutcome.Success -> handleSearchSuccess(jobId, searchOutcome)
        }
    }

    private fun handleSearchSuccess(jobId: String, searchOutcome: ArchivesSearchOutcome.Success): PersonSearchOutcome? {
        if (searchOutcome.numberFound > PERSON_SEARCH_NUMBER_SHOW) {
            updateOpenArchievenStatus(jobId, PersonSearchSourceConsultationStatus.SUCCEEDED)
            return PersonSearchOutcome.Partial(
                refinementMessage = "Er zijn meer dan $PERSON_SEARCH_NUMBER_SHOW mogelijke resultaten. " +
                    "Vul de naam aan, of geef een periode of gebeurtenistype op om te verfijnen.",
            )
        }

        val deduped = searchOutcome.results.deduplicated()
        if (deduped.isEmpty()) {
            updateOpenArchievenStatus(jobId, PersonSearchSourceConsultationStatus.SUCCEEDED)
            return PersonSearchOutcome.NoResults(context = fetchContextTracked(jobId))
        }
        if (jobStore.isCancelled(jobId)) return null

        val shows = deduped.map { item ->
            if (jobStore.isCancelled(jobId)) return null
            archivesClient.show(item.archiveCode, item.identifier)
        }
        if (shows.any { it is ArchivesShowOutcome.Failure }) {
            updateOpenArchievenStatus(jobId, PersonSearchSourceConsultationStatus.FAILED)
            return PersonSearchOutcome.SourceOutage(context = fetchContextTracked(jobId))
        }
        updateOpenArchievenStatus(jobId, PersonSearchSourceConsultationStatus.SUCCEEDED)
        if (jobStore.isCancelled(jobId)) return null

        val records = shows.filterIsInstance<ArchivesShowOutcome.Success>().map { it.record }
        val answer = answerBuilder.build(records, checkedAt = Instant.now(clock))
        return PersonSearchOutcome.SupportedAnswer(answer, context = fetchContextTracked(jobId))
    }

    private fun fetchContextTracked(jobId: String): PersonSearchWikidataContext? {
        if (jobStore.isCancelled(jobId)) return null
        updateWikidataStatus(jobId, PersonSearchSourceConsultationStatus.IN_PROGRESS)
        return try {
            val context = contextSource.fetchContext("Heemskerk")
            updateWikidataStatus(
                jobId,
                if (context != null) {
                    PersonSearchSourceConsultationStatus.SUCCEEDED
                } else {
                    PersonSearchSourceConsultationStatus.FAILED
                },
            )
            context
        } catch (_: Exception) {
            updateWikidataStatus(jobId, PersonSearchSourceConsultationStatus.FAILED)
            null
        }
    }

    private fun markRunning(jobId: String) {
        updateJob(jobId) { it.copy(status = PersonSearchStatus.RUNNING) }
    }

    private fun updateOpenArchievenStatus(jobId: String, status: PersonSearchSourceConsultationStatus) {
        updateJob(jobId) { it.copy(openArchievenStatus = status) }
    }

    private fun updateWikidataStatus(jobId: String, status: PersonSearchSourceConsultationStatus) {
        updateJob(jobId) { it.copy(wikidataStatus = status) }
    }

    /** Past een achtergrondvoortgangsupdate toe, tenzij de job intussen gestopt/opgeschoond is. */
    private fun updateJob(jobId: String, transform: (PersonSearchJob) -> PersonSearchJob) {
        val job = jobStore.findById(jobId) ?: return
        if (job.isTerminal) return
        jobStore.save(transform(job).copy(updatedAt = Instant.now(clock)))
    }
}
