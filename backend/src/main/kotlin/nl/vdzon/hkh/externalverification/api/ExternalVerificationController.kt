package nl.vdzon.hkh.externalverification.api

import java.time.Instant
import nl.vdzon.hkh.externalverification.ExternalVerificationRequest
import nl.vdzon.hkh.externalverification.ExternalVerificationService
import nl.vdzon.hkh.externalverification.ExternalVerificationValidator
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ExternalVerificationApiRequest(
    val localIdentifier: String? = null,
    val name: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val adtid: String? = null,
    val guid: String? = null,
    /** Alleen relevant wanneer het archiefendpoint expliciet een token eist. Nooit teruggegeven. */
    val accessToken: String? = null,
)

data class ExternalVerificationFieldErrorsResponse(val fieldErrors: List<String>)

data class ExternalVerificationResponse(
    val id: Long,
    val localIdentifier: String,
    val status: String,
    val externalUri: String,
    val matchedFields: List<String>,
    val checkedAt: Instant,
    val reason: String,
    val licenseStatus: String,
    val licenseValue: String?,
    val licenseCheckedAt: Instant,
)

/**
 * Neemt per verzoek precies één externe verificatie in. Valideert eerst het verzoek (alle
 * veldfouten verzameld, nooit fail-fast) en bevraagt daarna archieven.nl. De respons bevat
 * uitsluitend de minimale verificatievelden: nooit een tokenwaarde of de volledige externe payload.
 */
@RestController
@RequestMapping("/api/external-verification")
class ExternalVerificationController(
    private val validator: ExternalVerificationValidator,
    private val service: ExternalVerificationService,
) {
    @PostMapping
    fun verify(@RequestBody request: ExternalVerificationApiRequest): ResponseEntity<Any> {
        val domain = request.toDomain()
        val result = validator.validate(domain)
        if (result.fieldErrorPaths.isNotEmpty()) {
            return ResponseEntity.badRequest().body(ExternalVerificationFieldErrorsResponse(result.fieldErrorPaths))
        }

        val outcome = service.verify(domain)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ExternalVerificationResponse(
                id = outcome.record.id,
                localIdentifier = outcome.record.localIdentifier,
                status = outcome.record.status,
                externalUri = outcome.record.externalUri,
                matchedFields = outcome.record.matchedFields,
                checkedAt = outcome.record.checkedAt,
                reason = outcome.reason,
                licenseStatus = outcome.record.licenseStatus,
                licenseValue = outcome.record.licenseValue,
                licenseCheckedAt = outcome.record.licenseCheckedAt,
            ),
        )
    }
}

private fun ExternalVerificationApiRequest.toDomain() = ExternalVerificationRequest(
    localIdentifier = localIdentifier,
    name = name,
    birthDate = birthDate,
    deathDate = deathDate,
    adtid = adtid,
    guid = guid,
    accessToken = accessToken,
)
