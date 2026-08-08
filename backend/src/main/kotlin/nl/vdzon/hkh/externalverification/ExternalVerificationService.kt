package nl.vdzon.hkh.externalverification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class ExternalVerificationOutcome(
    val record: ExternalVerificationRecord,
    val reason: String,
)

/**
 * Orchestreert een verificatie: bevraagt archieven.nl, vergelijkt de kernvelden en slaat uitsluitend
 * de minimale verificatievelden op. Deze service veronderstelt dat de aanroeper het verzoek al met
 * [ExternalVerificationValidator] heeft goedgekeurd: er wordt hier niet opnieuw op verplichte velden
 * gecontroleerd. Er wordt nooit een tokenwaarde gelogd, ook niet bij een fout.
 */
@Service
class ExternalVerificationService(
    private val client: ArchivesNlClient,
    private val store: ExternalVerificationStore,
    private val tokenCipher: ExternalVerificationTokenCipher,
) {
    private val log = LoggerFactory.getLogger(ExternalVerificationService::class.java)

    fun verify(request: ExternalVerificationRequest): ExternalVerificationOutcome {
        val adtid = request.adtid!!.trim()
        val guid = request.guid!!.trim()
        val externalUri = archivesNlUri(adtid, guid)

        val fetchResult = runCatching { client.fetch(adtid, guid, request.accessToken) }
            .getOrElse { ArchiveFetchResult.NotFound }
        val matchResult = ExternalVerificationMatcher.match(request, fetchResult)
        val licenseResult = ExternalVerificationLicenseEvaluator.evaluate(fetchResult)

        val encryptedAccessToken = request.accessToken
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { tokenCipher.encrypt(it) }

        val record = store.create(
            localIdentifier = request.localIdentifier!!,
            externalUri = externalUri,
            matchedFields = matchResult.matchedFields,
            status = matchResult.status,
            encryptedAccessToken = encryptedAccessToken,
            licenseStatus = licenseResult.status,
            licenseValue = licenseResult.licenseValue,
        )

        log.info(
            "External verification completed for local record {}: status={}, matchedFields={}, licenseStatus={}",
            record.localIdentifier,
            record.status,
            record.matchedFields,
            record.licenseStatus,
        )

        return ExternalVerificationOutcome(record, matchResult.reason)
    }
}
