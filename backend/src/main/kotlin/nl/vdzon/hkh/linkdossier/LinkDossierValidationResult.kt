package nl.vdzon.hkh.linkdossier

/** Dossierbrede uitkomst van de validatie. */
enum class DossierStatus(val label: String) {
    PUBLICEERBAAR_ALS_METADATA_LINK("publiceerbaar als metadata-link"),
    GEBLOKKEERD("geblokkeerd"),
}

/**
 * Resultaat van [LinkDossierValidator]. De twee blokkadelijsten zijn afzonderlijk beoordeeld,
 * ontdubbeld en lexicografisch gesorteerd, onafhankelijk van de uitvoeringsvolgorde.
 */
data class LinkDossierValidationResult(
    val status: DossierStatus,
    val objectMediaAllowed: Boolean,
    val metadataBlockingFieldPaths: List<String>,
    val objectMediaBlockingFieldPaths: List<String>,
)

/** Vaste, machineleesbare veldpaden. */
object LinkDossierFieldPaths {
    const val RECORDS = "records"
    const val RELATION_TYPE = "relation.relationType"
    const val RELATION_CONNECTION_GROUND = "relation.connectionGround"
    const val RELATION_EVIDENCE_LINKS = "relation.evidenceLinks"
    const val RELATION_CONFIRMATION_STATUS = "relation.confirmationStatus"

    fun record(index: Int, field: String): String = "$RECORDS[$index].$field"

    const val SOURCE_HOLDER = "sourceHolder"
    const val PERMANENT_URL = "permanentUrl"
    const val IDENTIFIER = "identifier"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val DATING_VALUE = "dating.value"
    const val DATING_UNCERTAINTY = "dating.uncertainty"
    const val METADATA_RIGHTS = "metadataRights"
    const val OBJECT_RIGHTS = "objectRights"
    const val PRIVACY_CLASSIFICATION = "privacyClassification"
}
