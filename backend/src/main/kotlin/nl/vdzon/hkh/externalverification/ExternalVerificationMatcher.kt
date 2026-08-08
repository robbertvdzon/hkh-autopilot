package nl.vdzon.hkh.externalverification

/**
 * Deterministische, fail-closed matcher die het lokale record vergelijkt met het opgehaalde
 * archiefrecord.
 *
 * Een record krijgt alleen [ExternalVerificationStatus.VERIFIED] wanneer naam, geboortedatum én
 * overlijdensdatum alle drie (na normalisatie) overeenkomen met het opgehaalde archiefrecord. In
 * alle overige gevallen - geen match, geen gevonden archiefrecord (bijvoorbeeld een niet-bestaande
 * of ongeldige guid), of een endpoint dat een toegangstoken eist - is de uitkomst
 * [ExternalVerificationStatus.UNVERIFIED] met een leesbare reden. Er ontsnapt nooit een
 * uitzondering: een onverwachte fout levert fail-closed een [ExternalVerificationStatus.UNVERIFIED]
 * resultaat op.
 */
object ExternalVerificationMatcher {

    fun match(local: ExternalVerificationRequest, fetched: ArchiveFetchResult): ExternalVerificationMatchResult =
        runCatching { evaluate(local, fetched) }.getOrElse { failClosed() }

    private fun evaluate(
        local: ExternalVerificationRequest,
        fetched: ArchiveFetchResult,
    ): ExternalVerificationMatchResult = when (fetched) {
        is ArchiveFetchResult.Found -> {
            val matchedFields = mutableListOf<String>()
            if (local.name.normalize() == fetched.fields.name.normalize()) {
                matchedFields += ExternalVerificationMatchableFields.NAME
            }
            if (local.birthDate.normalize() == fetched.fields.birthDate.normalize()) {
                matchedFields += ExternalVerificationMatchableFields.BIRTH_DATE
            }
            if (local.deathDate.normalize() == fetched.fields.deathDate.normalize()) {
                matchedFields += ExternalVerificationMatchableFields.DEATH_DATE
            }

            if (matchedFields.containsAll(REQUIRED_FIELDS)) {
                ExternalVerificationMatchResult(ExternalVerificationStatus.VERIFIED, matchedFields, ExternalVerificationReasons.VERIFIED)
            } else {
                ExternalVerificationMatchResult(
                    ExternalVerificationStatus.UNVERIFIED,
                    matchedFields,
                    ExternalVerificationReasons.FIELDS_DO_NOT_MATCH,
                )
            }
        }

        ArchiveFetchResult.NotFound -> ExternalVerificationMatchResult(
            ExternalVerificationStatus.UNVERIFIED,
            emptyList(),
            ExternalVerificationReasons.NOT_FOUND,
        )

        ArchiveFetchResult.AuthenticationRequired -> ExternalVerificationMatchResult(
            ExternalVerificationStatus.UNVERIFIED,
            emptyList(),
            ExternalVerificationReasons.AUTHENTICATION_REQUIRED,
        )
    }

    private fun failClosed(): ExternalVerificationMatchResult = ExternalVerificationMatchResult(
        ExternalVerificationStatus.UNVERIFIED,
        emptyList(),
        ExternalVerificationReasons.UNEXPECTED_ERROR,
    )

    private val REQUIRED_FIELDS = listOf(
        ExternalVerificationMatchableFields.NAME,
        ExternalVerificationMatchableFields.BIRTH_DATE,
        ExternalVerificationMatchableFields.DEATH_DATE,
    )

    private fun String?.normalize(): String? = this?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}
