package nl.vdzon.hkh.linking

import java.net.URI
import java.util.TreeSet
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.ALLOWED
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.CERTAIN
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.CONFIRMED
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.ESTIMATED
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.NOT_ALLOWED
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.PUBLIC
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.UNCLEAR

/** Deterministic, side-effect-free validation of one internal linking dossier. */
class LinkingDossierValidator {
    fun validate(dossier: LinkingDossier): LinkingDossierValidationResult {
        val metadataBlockers = TreeSet<String>()
        val objectMediaBlockers = TreeSet<String>()

        if (dossier.records.size != REQUIRED_RECORD_COUNT) {
            metadataBlockers += RECORDS_PATH
            objectMediaBlockers += RECORDS_PATH
        }

        dossier.records.forEachIndexed { index, record ->
            validateRecord(record, index, metadataBlockers, objectMediaBlockers)
        }
        validateDistinctReferences(dossier.records, metadataBlockers)
        validateRelation(dossier.relation, metadataBlockers)

        return LinkingDossierValidationResult(
            status = if (metadataBlockers.isEmpty()) {
                DossierStatus.PUBLISHABLE_AS_METADATA_LINK
            } else {
                DossierStatus.BLOCKED
            },
            objectMediaAllowed = objectMediaBlockers.isEmpty(),
            metadataLinkBlockingFieldPaths = metadataBlockers.toList(),
            objectMediaBlockingFieldPaths = objectMediaBlockers.toList(),
        )
    }

    private fun validateRecord(
        record: LinkingRecord,
        index: Int,
        metadataBlockers: MutableSet<String>,
        objectMediaBlockers: MutableSet<String>,
    ) {
        val path = "records[$index]"
        if (record.sourceHolder.isBlank()) metadataBlockers += "$path.sourceHolder"

        if (record.permanentUrl.isBlank() && record.identifier.isBlank()) {
            metadataBlockers += "$path.identifier"
            metadataBlockers += "$path.permanentUrl"
        }
        record.permanentUrl.nonBlank()?.let { url ->
            if (!url.isAbsoluteHttpUrl()) metadataBlockers += "$path.permanentUrl"
        }

        if (record.title.isBlank() && record.description.isBlank()) {
            metadataBlockers += "$path.description"
            metadataBlockers += "$path.title"
        }

        if (record.dating == null) {
            metadataBlockers += "$path.dating.uncertainty"
            metadataBlockers += "$path.dating.value"
        } else {
            if (record.dating.value.isBlank()) metadataBlockers += "$path.dating.value"
            if (record.dating.uncertainty.normalized() !in ACCEPTED_DATING_UNCERTAINTIES) {
                metadataBlockers += "$path.dating.uncertainty"
            }
        }

        if (record.metadataRights.normalized() != ALLOWED) {
            metadataBlockers += "$path.metadataRights"
        }

        val objectRights = record.objectRights.normalized()
        if (objectRights != ALLOWED) objectMediaBlockers += "$path.objectRights"
        if (objectRights !in ACCEPTED_RIGHTS) metadataBlockers += "$path.objectRights"

        if (record.privacyClassification.normalized() != PUBLIC) {
            metadataBlockers += "$path.privacyClassification"
        }
    }

    private fun validateDistinctReferences(
        records: List<LinkingRecord>,
        metadataBlockers: MutableSet<String>,
    ) {
        val references = mutableMapOf<String, MutableList<ReferenceField>>()
        records.forEachIndexed { index, record ->
            record.permanentUrl.nonBlank()?.let { value ->
                references.getOrPut(value) { mutableListOf() } += ReferenceField(index, "permanentUrl")
            }
            record.identifier.nonBlank()?.let { value ->
                references.getOrPut(value) { mutableListOf() } += ReferenceField(index, "identifier")
            }
        }

        references.values
            .filter { fields -> fields.map(ReferenceField::recordIndex).distinct().size > 1 }
            .flatten()
            .forEach { field -> metadataBlockers += "records[${field.recordIndex}].${field.name}" }
    }

    private fun validateRelation(
        relation: LinkingRelation?,
        metadataBlockers: MutableSet<String>,
    ) {
        if (relation == null) {
            metadataBlockers += "relation.confirmationStatus"
            metadataBlockers += "relation.connectionBasis"
            metadataBlockers += "relation.evidenceLinks"
            metadataBlockers += "relation.relationType"
            return
        }

        if (relation.relationType.isBlank()) metadataBlockers += "relation.relationType"
        if (relation.connectionBasis.isBlank()) metadataBlockers += "relation.connectionBasis"

        if (relation.evidenceLinks.isEmpty()) {
            metadataBlockers += "relation.evidenceLinks"
        } else {
            relation.evidenceLinks.forEachIndexed { index, evidenceLink ->
                if (!evidenceLink.isAbsoluteHttpUrl()) {
                    metadataBlockers += "relation.evidenceLinks[$index]"
                }
            }
        }

        if (relation.confirmationStatus.normalized() != CONFIRMED) {
            metadataBlockers += "relation.confirmationStatus"
        }
    }

    private fun String?.isBlank(): Boolean = this == null || this.trim().isEmpty()

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun String?.normalized(): String? = nonBlank()

    private fun String.isAbsoluteHttpUrl(): Boolean = runCatching {
        val uri = URI(this.trim())
        uri.isAbsolute && uri.scheme.lowercase() in HTTP_SCHEMES && uri.host != null
    }.getOrDefault(false)

    private data class ReferenceField(val recordIndex: Int, val name: String)

    companion object {
        private const val REQUIRED_RECORD_COUNT = 2
        private const val RECORDS_PATH = "records"
        private val HTTP_SCHEMES = setOf("http", "https")
        private val ACCEPTED_RIGHTS = setOf(ALLOWED, NOT_ALLOWED, UNCLEAR)
        private val ACCEPTED_DATING_UNCERTAINTIES = setOf(CERTAIN, ESTIMATED)
    }
}
