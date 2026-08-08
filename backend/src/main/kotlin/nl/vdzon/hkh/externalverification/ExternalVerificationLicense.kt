package nl.vdzon.hkh.externalverification

/**
 * Licentiestatus van één specifiek archiefrecord, los van de bestaande verificatiestatus
 * ([ExternalVerificationStatus]). Een record deelt nooit de licentie-uitkomst van een ander record
 * uit dezelfde archiefcollectie: elke bevraging levert een eigen, onafhankelijke uitkomst op.
 */
enum class ExternalVerificationLicenseStatus {
    LICENSE_KNOWN,
    LICENSE_UNKNOWN,
}

/**
 * Resultaat van [ExternalVerificationLicenseEvaluator.evaluate]. [licenseValue] is uitsluitend
 * gezet wanneer [status] [ExternalVerificationLicenseStatus.LICENSE_KNOWN] is.
 */
data class ExternalVerificationLicenseResult(
    val status: ExternalVerificationLicenseStatus,
    val licenseValue: String?,
) {
    init {
        if (status == ExternalVerificationLicenseStatus.LICENSE_KNOWN) {
            require(!licenseValue.isNullOrBlank()) { "licenseValue must be present when status is LICENSE_KNOWN" }
        } else {
            require(licenseValue == null) { "licenseValue must be absent when status is LICENSE_UNKNOWN" }
        }
    }

    val known: Boolean get() = status == ExternalVerificationLicenseStatus.LICENSE_KNOWN
}

/** Vaste, leesbare licentiestatusteksten. */
object ExternalVerificationLicenseReasons {
    const val LICENSE_UNKNOWN = "License unknown"
    const val PUBLISH_BLOCKED =
        "Publicatie geweigerd: licentiestatus van dit record is 'License unknown'."
}

/**
 * Leest de hergebruikslicentie uit het JSON-LD-antwoord van dit ene specifieke record. Er wordt
 * nooit een licentiewaarde van een ander record binnen dezelfde archiefcollectie hergebruikt of
 * gecachet: ontbreekt de licentie in dit antwoord, dan is de uitkomst altijd fail-closed
 * [ExternalVerificationLicenseStatus.LICENSE_UNKNOWN], ook wanneer een eerder bevraagd record uit
 * dezelfde collectie wel een licentie had.
 */
object ExternalVerificationLicenseEvaluator {

    fun evaluate(fetched: ArchiveFetchResult): ExternalVerificationLicenseResult =
        runCatching { doEvaluate(fetched) }.getOrElse { unknown() }

    private fun doEvaluate(fetched: ArchiveFetchResult): ExternalVerificationLicenseResult {
        val license = (fetched as? ArchiveFetchResult.Found)
            ?.fields
            ?.license
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return unknown()

        return ExternalVerificationLicenseResult(ExternalVerificationLicenseStatus.LICENSE_KNOWN, license)
    }

    private fun unknown() = ExternalVerificationLicenseResult(ExternalVerificationLicenseStatus.LICENSE_UNKNOWN, null)
}
