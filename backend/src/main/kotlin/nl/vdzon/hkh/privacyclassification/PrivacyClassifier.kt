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
 *
 * Aanvullend op deze bestaande controles blijft het record ook [PrivacyClassificationStatus.BLOCKED]
 * zodra minstens één genoemd persoon ([GenealogicalRecord.namedPersons], de hoofdpersoon of een
 * familielid) volgens de FamilySearch 110/95-jaarregel ([LivingPersonAgeRule]) vermoedelijk nog leeft
 * of niet met zekerheid beoordeeld kan worden.
 */
class PrivacyClassifier(private val livingPersonAgeRule: LivingPersonAgeRule = LivingPersonAgeRule()) {

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

        val blockingNamedPersonStatus = record.namedPersons
            .map(livingPersonAgeRule::evaluate)
            .firstOrNull { it != PersonAgeStatus.DECEASED }

        if (blockingNamedPersonStatus != null) {
            return PrivacyClassificationResult(
                status = PrivacyClassificationStatus.BLOCKED,
                reason = when (blockingNamedPersonStatus) {
                    PersonAgeStatus.LIKELY_LIVING -> PrivacyClassificationReasons.NAMED_PERSON_LIKELY_LIVING
                    PersonAgeStatus.UNKNOWN_FAILCLOSED -> PrivacyClassificationReasons.NAMED_PERSON_AGE_UNKNOWN_FAILCLOSED
                    PersonAgeStatus.DECEASED -> PrivacyClassificationReasons.UNEXPECTED_ERROR
                },
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
