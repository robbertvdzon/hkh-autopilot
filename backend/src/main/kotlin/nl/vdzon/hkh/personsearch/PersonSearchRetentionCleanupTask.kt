package nl.vdzon.hkh.personsearch

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val CLEANUP_FIXED_DELAY_MILLIS = 60_000L

/**
 * Geplande opschoning: verwijdert de tijdelijke payload en zet de status op `EXPIRED` zodra 60
 * minuten sessie-inactiviteit of 24 uur na indienen is verstreken, wat eerder komt. Draait op de
 * gewone Spring-taakscheduler (`@EnableScheduling` op [nl.vdzon.hkh.HkhApplication]) — geen Agent
 * Runtime nodig.
 */
@Component
class PersonSearchRetentionCleanupTask(private val jobStore: PersonSearchJobStore) {
    @Scheduled(fixedDelay = CLEANUP_FIXED_DELAY_MILLIS)
    fun cleanup() {
        jobStore.purgeExpired()
    }
}
