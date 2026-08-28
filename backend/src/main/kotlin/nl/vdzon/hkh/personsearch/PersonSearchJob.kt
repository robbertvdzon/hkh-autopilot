package nl.vdzon.hkh.personsearch

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

private val jobIdRandom = SecureRandom()

/** Retentiegrenzen: het eerste criterium dat intreedt bepaalt wanneer een job wordt opgeschoond. */
val PERSON_SEARCH_SESSION_INACTIVITY_LIMIT: Duration = Duration.ofMinutes(60)
val PERSON_SEARCH_MAX_AGE: Duration = Duration.ofHours(24)

val PERSON_SEARCH_TERMINAL_STATUSES: Set<PersonSearchStatus> = setOf(
    PersonSearchStatus.READY,
    PersonSearchStatus.NO_EVIDENCE,
    PersonSearchStatus.PARTIAL,
    PersonSearchStatus.FAILED,
    PersonSearchStatus.CANCELLED,
    PersonSearchStatus.EXPIRED,
)

/** Cryptografisch random, niet-raadbaar job-id. */
fun newPersonSearchJobId(): String {
    val bytes = ByteArray(24)
    jobIdRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

data class PersonSearchJob(
    val id: String,
    val sessionId: String,
    val idempotencyKey: String,
    val status: PersonSearchStatus,
    val encryptedOriginalQuery: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val openArchievenStatus: PersonSearchSourceConsultationStatus = PersonSearchSourceConsultationStatus.NOT_STARTED,
    val wikidataStatus: PersonSearchSourceConsultationStatus = PersonSearchSourceConsultationStatus.NOT_STARTED,
    val encryptedOutcome: String? = null,
    val openedAt: Instant? = null,
) {
    val isTerminal: Boolean get() = status in PERSON_SEARCH_TERMINAL_STATUSES
}

/** Uitkomst van [PersonSearchJobStore.createIfAbsent]: de job, en of hij zojuist is aangemaakt. */
data class PersonSearchJobCreation(val job: PersonSearchJob, val created: Boolean)

/**
 * Aantal en job-ids van lopende en gereedstaande-niet-geopende jobs van uitsluitend één sessie.
 * De job-ids zijn nodig zodat de client na navigatie/herlading weet welke statuscontrole te
 * hervatten; dit zijn geen zichtbare bronlinks of analyticswaarden.
 */
data class PersonSearchSessionIndicator(
    val runningCount: Int,
    val readyUnopenedCount: Int,
    val runningJobIds: List<String>,
    val readyUnopenedJobIds: List<String>,
)

/**
 * In-memory jobopslag (proceslevensduur, geen aparte databasetabel). Ontdubbelt op
 * idempotentiesleutel en bewaakt sessie-eigenaarschap: een andere sessie kan een job-id niet
 * aanspreken (fail-closed: gedraagt zich alsof de job niet bestaat). Oorspronkelijke vraag en
 * antwoordpayload worden uitsluitend versleuteld bewaard (zie [PersonSearchPayloadCipher]) en
 * verwijderd zodra de retentietermijn verstrijkt of expliciet gestopt wordt.
 */
@Component
class PersonSearchJobStore(
    private val cipher: PersonSearchPayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val byIdempotencyKey = ConcurrentHashMap<String, PersonSearchJob>()
    private val byId = ConcurrentHashMap<String, PersonSearchJob>()
    private val sessionLastActivity = ConcurrentHashMap<String, Instant>()

    fun findByIdempotencyKey(key: String): PersonSearchJob? = byIdempotencyKey[key]

    fun save(job: PersonSearchJob) {
        byIdempotencyKey[job.idempotencyKey] = job
        byId[job.id] = job
    }

    /**
     * Atomisch equivalent van "findByIdempotencyKey, en zo niet gevonden aanmaken en opslaan".
     * Gebruikt [ConcurrentHashMap.computeIfAbsent], dat per sleutel maar één winnende aanroep
     * van [factory] toelaat: gelijktijdige indieningen met dezelfde idempotentiesleutel kunnen
     * dus nooit allebei een nieuwe job (en dus een tweede bronraadpleging) starten.
     */
    fun createIfAbsent(
        idempotencyKey: String,
        factory: () -> PersonSearchJob,
    ): PersonSearchJobCreation {
        var created = false
        val job = byIdempotencyKey.computeIfAbsent(idempotencyKey) {
            created = true
            factory()
        }
        if (created) byId[job.id] = job
        return PersonSearchJobCreation(job, created)
    }

    /** Retourneert de job alleen wanneer [sessionId] de eigenaar is; anders `null` (fail-closed). */
    fun findByIdForSession(jobId: String, sessionId: String): PersonSearchJob? =
        byId[jobId]?.takeIf { it.sessionId == sessionId }

    /**
     * Alleen voor gebruik door de achtergrondtaak zelf (voortgangsupdates op een job die zij zelf
     * gestart heeft); niet blootgesteld via de controller, die uitsluitend [findByIdForSession]
     * gebruikt zodat sessie-isolatie fail-closed blijft.
     */
    fun findById(jobId: String): PersonSearchJob? = byId[jobId]

    fun decryptOriginalQuery(job: PersonSearchJob): String = cipher.decrypt(job.encryptedOriginalQuery)

    fun decryptOutcome(job: PersonSearchJob): PersonSearchStoredPayload? =
        job.encryptedOutcome?.let { cipher.decryptPayload(it) }

    fun encryptOriginalQuery(originalQuery: String): String = cipher.encrypt(originalQuery)

    fun encryptOutcome(outcome: PersonSearchOutcome): String = cipher.encryptPayload(outcome.toStoredPayload())

    /** Ververst het laatste activiteitsmoment van een sessie, los van de 24u-cookie-`maxAge`. */
    fun touchSessionActivity(sessionId: String) {
        sessionLastActivity[sessionId] = Instant.now(clock)
    }

    /** Aantal en job-ids van lopende en gereedstaande-niet-geopende jobs van uitsluitend [sessionId]. */
    fun sessionIndicator(sessionId: String): PersonSearchSessionIndicator {
        val jobs = byId.values.filter { it.sessionId == sessionId }
        val running = jobs.filter { !it.isTerminal }.map { it.id }
        val readyUnopened = jobs.filter { it.status == PersonSearchStatus.READY && it.openedAt == null }.map { it.id }
        return PersonSearchSessionIndicator(running.size, readyUnopened.size, running, readyUnopened)
    }

    /**
     * Zet de job op `CANCELLED` en verwijdert direct de tijdelijke payload. Retourneert `null`
     * wanneer de job niet bestaat of van een andere sessie is; is idempotent op een reeds
     * terminale job.
     */
    fun cancel(jobId: String, sessionId: String): PersonSearchJob? {
        val job = findByIdForSession(jobId, sessionId) ?: return null
        if (job.isTerminal) return job
        val cancelled = job.copy(
            status = PersonSearchStatus.CANCELLED,
            encryptedOutcome = null,
            updatedAt = Instant.now(clock),
        )
        save(cancelled)
        return cancelled
    }

    /** `true` wanneer nieuwe uitgaande bronaanroepen voor deze job geblokkeerd moeten worden. */
    fun isCancelled(jobId: String): Boolean = byId[jobId]?.status == PersonSearchStatus.CANCELLED

    fun markOpened(jobId: String, sessionId: String): PersonSearchJob? {
        val job = findByIdForSession(jobId, sessionId) ?: return null
        if (job.status != PersonSearchStatus.READY || job.openedAt != null) return job
        val opened = job.copy(openedAt = Instant.now(clock), updatedAt = Instant.now(clock))
        save(opened)
        return opened
    }

    /**
     * Verwijdert de tijdelijke payload en zet de status op `EXPIRED` zodra 60 minuten
     * sessie-inactiviteit of 24 uur na indienen is verstreken, wat eerder komt. Wordt aangeroepen
     * door de geplande opschoningstaak.
     */
    fun purgeExpired() {
        val now = Instant.now(clock)
        byId.values.toList().forEach { job ->
            if (job.status == PersonSearchStatus.EXPIRED) return@forEach
            val tooOld = Duration.between(job.createdAt, now) >= PERSON_SEARCH_MAX_AGE
            val lastActivity = sessionLastActivity[job.sessionId] ?: job.createdAt
            val sessionInactive = Duration.between(lastActivity, now) >= PERSON_SEARCH_SESSION_INACTIVITY_LIMIT
            if (tooOld || sessionInactive) {
                save(job.copy(status = PersonSearchStatus.EXPIRED, encryptedOutcome = null, updatedAt = now))
            }
        }
    }
}
