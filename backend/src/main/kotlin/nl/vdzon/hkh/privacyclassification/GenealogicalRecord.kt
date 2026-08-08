package nl.vdzon.hkh.privacyclassification

/**
 * Intern genealogisch record (bijvoorbeeld een bidprentje).
 *
 * De overlijdensstatus is met opzet als ruwe tekst gemodelleerd. Zo kan een record zowel een
 * ontbrekende (`null`) als een niet-herkende waarde bevatten zonder dat constructie of
 * deserialisatie faalt; [PrivacyClassifier] beoordeelt zulke waarden fail-closed als [DeceasedStatus.ONBEKEND].
 */
data class GenealogicalRecord(
    val deceasedStatus: String? = null,
    val nextOfKin: LivingNextOfKinFields = LivingNextOfKinFields(),
    val namedPersons: List<NamedPerson> = emptyList(),
)

/**
 * Eén in het record genoemde persoon (de hoofdpersoon of een familielid), voor de FamilySearch
 * 110/95-jaarregel in [LivingPersonAgeRule].
 *
 * De datumvelden zijn met opzet als ruwe tekst gemodelleerd, naar het patroon van
 * `ExternalVerificationRequest.birthDate`: zo kan een persoon zowel een ontbrekende (`null`) als
 * een onleesbare waarde bevatten zonder dat constructie faalt; [LivingPersonAgeRule] beoordeelt
 * zulke waarden fail-closed.
 */
data class NamedPerson(
    val birthDate: String? = null,
    val marriageDate: String? = null,
    val childBirthDate: String? = null,
    val deathDate: String? = null,
    val burialDate: String? = null,
)

/**
 * Benoemde velden die, indien gezet, een nog levende nabestaande identificeren. Aanwezigheid van
 * een niet-lege waarde in een van deze velden is voldoende om het record te blokkeren.
 */
data class LivingNextOfKinFields(
    val contactName: String? = null,
    val contactAddress: String? = null,
    val contactPhoneNumber: String? = null,
) {
    fun hasIdentifyingField(): Boolean =
        !contactName.isMissing() || !contactAddress.isMissing() || !contactPhoneNumber.isMissing()
}

/** Gecontroleerde waarden voor de overlijdensstatus. */
enum class DeceasedStatus(val wireValue: String) {
    OVERLEDEN("overleden"),
    LEVEND("levend"),
    ONBEKEND("onbekend"),
    ;

    companion object {
        /** Fail-closed: een ontbrekende of niet-herkende waarde levert [ONBEKEND] op. */
        fun parse(raw: String?): DeceasedStatus = entries.firstOrNull { it.wireValue == raw.normalize() } ?: ONBEKEND
    }
}

private fun String?.normalize(): String? = this?.trim()?.lowercase()

private fun String?.isMissing(): Boolean = this == null || this.trim().isEmpty()
