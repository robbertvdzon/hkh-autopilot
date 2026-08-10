package nl.vdzon.hkh.recordintake

import java.time.Instant

/**
 * Opgeslagen intern conceptrecord. Bevat uitsluitend metadata: geen publicatie- of
 * objectmediavelden. `archiveXxx`-velden zijn uitsluitend de gestructureerde kernvelden van een
 * bevestigde externe archiefbron; de ruwe externe respons wordt nooit opgeslagen.
 */
data class RecordIntakeRecord(
    val id: Long,
    val localIdentifier: String,
    val status: String,
    val createdAt: Instant,
    val deceasedStatus: String,
    val nextOfKinConfirmed: Boolean?,
    val archiveName: String?,
    val archiveBirthDate: String?,
    val archiveDeathDate: String?,
    val archiveLicense: String?,
    val archiveSourceUri: String?,
    val archiveFetchedAt: Instant?,
)

/** Opgeslagen optionele externe conceptkoppeling. */
data class RecordIntakeExternalLink(
    val id: Long,
    val recordIntakeId: Long,
    val status: String,
    val createdAt: Instant,
)

const val RECORD_INTAKE_STATUS_INTERN_CONCEPT = "intern_concept"
const val RECORD_INTAKE_EXTERNAL_LINK_STATUS_CONCEPT = "concept"
