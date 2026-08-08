package nl.vdzon.hkh.recordintake

import java.time.Instant

/** Opgeslagen intern conceptrecord. Bevat uitsluitend metadata: geen publicatie- of objectmediavelden. */
data class RecordIntakeRecord(
    val id: Long,
    val localIdentifier: String,
    val status: String,
    val createdAt: Instant,
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
