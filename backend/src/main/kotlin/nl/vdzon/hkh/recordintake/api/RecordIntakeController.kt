package nl.vdzon.hkh.recordintake.api

import java.time.Instant
import nl.vdzon.hkh.recordintake.PRIVACY_CLASSIFICATION_BLOCKED_CODE
import nl.vdzon.hkh.recordintake.RecordIntake
import nl.vdzon.hkh.recordintake.RecordIntakeExternalArchiveOutcome
import nl.vdzon.hkh.recordintake.RecordIntakeExternalLinkInput
import nl.vdzon.hkh.recordintake.RecordIntakeService
import nl.vdzon.hkh.recordintake.RecordIntakeTokenVerifier
import nl.vdzon.hkh.recordintake.RecordIntakeValidator
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class RecordIntakeExternalLinkRequest(
    val durableUrl: String? = null,
    val linkRationale: String? = null,
    val uncertainty: String? = null,
)

data class RecordIntakeRequest(
    val localIdentifier: String? = null,
    val title: String? = null,
    val description: String? = null,
    val dating: String? = null,
    val provenance: String? = null,
    val rightsStatus: String? = null,
    val privacyClassification: String? = null,
    val accessUrl: String? = null,
    val externalLink: RecordIntakeExternalLinkRequest? = null,
    val deceasedStatus: String? = null,
    val nextOfKinConfirmed: Boolean? = null,
    val confirmExternalArchiveData: Boolean? = null,
)

data class RecordIntakeFieldErrorsResponse(val fieldErrors: List<String>)

data class RecordIntakeRejectionResponse(val errorCode: String)

data class RecordIntakeExternalLinkResponse(val id: Long, val status: String)

data class RecordIntakeExternalArchiveDataResponse(
    val stored: Boolean,
    val reason: String,
    val license: String?,
    val sourceUri: String?,
    val fetchedAt: Instant?,
)

data class RecordIntakeResponse(
    val id: Long,
    val status: String,
    val createdAt: Instant,
    val externalLink: RecordIntakeExternalLinkResponse?,
    val externalArchiveData: RecordIntakeExternalArchiveDataResponse?,
)

/**
 * Neemt per verzoek precies één record-intake in. Leest en verifieert eerst het
 * `Authorization: Bearer`-token; pas daarna wordt het record gevalideerd en, indien geldig,
 * opgeslagen als intern concept. De respons bevat uitsluitend metadata: nooit een tokenwaarde,
 * header, claim, publicatie-/objectmediaveld of de opgehaalde externe naam-/datumwaarden zelf -
 * alleen of ze opgeslagen zijn en waarom (niet).
 */
@RestController
@RequestMapping("/api/record-intake")
class RecordIntakeController(
    private val tokenVerifier: RecordIntakeTokenVerifier,
    private val validator: RecordIntakeValidator,
    private val service: RecordIntakeService,
) {
    @PostMapping
    fun intake(
        @RequestBody request: RecordIntakeRequest,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): ResponseEntity<Any> {
        tokenVerifier.verify(bearerToken(authorization))

        val intake = request.toDomain()
        val result = validator.validate(intake)
        if (result.fieldErrorPaths.isNotEmpty()) {
            return ResponseEntity.badRequest().body(RecordIntakeFieldErrorsResponse(result.fieldErrorPaths))
        }
        if (result.privacyBlocked) {
            return ResponseEntity.unprocessableEntity()
                .body(RecordIntakeRejectionResponse(PRIVACY_CLASSIFICATION_BLOCKED_CODE))
        }

        val created = service.create(intake)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            RecordIntakeResponse(
                id = created.record.id,
                status = created.record.status,
                createdAt = created.record.createdAt,
                externalLink = created.externalLink?.let { RecordIntakeExternalLinkResponse(it.id, it.status) },
                externalArchiveData = created.externalArchiveOutcome?.toResponse(),
            ),
        )
    }

    private fun bearerToken(authorization: String?): String = authorization
        ?.takeIf { it.startsWith(BEARER_PREFIX) }
        ?.removePrefix(BEARER_PREFIX)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "A record intake access token is required")

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}

private fun RecordIntakeExternalArchiveOutcome.toResponse() = RecordIntakeExternalArchiveDataResponse(
    stored = stored,
    reason = reason,
    license = license,
    sourceUri = sourceUri,
    fetchedAt = fetchedAt,
)

private fun RecordIntakeRequest.toDomain() = RecordIntake(
    localIdentifier = localIdentifier,
    title = title,
    description = description,
    dating = dating,
    provenance = provenance,
    rightsStatus = rightsStatus,
    privacyClassification = privacyClassification,
    accessUrl = accessUrl,
    externalLink = externalLink?.let {
        RecordIntakeExternalLinkInput(
            durableUrl = it.durableUrl,
            linkRationale = it.linkRationale,
            uncertainty = it.uncertainty,
        )
    },
    deceasedStatus = deceasedStatus,
    nextOfKinConfirmed = nextOfKinConfirmed,
    confirmExternalArchiveData = confirmExternalArchiveData,
)
