package nl.vdzon.hkh.externalverification

import org.springframework.stereotype.Component
import nl.vdzon.hkh.externalverification.ExternalVerificationFieldPaths as Paths

/**
 * Deterministische, fail-closed validator voor een enkel verificatieverzoek.
 *
 * Verzamelt altijd alle veldfouten (stopt nooit bij de eerste), naar het patroon van
 * `RecordIntakeValidator`. Er ontsnapt nooit een uitzondering; een onverwachte fout leidt tot een
 * volledig geblokkeerd resultaat (alle verplichte velden gerapporteerd als ontbrekend).
 */
@Component
class ExternalVerificationValidator {

    fun validate(request: ExternalVerificationRequest): ExternalVerificationValidationResult =
        runCatching { evaluate(request) }.getOrElse { failClosed() }

    private fun evaluate(request: ExternalVerificationRequest): ExternalVerificationValidationResult {
        val fieldErrors = mutableSetOf<String>()

        if (request.localIdentifier.isMissing()) fieldErrors += Paths.LOCAL_IDENTIFIER
        if (request.name.isMissing()) fieldErrors += Paths.NAME
        if (request.birthDate.isMissing()) fieldErrors += Paths.BIRTH_DATE
        if (request.deathDate.isMissing()) fieldErrors += Paths.DEATH_DATE
        if (request.adtid.isMissing()) fieldErrors += Paths.ADTID
        if (request.guid.isMissing()) fieldErrors += Paths.GUID

        return ExternalVerificationValidationResult(fieldErrors.sorted())
    }

    private fun String?.isMissing(): Boolean = this == null || this.trim().isEmpty()

    private fun failClosed(): ExternalVerificationValidationResult = ExternalVerificationValidationResult(
        fieldErrorPaths = listOf(
            Paths.LOCAL_IDENTIFIER,
            Paths.NAME,
            Paths.BIRTH_DATE,
            Paths.DEATH_DATE,
            Paths.ADTID,
            Paths.GUID,
        ),
    )
}
