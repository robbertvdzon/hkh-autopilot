package nl.vdzon.hkh.recordintake.api

import java.time.Instant
import nl.vdzon.hkh.recordintake.RecordIntakeStore
import nl.vdzon.hkh.recordintake.RecordPublicStatusResolver
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RecordPublicResponse(
    val localIdentifier: String,
    val status: String,
    val name: String? = null,
    val birthYear: String? = null,
    val deathYear: String? = null,
    val license: String? = null,
    val sourceUri: String? = null,
    val confirmedAt: Instant? = null,
)

/**
 * Publiek, ongeauthenticeerd leespad naar de afgeleide status van precies één record. Geeft
 * uitsluitend niet-privacygevoelige, publicatiewaardige velden terug en nooit de ruwe
 * `RecordIntakeRecord`-data (o.a. status, `deceasedStatus`, `nextOfKinConfirmed`). Levert altijd
 * HTTP 200 op, ook wanneer er geen record bestaat voor deze `localIdentifier`: het onderscheid
 * tussen "bestaat niet" en "bestaat wel maar heeft (nog) geen bevestigde externe bron" mag niet
 * via de HTTP-status lekken.
 */
@RestController
@RequestMapping("/api/records")
class RecordPublicController(
    private val store: RecordIntakeStore,
    private val statusResolver: RecordPublicStatusResolver,
) {
    @GetMapping("/{localIdentifier}")
    fun findByLocalIdentifier(@PathVariable localIdentifier: String): RecordPublicResponse {
        val view = statusResolver.resolve(store.findByLocalIdentifier(localIdentifier))
        return RecordPublicResponse(
            localIdentifier = localIdentifier,
            status = view.status.name,
            name = view.name,
            birthYear = view.birthYear,
            deathYear = view.deathYear,
            license = view.license,
            sourceUri = view.sourceUri,
            confirmedAt = view.confirmedAt,
        )
    }
}
