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
    const val NAMED_PERSON_LIKELY_LIVING =
        "Bevat een genoemde persoon die volgens de FamilySearch 110/95-jaarregel vermoedelijk nog leeft."
    const val NAMED_PERSON_AGE_UNKNOWN_FAILCLOSED =
        "Leeftijd van een genoemde persoon kon niet worden vastgesteld door een ontbrekend of onleesbaar datumveld."
    const val GEDCOM_RESN_BLOCKED =
        "Bevat een GEDCOM 7.0 RESN-markering (vertrouwelijk/afgesloten/privacy) die verwerking blokkeert."
    const val UNEXPECTED_ERROR = "Classificatie kon niet worden vastgesteld door een onverwachte fout."
}
