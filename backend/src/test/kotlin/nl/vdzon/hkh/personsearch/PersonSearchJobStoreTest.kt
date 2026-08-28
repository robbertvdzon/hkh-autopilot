package nl.vdzon.hkh.personsearch

import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonSearchJobStoreTest {

    @Test
    fun `a job is only addressable by the session that created it`() {
        val store = PersonSearchJobStore()
        val job = PersonSearchJob(
            id = "job-1",
            sessionId = "session-a",
            idempotencyKey = "key-1",
            status = PersonSearchStatus.RUNNING,
            createdAt = Instant.now(),
        )
        store.save(job)

        assertEquals(job, store.findByIdForSession("job-1", "session-a"))
        assertNull(store.findByIdForSession("job-1", "session-b"))
    }

    @Test
    fun `an unknown idempotency key yields no cached job`() {
        val store = PersonSearchJobStore()

        assertNull(store.findByIdempotencyKey("unknown"))
    }

    @Test
    fun `concurrent createIfAbsent calls for the same key create the job exactly once`() {
        val store = PersonSearchJobStore()
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
                        PersonSearchJob(
                            id = "job-$index",
                            sessionId = "session-a",
                            idempotencyKey = "same-key",
                            status = PersonSearchStatus.RUNNING,
                            createdAt = Instant.now(),
                        )
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
}
