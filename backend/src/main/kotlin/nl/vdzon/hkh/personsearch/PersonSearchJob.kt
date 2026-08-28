package nl.vdzon.hkh.personsearch

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

private val jobIdRandom = SecureRandom()

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
    val outcome: PersonSearchOutcome? = null,
    val createdAt: Instant,
)

/**
 * In-memory jobopslag (proceslevensduur, geen TTL/opschoning — expliciet buiten scope van deze
 * story, zie worklog). Ontdubbelt op idempotentiesleutel en bewaakt sessie-eigenaarschap: een
 * andere sessie kan een job-id niet aanspreken.
 */
@Component
class PersonSearchJobStore {
    private val byIdempotencyKey = ConcurrentHashMap<String, PersonSearchJob>()
    private val byId = ConcurrentHashMap<String, PersonSearchJob>()

    fun findByIdempotencyKey(key: String): PersonSearchJob? = byIdempotencyKey[key]

    fun save(job: PersonSearchJob) {
        byIdempotencyKey[job.idempotencyKey] = job
        byId[job.id] = job
    }

    /** Retourneert de job alleen wanneer [sessionId] de eigenaar is; anders `null` (fail-closed). */
    fun findByIdForSession(jobId: String, sessionId: String): PersonSearchJob? =
        byId[jobId]?.takeIf { it.sessionId == sessionId }
}
