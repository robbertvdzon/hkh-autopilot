package nl.vdzon.hkh.externalverification

/** Uitkomst van [ExternalVerificationMatcher.match]. */
enum class ExternalVerificationStatus {
    VERIFIED,
    UNVERIFIED,
}

/** Vaste, machineleesbare namen voor de vergelijkbare velden, ook gebruikt als opgeslagen `matchedFields`. */
object ExternalVerificationMatchableFields {
    const val NAME = "name"
    const val BIRTH_DATE = "birthDate"
    const val DEATH_DATE = "deathDate"
}

/**
 * Resultaat van [ExternalVerificationMatcher.match]. [reason] is altijd een niet-lege, leesbare
 * toelichting, naar het patroon van `PrivacyClassificationResult`. [matchedFields] bevat uitsluitend
 * de namen van de velden die overeenkwamen - nooit de opgehaalde waarden zelf.
 */
data class ExternalVerificationMatchResult(
    val status: ExternalVerificationStatus,
    val matchedFields: List<String>,
    val reason: String,
) {
    init {
        require(reason.isNotBlank()) { "reason must not be blank" }
    }

    val verified: Boolean get() = status == ExternalVerificationStatus.VERIFIED
}

/** Vaste, leesbare matchredenen. */
object ExternalVerificationReasons {
    const val VERIFIED = "Naam en datumvelden komen overeen met het archiefrecord van archieven.nl."
    const val NOT_FOUND = "Geen archiefrecord gevonden voor de opgegeven adtid/guid."
    const val AUTHENTICATION_REQUIRED = "Het archiefendpoint vereist een toegangstoken om dit record te bevragen."
    const val FIELDS_DO_NOT_MATCH = "Naam- en/of datumvelden komen niet overeen met het archiefrecord van archieven.nl."
    const val UNEXPECTED_ERROR = "Verificatie kon niet worden vastgesteld door een onverwachte fout."
}
