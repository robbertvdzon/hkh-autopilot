package nl.vdzon.hkh.recordintake

import java.time.Instant
import nl.vdzon.hkh.privacyclassification.GenealogicalRecord
import nl.vdzon.hkh.privacyclassification.LivingNextOfKinFields
import nl.vdzon.hkh.privacyclassification.PrivacyClassificationStatus
import nl.vdzon.hkh.privacyclassification.PrivacyClassifier
import org.springframework.stereotype.Component

/**
 * Publiek zichtbare afgeleide status van een [RecordIntakeRecord], zoals berekend door
 * [RecordPublicStatusResolver]. Deze status wordt bij elk verzoek opnieuw bepaald en nooit los
 * opgeslagen.
 */
enum class RecordPublicStatus {
    /** Er bestaat geen [RecordIntakeRecord] voor deze `localIdentifier`. */
    NO_INTAKE,

    /**
     * Er bestaat een [RecordIntakeRecord], maar er zijn geen gestructureerde externe
     * brongegevens (curator koos destijds "Sla op zonder externe brongegevens"), er is nog geen
     * expliciete beheerdersbevestiging, of een live herclassificatie levert momenteel
     * [PrivacyClassificationStatus.BLOCKED] op voor een eerder wél bevestigd record
     * (zelfherstellend gedrag: `confirmedBy`/`confirmedAt` blijven hierbij bewaard).
     */
    SAVED_WITHOUT_SOURCE,

    /**
     * Gevulde `archive*`-velden, expliciete beheerdersbevestiging én een bij dit verzoek opnieuw
     * uitgevoerde [PrivacyClassifier.classify] die [PrivacyClassificationStatus.PROCESSABLE]
     * oplevert.
     */
    CONFIRMED,
}

/** Publiek te tonen weergave van een record, afgeleid door [RecordPublicStatusResolver]. */
data class RecordPublicView(
    val status: RecordPublicStatus,
    val name: String? = null,
    val birthYear: String? = null,
    val deathYear: String? = null,
    val license: String? = null,
    val sourceUri: String? = null,
    val confirmedAt: Instant? = null,
)

/**
 * Berekent, server-side en per verzoek, de publiek zichtbare status en velden van een
 * [RecordIntakeRecord]. Nooit de ruwe `RecordIntakeRecord`-data zelf blootstellen: alleen het
 * resultaat van deze resolver mag richting de publieke route.
 */
@Component
class RecordPublicStatusResolver(
    private val privacyClassifier: PrivacyClassifier = PrivacyClassifier(),
) {
    fun resolve(record: RecordIntakeRecord?): RecordPublicView {
        if (record == null) return RecordPublicView(status = RecordPublicStatus.NO_INTAKE)

        if (!record.hasArchiveSource() || record.confirmedBy == null || record.confirmedAt == null) {
            return RecordPublicView(status = RecordPublicStatus.SAVED_WITHOUT_SOURCE)
        }

        val classification = privacyClassifier.classify(record.toGenealogicalRecord())
        if (classification.status != PrivacyClassificationStatus.PROCESSABLE) {
            return RecordPublicView(status = RecordPublicStatus.SAVED_WITHOUT_SOURCE)
        }

        return RecordPublicView(
            status = RecordPublicStatus.CONFIRMED,
            name = record.archiveName,
            birthYear = record.archiveBirthDate.toYearOnly(),
            deathYear = record.archiveDeathDate.toYearOnly(),
            license = record.archiveLicense,
            sourceUri = record.archiveSourceUri,
            confirmedAt = record.confirmedAt,
        )
    }

    private fun RecordIntakeRecord.hasArchiveSource(): Boolean = archiveName != null && archiveSourceUri != null

    private fun RecordIntakeRecord.toGenealogicalRecord(): GenealogicalRecord = GenealogicalRecord(
        deceasedStatus = deceasedStatus,
        nextOfKin = LivingNextOfKinFields(contactName = if (nextOfKinConfirmed == true) "bevestigd" else null),
    )

    private fun String?.toYearOnly(): String? = this?.let { YEAR_PATTERN.find(it)?.value }

    private companion object {
        val YEAR_PATTERN = Regex("\\d{4}")
    }
}
