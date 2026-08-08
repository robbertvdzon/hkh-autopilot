package nl.vdzon.hkh.privacyclassification

/**
 * Losstaande, herbruikbare guard die publicatie van een genealogisch record weigert zodra de
 * privacyclassificatie [PrivacyClassificationStatus.BLOCKED] is. Er is nog geen bestaande
 * publicatieworkflow om op aan te sluiten; een latere publicatiefeature kan deze functie
 * hergebruiken.
 */
object PrivacyPublishGuard {

    /** Weigert publicatie met [PrivacyPublishBlockedException] wanneer [result] geblokkeerd is. */
    fun assertPublishable(result: PrivacyClassificationResult) {
        if (!result.processable) {
            throw PrivacyPublishBlockedException(result.reason)
        }
    }
}

/** Geworpen door [PrivacyPublishGuard] wanneer publicatie van een geblokkeerd record wordt geweigerd. */
class PrivacyPublishBlockedException(reason: String) : RuntimeException(reason)
