package nl.vdzon.hkh.linkdossier

/**
 * Intern koppelingsdossier: exact twee geordende records en een relatie daartussen.
 *
 * Gecontroleerde waarden zijn met opzet als ruwe tekst gemodelleerd. Zo kan een dossier zowel een
 * ontbrekende (`null`) als een niet-herkende waarde bevatten zonder dat constructie of
 * deserialisatie faalt; de validator keurt zulke waarden af in plaats van te klappen.
 */
data class LinkDossier(
    val records: List<LinkDossierRecord> = emptyList(),
    val relation: LinkDossierRelation = LinkDossierRelation(),
)

data class LinkDossierRecord(
    val sourceHolder: String? = null,
    val permanentUrl: String? = null,
    val identifier: String? = null,
    val title: String? = null,
    val description: String? = null,
    val dating: RecordDating = RecordDating(),
    val metadataRights: String? = null,
    val objectRights: String? = null,
    val privacyClassification: String? = null,
)

data class RecordDating(
    val value: String? = null,
    val uncertainty: String? = null,
)

data class LinkDossierRelation(
    val relationType: String? = null,
    val connectionGround: String? = null,
    val evidenceLinks: List<String?> = emptyList(),
    val confirmationStatus: String? = null,
)

/** Gecontroleerde waarden voor metadatarechten en objectrechten. */
enum class RightsClassification(val wireValue: String) {
    TOEGESTAAN("toegestaan"),
    NIET_TOEGESTAAN("niet toegestaan"),
    ONDUIDELIJK("onduidelijk"),
    ;

    companion object {
        fun parse(raw: String?): RightsClassification? = entries.firstOrNull { it.wireValue == raw.normalize() }
    }
}

/** Gecontroleerde waarden voor de privacyclassificatie. */
enum class PrivacyClassification(val wireValue: String) {
    OPENBAAR("openbaar"),
    BEPERKT("beperkt"),
    ONDUIDELIJK("onduidelijk"),
    ;

    companion object {
        fun parse(raw: String?): PrivacyClassification? = entries.firstOrNull { it.wireValue == raw.normalize() }
    }
}

/** Gecontroleerde waarden voor de dateringsonzekerheid. */
enum class DatingUncertainty(val wireValue: String) {
    ZEKER("zeker"),
    GESCHAT("geschat"),
    ONBEKEND("onbekend"),
    ;

    companion object {
        fun parse(raw: String?): DatingUncertainty? = entries.firstOrNull { it.wireValue == raw.normalize() }
    }
}

/** Gecontroleerde waarden voor de bevestigingsstatus van de relatie. */
enum class ConfirmationStatus(val wireValue: String) {
    BEVESTIGD("bevestigd"),
    HYPOTHESE("hypothese"),
    ;

    companion object {
        fun parse(raw: String?): ConfirmationStatus? = entries.firstOrNull { it.wireValue == raw.normalize() }
    }
}

private fun String?.normalize(): String? = this?.trim()?.lowercase()
