package nl.vdzon.hkh.privacyclassification

/** Uitkomst van [PrivacyClassifier]. */
enum class PrivacyClassificationStatus {
    PROCESSABLE,
    BLOCKED,
}

/**
 * Resultaat van [PrivacyClassifier]. [reason] is altijd een niet-lege, leesbare tekstuele
 * toelichting - ook voor [PrivacyClassificationStatus.PROCESSABLE] - en nooit uitsluitend een
 * interne code.
 */
data class PrivacyClassificationResult(
    val status: PrivacyClassificationStatus,
    val reason: String,
) {
    init {
        require(reason.isNotBlank()) { "reason must not be blank" }
    }

    val processable: Boolean
        get() = status == PrivacyClassificationStatus.PROCESSABLE
}

/** Vaste, leesbare classificatieredenen. */
object PrivacyClassificationReasons {
    const val PROCESSABLE = "Persoon is overleden en er zijn geen gegevens van een levende nabestaande gevonden."
    const val LIVING_NEXT_OF_KIN = "Bevat gegevens van levende nabestaande"
    const val DECEASED_STATUS_UNKNOWN = "Overlijdensstatus is onbekend of niet herkend."
    const val PERSON_ALIVE = "Persoon is (mogelijk) nog in leven."
    const val UNEXPECTED_ERROR = "Classificatie kon niet worden vastgesteld door een onverwachte fout."
}
