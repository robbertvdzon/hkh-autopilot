package nl.vdzon.hkh.recordintake

import java.net.URI
import org.springframework.stereotype.Component
import nl.vdzon.hkh.recordintake.RecordIntakeFieldPaths as Paths

/**
 * Deterministische, fail-closed validator voor een enkel record-intakeverzoek.
 *
 * De validator verzamelt altijd alle veldfouten (hij stopt nooit bij de eerste). De privacyregel
 * wordt volledig los van de overige veldfouten beoordeeld: een niet-toegestane classificatie
 * blokkeert opslag onafhankelijk van of andere velden wel geldig zijn. Er ontsnapt nooit een
 * uitzondering; een onverwachte fout leidt tot een volledig geblokkeerd resultaat.
 */
@Component
class RecordIntakeValidator {

    fun validate(intake: RecordIntake): RecordIntakeValidationResult =
        runCatching { evaluate(intake) }.getOrElse { failClosed() }

    private fun evaluate(intake: RecordIntake): RecordIntakeValidationResult {
        val fieldErrors = mutableSetOf<String>()

        if (intake.localIdentifier.isMissing()) fieldErrors += Paths.LOCAL_IDENTIFIER

        // Alternatievenpaar: minimaal een van beide is vereist.
        if (intake.title.isMissing() && intake.description.isMissing()) {
            fieldErrors += Paths.TITLE
            fieldErrors += Paths.DESCRIPTION
        }

        if (intake.dating.isMissing()) fieldErrors += Paths.DATING
        if (intake.provenance.isMissing()) fieldErrors += Paths.PROVENANCE
        if (intake.rightsStatus.isMissing()) fieldErrors += Paths.RIGHTS_STATUS
        if (!isAbsoluteHttpUrl(intake.accessUrl)) fieldErrors += Paths.ACCESS_URL

        // Fail-closed privacyregel: een niet-herkende waarde is een gewone veldfout, maar een
        // herkende, niet-toegestane classificatie blokkeert onafhankelijk van de overige velden.
        val privacyClassification = PrivacyClassification.parse(intake.privacyClassification)
        val privacyBlocked = if (privacyClassification == null) {
            fieldErrors += Paths.PRIVACY_CLASSIFICATION
            false
        } else {
            privacyClassification != PrivacyClassification.GEEN_PERSOONSGEGEVENS
        }

        return RecordIntakeValidationResult(
            fieldErrorPaths = fieldErrors.sorted(),
            privacyBlocked = privacyBlocked,
        )
    }

    /** Alleen `true` wanneer duurzame URL, koppelmotivering en onzekerheidswaarde alle drie geldig zijn. */
    fun isValidExternalLink(link: RecordIntakeExternalLinkInput?): Boolean {
        if (link == null) return false
        if (!isAbsoluteHttpUrl(link.durableUrl)) return false
        if (link.linkRationale.isMissing()) return false
        return LinkUncertainty.parse(link.uncertainty) != null
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

    private fun failClosed(): RecordIntakeValidationResult = RecordIntakeValidationResult(
        fieldErrorPaths = listOf(Paths.LOCAL_IDENTIFIER),
        privacyBlocked = true,
    )
}
