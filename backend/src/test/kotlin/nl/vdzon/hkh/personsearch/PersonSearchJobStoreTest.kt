package nl.vdzon.hkh.personsearch

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonSearchJobStoreTest {

    private val cipher = testPayloadCipher()

    private fun job(
        id: String = "job-1",
        sessionId: String = "session-a",
        idempotencyKey: String = "key-$id",
        status: PersonSearchStatus = PersonSearchStatus.RUNNING,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = createdAt,
        openedAt: Instant? = null,
        encryptedOutcome: String? = null,
    ) = PersonSearchJob(
        id = id,
        sessionId = sessionId,
        idempotencyKey = idempotencyKey,
        status = status,
        encryptedOriginalQuery = cipher.encrypt("Wie was Jansen?"),
        createdAt = createdAt,
        updatedAt = updatedAt,
        openedAt = openedAt,
        encryptedOutcome = encryptedOutcome,
    )

    @Test
    fun `a job is only addressable by the session that created it`() {
        val store = testJobStore()
        val theJob = job()
        store.save(theJob)

        assertEquals(theJob, store.findByIdForSession("job-1", "session-a"))
        assertNull(store.findByIdForSession("job-1", "session-b"))
    }

    @Test
    fun `an unknown idempotency key yields no cached job`() {
        val store = testJobStore()

        assertNull(store.findByIdempotencyKey("unknown"))
    }

    @Test
    fun `concurrent createIfAbsent calls for the same key create the job exactly once`() {
        val store = testJobStore()
        val factoryCalls = AtomicInteger(0)
        val threadCount = 16
        val startLatch = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threadCount)

        try {
            val futures = (0 until threadCount).map { index ->
                pool.submit<PersonSearchJobCreation> {
                    startLatch.await(5, TimeUnit.SECONDS)
                    store.createIfAbsent("same-key") {
                        factoryCalls.incrementAndGet()
                        job(id = "job-$index", idempotencyKey = "same-key")
                    }
                }
            }
            startLatch.countDown()
            val results = futures.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, factoryCalls.get())
            assertEquals(1, results.map { it.job.id }.toSet().size)
            assertEquals(1, results.count { it.created })
            assertTrue(results.any { it.created })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `cancel sets CANCELLED and wipes the payload, but never resurrects an already terminal job`() {
        val store = testJobStore()
        store.save(
            job(
                status = PersonSearchStatus.RUNNING,
                encryptedOutcome = cipher.encryptPayload(PersonSearchStoredPayload()),
            ),
        )

        val cancelled = store.cancel("job-1", "session-a")

        assertEquals(PersonSearchStatus.CANCELLED, cancelled?.status)
        assertNull(cancelled?.encryptedOutcome)
        assertTrue(store.isCancelled("job-1"))

        val store2 = testJobStore()
        store2.save(job(status = PersonSearchStatus.READY))
        val result = store2.cancel("job-1", "session-a")
        assertEquals(PersonSearchStatus.READY, result?.status)
    }

    @Test
    fun `cancel is fail-closed for an unknown job or another session`() {
        val store = testJobStore()
        store.save(job())

        assertNull(store.cancel("job-1", "session-b"))
        assertNull(store.cancel("unknown-job", "session-a"))
    }

    @Test
    fun `markOpened is idempotent and only applies to a READY job`() {
        val store = testJobStore()
        store.save(job(status = PersonSearchStatus.READY))

        val opened = store.markOpened("job-1", "session-a")
        assertNotNull(opened?.openedAt)

        val openedAgain = store.markOpened("job-1", "session-a")
        assertEquals(opened?.openedAt, openedAgain?.openedAt)

        val store2 = testJobStore()
        store2.save(job(status = PersonSearchStatus.RUNNING))
        assertNull(store2.markOpened("job-1", "session-a")?.openedAt)
    }

    @Test
    fun `session indicator counts only running and ready-unopened jobs of that session`() {
        val store = testJobStore()
        store.save(job(id = "running", status = PersonSearchStatus.RUNNING))
        store.save(job(id = "ready-unopened", status = PersonSearchStatus.READY))
        store.save(job(id = "ready-opened", status = PersonSearchStatus.READY, openedAt = Instant.now()))
        store.save(job(id = "failed", status = PersonSearchStatus.FAILED))
        store.save(job(id = "other-session", sessionId = "session-b", status = PersonSearchStatus.RUNNING))

        val indicator = store.sessionIndicator("session-a")

        assertEquals(1, indicator.runningCount)
        assertEquals(1, indicator.readyUnopenedCount)
    }

    @Test
    fun `purgeExpired wipes a job after 24 hours regardless of session activity`() {
        val start = Instant.parse("2026-08-28T10:00:00Z")
        val clock = MutableClock(start)
        val store = PersonSearchJobStore(cipher, clock)
        store.save(
            job(
                status = PersonSearchStatus.READY,
                createdAt = start,
                encryptedOutcome = cipher.encryptPayload(PersonSearchStoredPayload()),
            ),
        )
        store.touchSessionActivity("session-a")

        clock.instant = start.plus(Duration.ofHours(24))
        store.touchSessionActivity("session-a")
        store.purgeExpired()

        val purged = store.findByIdForSession("job-1", "session-a")
        assertEquals(PersonSearchStatus.EXPIRED, purged?.status)
        assertNull(purged?.encryptedOutcome)
    }

    @Test
    fun `purgeExpired wipes a job after 60 minutes of session inactivity even before 24 hours`() {
        val start = Instant.parse("2026-08-28T10:00:00Z")
        val clock = MutableClock(start)
        val store = PersonSearchJobStore(cipher, clock)
        store.touchSessionActivity("session-a")
        store.save(job(status = PersonSearchStatus.READY, createdAt = start))

        clock.instant = start.plus(Duration.ofMinutes(61))
        store.purgeExpired()

        assertEquals(PersonSearchStatus.EXPIRED, store.findByIdForSession("job-1", "session-a")?.status)
    }

    @Test
    fun `purgeExpired leaves an active job untouched`() {
        val start = Instant.parse("2026-08-28T10:00:00Z")
        val clock = MutableClock(start)
        val store = PersonSearchJobStore(cipher, clock)
        store.touchSessionActivity("session-a")
        store.save(job(status = PersonSearchStatus.RUNNING, createdAt = start))

        clock.instant = start.plus(Duration.ofMinutes(5))
        store.purgeExpired()

        assertEquals(PersonSearchStatus.RUNNING, store.findByIdForSession("job-1", "session-a")?.status)
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = instant
    }
}
