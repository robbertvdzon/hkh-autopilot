package nl.vdzon.hkh.historicalsearch

import java.net.URI

enum class HistoricalAdminStatus {
    CONFIRMED,
    UNKNOWN,
    REJECTED,
    NOT_APPLICABLE,
}

data class HistoricalAdminStatusValue(
    val status: HistoricalAdminStatus,
    val reason: String,
)

data class HistoricalAdminResultStatus(
    val sourceVerification: HistoricalAdminStatusValue,
    val metadataRights: HistoricalAdminStatusValue,
    val privacy: HistoricalAdminStatusValue,
    val publicRelease: HistoricalAdminStatusValue,
    val objectMediaRights: HistoricalAdminStatusValue,
)

/**
 * Deterministic, server-side status mapping for the admin view. It consumes only the normalized
 * provider fields and never infers rights or relationships from titles, queries, URLs or text.
 */
object HistoricalAdminStatusContract {
    fun evaluate(result: HistoricalSearchResult): HistoricalAdminResultStatus {
        val sourceVerification = sourceVerification(result)
        val metadataRights = result.metadataRights.toAdminStatus("metadatarechten")
        val privacy = result.privacyStatus.toAdminStatus("privacy")
        val objectMediaRights = result.objectMediaRights.toAdminStatus("object-/mediarechten")
        val publicRelease = publicRelease(
            sourceVerification,
            metadataRights,
            privacy,
            result.stableIdentifier.isValidStableIdentifier(),
            result.originalSourceUrl.isValidHttpUrl(),
        )
        return HistoricalAdminResultStatus(
            sourceVerification = sourceVerification,
            metadataRights = metadataRights,
            privacy = privacy,
            publicRelease = publicRelease,
            objectMediaRights = objectMediaRights,
        )
    }

    fun safeSourceName(result: HistoricalSearchResult): String? = result.sourceName.asSafeText(500)

    fun safeStableIdentifier(result: HistoricalSearchResult): String? =
        result.stableIdentifier?.takeIf { it.isValidStableIdentifier() }?.asSafeText(500)

    fun safeOriginalSourceUrl(result: HistoricalSearchResult): String? =
        result.originalSourceUrl?.takeIf { it.isValidHttpUrl() }?.asHttpUrl()

    private fun sourceVerification(result: HistoricalSearchResult): HistoricalAdminStatusValue {
        if (result.technicalStatus in setOf(
                HistoricalTechnicalStatus.INVALID_RESPONSE,
                HistoricalTechnicalStatus.INVALID_JSON,
                HistoricalTechnicalStatus.MISSING_REQUIRED_FIELDS,
            )
        ) {
            return rejected("De bronmetadata is ongeldig of onvolledig.")
        }
        if (result.technicalStatus != HistoricalTechnicalStatus.AVAILABLE) {
            return unknown("De bronmetadata kon technisch niet volledig worden vastgesteld.")
        }
        val hasInvalidIdentity = listOf(
            result.sourceName to result.sourceName.asSafeText(500),
            result.stableIdentifier to result.stableIdentifier?.takeIf { it.isValidStableIdentifier() }?.asSafeText(500),
            result.originalSourceUrl to result.originalSourceUrl?.takeIf { it.isValidHttpUrl() }?.asHttpUrl(),
        ).any { (raw, safe) -> !raw.isNullOrBlank() && safe == null }
        if (hasInvalidIdentity) {
            return rejected("De bron heeft ongeldige of tegenstrijdige identiteitsmetadata geleverd.")
        }
        return if (
            result.sourceName.asSafeText(500) != null &&
            result.stableIdentifier.isValidStableIdentifier() &&
            result.originalSourceUrl.isValidHttpUrl()
        ) {
            confirmed("De bronnaam, stabiele identifier en permanente bronlink zijn door de bron geleverd.")
        } else {
            unknown("Niet alle vereiste bronmetadata is aanwezig of veilig vaststelbaar.")
        }
    }

    private fun HistoricalRightsStatus.toAdminStatus(field: String): HistoricalAdminStatusValue = when (this) {
        HistoricalRightsStatus.ALLOWED -> confirmed("De expliciete status voor $field is toegestaan.")
        HistoricalRightsStatus.RESTRICTED -> rejected("De expliciete status voor $field beperkt gebruik.")
        HistoricalRightsStatus.UNKNOWN -> unknown("De expliciete status voor $field ontbreekt of is niet herkenbaar.")
    }

    private fun HistoricalPrivacyStatus.toAdminStatus(field: String): HistoricalAdminStatusValue = when (this) {
        HistoricalPrivacyStatus.CLEAR -> confirmed("De expliciete privacystatus is CLEAR.")
        HistoricalPrivacyStatus.BLOCKED -> rejected("De expliciete privacystatus is BLOCKED.")
        HistoricalPrivacyStatus.UNKNOWN -> unknown("De expliciete privacystatus ontbreekt of is niet herkenbaar.")
    }

    private fun publicRelease(
        sourceVerification: HistoricalAdminStatusValue,
        metadataRights: HistoricalAdminStatusValue,
        privacy: HistoricalAdminStatusValue,
        hasStableIdentifier: Boolean,
        hasPermanentLink: Boolean,
    ): HistoricalAdminStatusValue = when {
        sourceVerification.status == HistoricalAdminStatus.CONFIRMED &&
            metadataRights.status == HistoricalAdminStatus.CONFIRMED &&
            privacy.status == HistoricalAdminStatus.CONFIRMED &&
            hasStableIdentifier && hasPermanentLink ->
            confirmed("Bronverificatie, metadatarechten, privacy en bronidentiteit zijn bevestigd.")
        listOf(sourceVerification, metadataRights, privacy).any { it.status == HistoricalAdminStatus.REJECTED } ->
            rejected("Publieke vrijgave is geblokkeerd door een afgewezen bron-, rechten- of privacystatus.")
        listOf(sourceVerification, metadataRights, privacy).any { it.status == HistoricalAdminStatus.UNKNOWN } ||
            !hasStableIdentifier || !hasPermanentLink ->
            unknown("Publieke vrijgave is niet bevestigd omdat bron-, rechten- of identiteitsinformatie ontbreekt.")
        else -> notApplicable("Publieke vrijgave is voor dit resultaat niet van toepassing.")
    }

    private fun confirmed(reason: String) = HistoricalAdminStatusValue(HistoricalAdminStatus.CONFIRMED, reason)
    private fun unknown(reason: String) = HistoricalAdminStatusValue(HistoricalAdminStatus.UNKNOWN, reason)
    private fun rejected(reason: String) = HistoricalAdminStatusValue(HistoricalAdminStatus.REJECTED, reason)
    private fun notApplicable(reason: String) = HistoricalAdminStatusValue(HistoricalAdminStatus.NOT_APPLICABLE, reason)
}

private fun String?.isValidStableIdentifier(): Boolean = asSafeText(500)?.none(Char::isWhitespace) == true

private fun String?.isValidHttpUrl(): Boolean = asHttpUrl()?.let { value ->
    runCatching { URI(value) }.getOrNull()?.let { uri ->
        uri.isAbsolute && (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            !uri.host.isNullOrBlank()
    } == true
} == true
