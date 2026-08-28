package nl.vdzon.hkh.personsearch

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
