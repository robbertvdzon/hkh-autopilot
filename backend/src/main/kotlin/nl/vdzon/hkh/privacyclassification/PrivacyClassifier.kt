package nl.vdzon.hkh.privacyclassification

/**
 * Deterministische, fail-closed classifier voor een genealogisch record.
 *
 * De AVG geldt niet voor overleden personen. Een record mag daarom alleen als [PrivacyClassificationStatus.PROCESSABLE]
 * gelden wanneer vaststaat dat de persoon overleden is *en* er geen gegevens van een nog levende
 * nabestaande in het record staan. In alle overige gevallen - onbekende status, een levende persoon,
 * of wel gedetecteerde nabestaande-velden - is het record [PrivacyClassificationStatus.BLOCKED] met een
 * leesbare reden. Er ontsnapt nooit een uitzondering: onverwachte fouten leiden tot een geblokkeerd
 * record.
 */
class PrivacyClassifier {

    fun classify(record: GenealogicalRecord): PrivacyClassificationResult =
        runCatching { evaluate(record) }.getOrElse { failClosed() }

    private fun evaluate(record: GenealogicalRecord): PrivacyClassificationResult {
        val deceasedStatus = DeceasedStatus.parse(record.deceasedStatus)

        if (deceasedStatus != DeceasedStatus.OVERLEDEN) {
            return PrivacyClassificationResult(
                status = PrivacyClassificationStatus.BLOCKED,
                reason = when (deceasedStatus) {
                    DeceasedStatus.LEVEND -> PrivacyClassificationReasons.PERSON_ALIVE
                    else -> PrivacyClassificationReasons.DECEASED_STATUS_UNKNOWN
                },
            )
        }

        if (record.nextOfKin.hasIdentifyingField()) {
            return PrivacyClassificationResult(
                status = PrivacyClassificationStatus.BLOCKED,
                reason = PrivacyClassificationReasons.LIVING_NEXT_OF_KIN,
            )
        }

        return PrivacyClassificationResult(
            status = PrivacyClassificationStatus.PROCESSABLE,
            reason = PrivacyClassificationReasons.PROCESSABLE,
        )
    }

    private fun failClosed(): PrivacyClassificationResult = PrivacyClassificationResult(
        status = PrivacyClassificationStatus.BLOCKED,
        reason = PrivacyClassificationReasons.UNEXPECTED_ERROR,
    )
}
