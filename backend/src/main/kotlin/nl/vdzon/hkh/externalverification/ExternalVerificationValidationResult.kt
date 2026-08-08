package nl.vdzon.hkh.externalverification

/**
 * Resultaat van [ExternalVerificationValidator.validate]. `fieldErrorPaths` verzamelt alle
 * ontbrekende of ongeldige verplichte velden (ontdubbeld en lexicografisch gesorteerd, onafhankelijk
 * van de uitvoeringsvolgorde), naar het patroon van `RecordIntakeValidationResult`.
 */
data class ExternalVerificationValidationResult(val fieldErrorPaths: List<String>) {
    val valid: Boolean get() = fieldErrorPaths.isEmpty()
}

/** Vaste, machineleesbare veldpaden. */
object ExternalVerificationFieldPaths {
    const val LOCAL_IDENTIFIER = "localIdentifier"
    const val NAME = "name"
    const val BIRTH_DATE = "birthDate"
    const val DEATH_DATE = "deathDate"
    const val ADTID = "adtid"
    const val GUID = "guid"
}
