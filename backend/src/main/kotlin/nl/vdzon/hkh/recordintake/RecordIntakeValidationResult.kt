package nl.vdzon.hkh.recordintake

/**
 * Resultaat van [RecordIntakeValidator.validate]. `fieldErrorPaths` verzamelt alle ontbrekende of
 * ongeldige verplichte velden (ontdubbeld en lexicografisch gesorteerd, onafhankelijk van de
 * uitvoeringsvolgorde). `privacyBlocked` is een volledig geïsoleerde uitkomst van de fail-closed
 * privacyregel en staat los van de overige veldfouten.
 */
data class RecordIntakeValidationResult(
    val fieldErrorPaths: List<String>,
    val privacyBlocked: Boolean,
) {
    val valid: Boolean get() = fieldErrorPaths.isEmpty() && !privacyBlocked
}

/** Machineleesbare, technische foutcode voor de fail-closed privacyregel. */
const val PRIVACY_CLASSIFICATION_BLOCKED_CODE = "PRIVACY_CLASSIFICATION_BLOCKED"

/** Vaste, machineleesbare veldpaden. */
object RecordIntakeFieldPaths {
    const val LOCAL_IDENTIFIER = "localIdentifier"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val DATING = "dating"
    const val PROVENANCE = "provenance"
    const val RIGHTS_STATUS = "rightsStatus"
    const val PRIVACY_CLASSIFICATION = "privacyClassification"
    const val ACCESS_URL = "accessUrl"
}
