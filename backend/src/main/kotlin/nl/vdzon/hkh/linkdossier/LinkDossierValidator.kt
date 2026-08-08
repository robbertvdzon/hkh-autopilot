package nl.vdzon.hkh.linkdossier

import java.net.URI
import nl.vdzon.hkh.linkdossier.LinkDossierFieldPaths as Paths

/**
 * Deterministische, fail-closed validator voor een intern koppelingsdossier.
 *
 * De validator verzamelt altijd alle overtredingen (hij stopt nooit bij de eerste) en beoordeelt de
 * metadata-link en de objectmedia-uitvoer volledig los van elkaar. Hij raadpleegt geen externe
 * bronnen: alleen vorm, volledigheid, gecontroleerde waarden en interne samenhang worden getoetst.
 * Er ontsnapt nooit een uitzondering; onverwachte fouten leiden tot een geblokkeerd dossier.
 */
class LinkDossierValidator {

    fun validate(dossier: LinkDossier): LinkDossierValidationResult =
        runCatching { evaluate(dossier) }.getOrElse { failClosed() }

    private fun evaluate(dossier: LinkDossier): LinkDossierValidationResult {
        val metadataBlocks = mutableSetOf<String>()
        val objectMediaBlocks = mutableSetOf<String>()

        val records = dossier.records
        if (records.size != EXPECTED_RECORD_COUNT) {
            metadataBlocks += Paths.RECORDS
            objectMediaBlocks += Paths.RECORDS
        }

        records.forEachIndexed { index, record ->
            validateRecord(index, record, metadataBlocks, objectMediaBlocks)
        }
        validateDistinctReferences(records, metadataBlocks)
        validateRelation(dossier.relation, metadataBlocks)

        return LinkDossierValidationResult(
            status = if (metadataBlocks.isEmpty()) {
                DossierStatus.PUBLICEERBAAR_ALS_METADATA_LINK
            } else {
                DossierStatus.GEBLOKKEERD
            },
            objectMediaAllowed = objectMediaBlocks.isEmpty(),
            metadataBlockingFieldPaths = metadataBlocks.stable(),
            objectMediaBlockingFieldPaths = objectMediaBlocks.stable(),
        )
    }

    private fun validateRecord(
        index: Int,
        record: LinkDossierRecord,
        metadataBlocks: MutableSet<String>,
        objectMediaBlocks: MutableSet<String>,
    ) {
        fun path(field: String) = Paths.record(index, field)

        if (record.sourceHolder.isMissing()) metadataBlocks += path(Paths.SOURCE_HOLDER)

        // Alternatievenpaar: minimaal een van beide is vereist, maar een wel opgegeven permanente
        // URL moet altijd een absolute http(s)-URL zijn - ook als de identifier geldig is.
        if (record.permanentUrl.isMissing() && record.identifier.isMissing()) {
            metadataBlocks += path(Paths.PERMANENT_URL)
            metadataBlocks += path(Paths.IDENTIFIER)
        }
        if (!record.permanentUrl.isMissing() && !isAbsoluteHttpUrl(record.permanentUrl)) {
            metadataBlocks += path(Paths.PERMANENT_URL)
        }

        if (record.title.isMissing() && record.description.isMissing()) {
            metadataBlocks += path(Paths.TITLE)
            metadataBlocks += path(Paths.DESCRIPTION)
        }

        if (record.dating.value.isMissing()) metadataBlocks += path(Paths.DATING_VALUE)
        if (DatingUncertainty.parse(record.dating.uncertainty) == null) {
            metadataBlocks += path(Paths.DATING_UNCERTAINTY)
        }

        // Fail-closed: alleen expliciet 'toegestaan' respectievelijk 'openbaar' geeft vrij.
        if (RightsClassification.parse(record.metadataRights) != RightsClassification.TOEGESTAAN) {
            metadataBlocks += path(Paths.METADATA_RIGHTS)
        }
        if (PrivacyClassification.parse(record.privacyClassification) != PrivacyClassification.OPENBAAR) {
            metadataBlocks += path(Paths.PRIVACY_CLASSIFICATION)
        }

        // Objectrechten worden volledig apart beoordeeld en blokkeren nooit de metadata-link.
        if (RightsClassification.parse(record.objectRights) != RightsClassification.TOEGESTAAN) {
            objectMediaBlocks += path(Paths.OBJECT_RIGHTS)
        }
    }

    private fun validateDistinctReferences(
        records: List<LinkDossierRecord>,
        metadataBlocks: MutableSet<String>,
    ) {
        if (records.size != EXPECTED_RECORD_COUNT) return
        val first = records[0].stableReference(0) ?: return
        val second = records[1].stableReference(1) ?: return
        if (first.value == second.value) {
            metadataBlocks += first.path
            metadataBlocks += second.path
        }
    }

    private fun validateRelation(relation: LinkDossierRelation, metadataBlocks: MutableSet<String>) {
        if (relation.relationType.isMissing()) metadataBlocks += Paths.RELATION_TYPE
        if (relation.connectionGround.isMissing()) metadataBlocks += Paths.RELATION_CONNECTION_GROUND

        val links = relation.evidenceLinks
        val hasInvalidLink = links.any { it.isMissing() || !isAbsoluteHttpUrl(it) }
        if (links.isEmpty() || hasInvalidLink) metadataBlocks += Paths.RELATION_EVIDENCE_LINKS

        if (ConfirmationStatus.parse(relation.confirmationStatus) != ConfirmationStatus.BEVESTIGD) {
            metadataBlocks += Paths.RELATION_CONFIRMATION_STATUS
        }
    }

    private data class StableReference(val path: String, val value: String)

    /** De stabiele referentie is de permanente URL en anders de identifier. */
    private fun LinkDossierRecord.stableReference(index: Int): StableReference? = when {
        !permanentUrl.isMissing() ->
            StableReference(Paths.record(index, Paths.PERMANENT_URL), permanentUrl!!.trim())
        !identifier.isMissing() ->
            StableReference(Paths.record(index, Paths.IDENTIFIER), identifier!!.trim())
        else -> null
    }

    private fun isAbsoluteHttpUrl(raw: String?): Boolean {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty()) return false
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
        if (!uri.isAbsolute) return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        return !uri.host.isNullOrBlank()
    }

    private fun String?.isMissing(): Boolean = this == null || this.trim().isEmpty()

    private fun Set<String>.stable(): List<String> = sorted()

    private fun failClosed(): LinkDossierValidationResult = LinkDossierValidationResult(
        status = DossierStatus.GEBLOKKEERD,
        objectMediaAllowed = false,
        metadataBlockingFieldPaths = listOf(Paths.RECORDS),
        objectMediaBlockingFieldPaths = listOf(Paths.RECORDS),
    )

    private companion object {
        const val EXPECTED_RECORD_COUNT = 2
    }
}
