package nl.vdzon.hkh.externalverification

import java.time.Instant

/**
 * Opgeslagen verificatieresultaat. Bevat uitsluitend de minimale verificatievelden: externe URI,
 * welke velden gematcht zijn, controletijdstip en status - nooit de volledige externe
 * JSON-LD-payload. Het optionele versleutelde toegangstoken staat bewust niet in dit domeinresultaat
 * (het wordt nooit teruggegeven), alleen in de opslag zelf.
 */
data class ExternalVerificationRecord(
    val id: Long,
    val localIdentifier: String,
    val externalUri: String,
    val matchedFields: List<String>,
    val status: String,
    val checkedAt: Instant,
)
