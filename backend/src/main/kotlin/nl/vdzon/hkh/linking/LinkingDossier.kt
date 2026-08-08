package nl.vdzon.hkh.linking

/** Raw internal dossier input. Controlled values are validated fail-closed by [LinkingDossierValidator]. */
data class LinkingDossier(
    val records: List<LinkingRecord>,
    val relation: LinkingRelation?,
)

data class LinkingRecord(
    val sourceHolder: String?,
    val permanentUrl: String?,
    val identifier: String?,
    val title: String?,
    val description: String?,
    val dating: RecordDating?,
    val metadataRights: String?,
    val objectRights: String?,
    val privacyClassification: String?,
)

data class RecordDating(
    val value: String?,
    val uncertainty: String?,
)

data class LinkingRelation(
    val relationType: String?,
    val connectionBasis: String?,
    val evidenceLinks: List<String>,
    val confirmationStatus: String?,
)

enum class DossierStatus(val value: String) {
    PUBLISHABLE_AS_METADATA_LINK("publiceerbaar als metadata-link"),
    BLOCKED("geblokkeerd"),
}

data class LinkingDossierValidationResult(
    val status: DossierStatus,
    val objectMediaAllowed: Boolean,
    val metadataLinkBlockingFieldPaths: List<String>,
    val objectMediaBlockingFieldPaths: List<String>,
)

object LinkingDossierControlledValues {
    const val ALLOWED = "toegestaan"
    const val NOT_ALLOWED = "niet toegestaan"
    const val UNCLEAR = "onduidelijk"

    const val PUBLIC = "openbaar"
    const val RESTRICTED = "beperkt"

    const val CERTAIN = "zeker"
    const val ESTIMATED = "geschat"
    const val UNKNOWN = "onbekend"

    const val CONFIRMED = "bevestigd"
    const val HYPOTHESIS = "hypothese"
}
