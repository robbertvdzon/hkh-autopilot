package nl.vdzon.hkh.externalverification

/**
 * Losstaande, herbruikbare guard die publicatie van een lokaal record weigert zolang de externe
 * verificatie [ExternalVerificationStatus.UNVERIFIED] is, naar het patroon van
 * `PrivacyPublishGuard`. Er is nog geen bestaande publicatieworkflow om op aan te sluiten; een
 * latere publicatiefeature kan deze guard hergebruiken.
 */
object ExternalVerificationPublishGuard {

    /** Weigert publicatie met [ExternalVerificationPublishBlockedException] wanneer [outcome] niet geverifieerd is. */
    fun assertPublishable(outcome: ExternalVerificationOutcome) {
        if (outcome.record.status != ExternalVerificationStatus.VERIFIED.name) {
            throw ExternalVerificationPublishBlockedException(outcome.reason)
        }
    }
}

/** Geworpen door [ExternalVerificationPublishGuard] wanneer publicatie van een niet-geverifieerd record wordt geweigerd. */
class ExternalVerificationPublishBlockedException(reason: String) : RuntimeException(reason)
