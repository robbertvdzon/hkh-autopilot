package nl.vdzon.hkh.externalverification

import java.net.URI
import java.time.Instant

/** Rechten op de beschrijvende metadata, los van rechten op het bronobject of de media. */
enum class MetadataRightsStatus {
    ALLOWED,
    RESTRICTED,
    UNKNOWN,
}

/** Rechten op het afzonderlijke object of de media; deze status geeft nooit vanzelf media-toestemming. */
enum class ObjectMediaRightsStatus {
    ALLOWED,
    RESTRICTED,
    UNKNOWN,
}

enum class HistoricalMetadataPrivacyStatus {
    CLEAR,
    BLOCKED,
    UNKNOWN,
}

enum class HistoricalMetadataAvailabilityStatus {
    AVAILABLE,
    TEMPORARILY_UNAVAILABLE,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
}

enum class HistoricalMetadataVerificationStatus {
    VERIFIED,
    UNVERIFIED,
}

/** Machineleesbare redenen voor de afgeleide verificatiestatus. */
object HistoricalMetadataVerificationReasons {
    const val VERIFIED = "VERIFIED"
    const val MISSING_REQUIRED_FIELD = "MISSING_REQUIRED_FIELD"
    const val INVALID_REQUIRED_FIELD = "INVALID_REQUIRED_FIELD"
    const val CONTRADICTORY_SOURCE_DATA = "CONTRADICTORY_SOURCE_DATA"
    const val METADATA_RIGHTS_UNKNOWN = "METADATA_RIGHTS_UNKNOWN"
    const val METADATA_RIGHTS_RESTRICTED = "METADATA_RIGHTS_RESTRICTED"
    const val PRIVACY_UNKNOWN = "PRIVACY_UNKNOWN"
    const val PRIVACY_BLOCKED = "PRIVACY_BLOCKED"
    const val SOURCE_TEMPORARILY_UNAVAILABLE = "SOURCE_TEMPORARILY_UNAVAILABLE"
    const val EMPTY_SOURCE_RESPONSE = "EMPTY_SOURCE_RESPONSE"
    const val INVALID_SOURCE_RESPONSE = "INVALID_SOURCE_RESPONSE"
}

/** Alleen de veilige, gevalideerde metadata die een volledig geverifieerd resultaat mag bevatten. */
data class VerifiedHistoricalMetadata(
    val sourceIdentifier: String,
    val sourceLink: String,
    val holder: String,
    val title: String?,
    val description: String?,
    val dating: String,
    val sourceVersion: String?,
    val snapshotId: String?,
)

/**
 * Invoer voor [HistoricalMetadataContract.evaluate]. De velden blijven bewust ruw genoeg om een
 * ontbrekende of tegenstrijdige bronwaarde fail-closed te kunnen beoordelen. [fallbackSourceIdentifier]
 * en [fallbackSourceLink] zijn uitsluitend bedoeld voor de minimale uitkomst.
 */
data class HistoricalMetadataCandidate(
    val sourceIdentifier: String?,
    val sourceLink: String?,
    val holder: String?,
    val title: String?,
    val description: String?,
    val dating: String?,
    val sourceVersion: String?,
    val snapshotId: String?,
    val fetchedAt: Instant?,
    val metadataRightsStatus: MetadataRightsStatus,
    val objectMediaRightsStatus: ObjectMediaRightsStatus,
    val privacyStatus: HistoricalMetadataPrivacyStatus,
    val availabilityStatus: HistoricalMetadataAvailabilityStatus,
    val containsUnclearedPersonalData: Boolean = false,
    val containsContradictorySourceData: Boolean = false,
    val fallbackSourceIdentifier: String? = null,
    val fallbackSourceLink: String? = null,
)

/**
 * Brononafhankelijk contract voor één extern historisch zoekresultaat. Bij elke ongeldigheid wordt
 * alleen de veilige bronverwijzing en technische status behouden; onbetrouwbare inhoudelijke
 * metadata komt nooit in [metadata].
 */
data class HistoricalMetadataResult(
    val sourceIdentifier: String?,
    val sourceLink: String?,
    val fetchedAt: Instant?,
    val metadataRightsStatus: MetadataRightsStatus,
    val objectMediaRightsStatus: ObjectMediaRightsStatus,
    val privacyStatus: HistoricalMetadataPrivacyStatus,
    val availabilityStatus: HistoricalMetadataAvailabilityStatus,
    val verificationStatus: HistoricalMetadataVerificationStatus,
    val verificationReason: String,
    val metadata: VerifiedHistoricalMetadata? = null,
) {
    val fullyVerified: Boolean get() = verificationStatus == HistoricalMetadataVerificationStatus.VERIFIED
    val mediaAllowed: Boolean
        get() = objectMediaRightsStatus == ObjectMediaRightsStatus.ALLOWED && fullyVerified
}

object HistoricalMetadataContract {

    fun evaluate(candidate: HistoricalMetadataCandidate): HistoricalMetadataResult {
        val safeIdentifier = candidate.fallbackSourceIdentifier.validIdentifierOrNull()
            ?: candidate.sourceIdentifier.validIdentifierOrNull()
        val safeLink = candidate.fallbackSourceLink.validHttpUrlOrNull()
            ?: candidate.sourceLink.validHttpUrlOrNull()

        val reason = invalidReason(candidate)
        if (reason != null) {
            return HistoricalMetadataResult(
                sourceIdentifier = safeIdentifier,
                sourceLink = safeLink,
                fetchedAt = candidate.fetchedAt,
                metadataRightsStatus = candidate.metadataRightsStatus,
                objectMediaRightsStatus = candidate.objectMediaRightsStatus,
                privacyStatus = candidate.privacyStatus,
                availabilityStatus = candidate.availabilityStatus,
                verificationStatus = HistoricalMetadataVerificationStatus.UNVERIFIED,
                verificationReason = reason,
            )
        }

        return HistoricalMetadataResult(
            sourceIdentifier = candidate.sourceIdentifier!!.trim(),
            sourceLink = candidate.sourceLink!!.trim(),
            fetchedAt = candidate.fetchedAt,
            metadataRightsStatus = candidate.metadataRightsStatus,
            objectMediaRightsStatus = candidate.objectMediaRightsStatus,
            privacyStatus = candidate.privacyStatus,
            availabilityStatus = candidate.availabilityStatus,
            verificationStatus = HistoricalMetadataVerificationStatus.VERIFIED,
            verificationReason = HistoricalMetadataVerificationReasons.VERIFIED,
            metadata = VerifiedHistoricalMetadata(
                sourceIdentifier = candidate.sourceIdentifier.trim(),
                sourceLink = candidate.sourceLink.trim(),
                holder = candidate.holder!!.trim(),
                title = candidate.title.cleanTextOrNull(),
                description = candidate.description.cleanTextOrNull(),
                dating = candidate.dating!!.trim(),
                sourceVersion = candidate.sourceVersion.cleanTextOrNull(),
                snapshotId = candidate.snapshotId.cleanTextOrNull(),
            ),
        )
    }

    private fun invalidReason(candidate: HistoricalMetadataCandidate): String? {
        if (candidate.containsContradictorySourceData) return HistoricalMetadataVerificationReasons.CONTRADICTORY_SOURCE_DATA
        if (candidate.fetchedAt == null) return HistoricalMetadataVerificationReasons.MISSING_REQUIRED_FIELD
        if (!candidate.sourceIdentifier.isValidIdentifier()) return HistoricalMetadataVerificationReasons.MISSING_REQUIRED_FIELD
        if (!candidate.sourceLink.isValidHttpUrl()) return HistoricalMetadataVerificationReasons.INVALID_REQUIRED_FIELD
        if (!candidate.holder.isValidText()) return HistoricalMetadataVerificationReasons.MISSING_REQUIRED_FIELD
        if (!candidate.title.isValidText() && !candidate.description.isValidText()) {
            return HistoricalMetadataVerificationReasons.MISSING_REQUIRED_FIELD
        }
        if (!candidate.dating.isValidText()) return HistoricalMetadataVerificationReasons.INVALID_REQUIRED_FIELD
        if (!candidate.sourceVersion.isValidText() && !candidate.snapshotId.isValidText()) {
            return HistoricalMetadataVerificationReasons.MISSING_REQUIRED_FIELD
        }
        if (candidate.availabilityStatus != HistoricalMetadataAvailabilityStatus.AVAILABLE) {
            return when (candidate.availabilityStatus) {
                HistoricalMetadataAvailabilityStatus.TEMPORARILY_UNAVAILABLE ->
                    HistoricalMetadataVerificationReasons.SOURCE_TEMPORARILY_UNAVAILABLE
                HistoricalMetadataAvailabilityStatus.EMPTY_RESPONSE -> HistoricalMetadataVerificationReasons.EMPTY_SOURCE_RESPONSE
                HistoricalMetadataAvailabilityStatus.INVALID_RESPONSE -> HistoricalMetadataVerificationReasons.INVALID_SOURCE_RESPONSE
                HistoricalMetadataAvailabilityStatus.AVAILABLE -> error("unreachable")
            }
        }
        if (candidate.metadataRightsStatus == MetadataRightsStatus.UNKNOWN) {
            return HistoricalMetadataVerificationReasons.METADATA_RIGHTS_UNKNOWN
        }
        if (candidate.metadataRightsStatus == MetadataRightsStatus.RESTRICTED) {
            return HistoricalMetadataVerificationReasons.METADATA_RIGHTS_RESTRICTED
        }
        if (candidate.privacyStatus == HistoricalMetadataPrivacyStatus.UNKNOWN) {
            return HistoricalMetadataVerificationReasons.PRIVACY_UNKNOWN
        }
        if (candidate.privacyStatus == HistoricalMetadataPrivacyStatus.BLOCKED ||
            candidate.containsUnclearedPersonalData
        ) {
            return HistoricalMetadataVerificationReasons.PRIVACY_BLOCKED
        }
        return null
    }

    private fun String?.isValidIdentifier(): Boolean = this.validIdentifierOrNull() != null

    private fun String?.validIdentifierOrNull(): String? = cleanTextOrNull()
        ?.takeIf { it.length <= 500 && it.none(Char::isWhitespace) }

    private fun String?.isValidHttpUrl(): Boolean = validHttpUrlOrNull() != null

    private fun String?.validHttpUrlOrNull(): String? = cleanTextOrNull()?.let { value ->
        runCatching { URI(value) }.getOrNull()?.takeIf {
            it.isAbsolute && (it.scheme.equals("http", true) || it.scheme.equals("https", true)) &&
                !it.host.isNullOrBlank()
        }?.let { value }
    }

    private fun String?.isValidText(): Boolean = cleanTextOrNull()?.let {
        it.length <= 2000 && it.none(Char::isISOControl)
    } != null

    private fun String?.cleanTextOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
