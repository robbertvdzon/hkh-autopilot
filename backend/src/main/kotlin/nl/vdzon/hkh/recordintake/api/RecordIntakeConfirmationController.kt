package nl.vdzon.hkh.recordintake.api

import java.time.Clock
import java.time.Instant
import nl.vdzon.hkh.auth.AdminAuthenticator
import nl.vdzon.hkh.auth.PreviewRuntimeConfig
import nl.vdzon.hkh.recordintake.RecordIntakeStore
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class RecordIntakeConfirmationResponse(
    val localIdentifier: String,
    val confirmedBy: String,
    val confirmedAt: Instant,
)

/**
 * Admin-only bevestigingsactie, uitbreiding op het bestaande admin-pad (dezelfde
 * tokenverificatie-conventie als de rest van de beheerfrontend via [AdminAuthenticator]). Zet
 * `confirmedBy`/`confirmedAt` op het meest recente record voor deze `localIdentifier`; het vullen
 * van de `archive*`-velden alleen is hiervoor niet voldoende - dit is een aparte, bewuste stap.
 */
@RestController
@RequestMapping("/api/admin/record-intake")
class RecordIntakeConfirmationController(
    private val store: RecordIntakeStore,
    private val authenticator: AdminAuthenticator,
    private val clock: Clock = Clock.systemUTC(),
) {
    @PostMapping("/{localIdentifier}/confirm")
    fun confirm(
        @PathVariable localIdentifier: String,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestHeader(PreviewRuntimeConfig.ADMIN_HEADER, required = false) previewHeader: String?,
    ): RecordIntakeConfirmationResponse {
        val admin = authenticator.authenticate(authorization, previewHeader)
        val confirmedAt = Instant.now(clock)
        val record = store.confirm(localIdentifier, admin.email, confirmedAt)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown local identifier")
        return RecordIntakeConfirmationResponse(
            localIdentifier = record.localIdentifier,
            confirmedBy = record.confirmedBy ?: admin.email,
            confirmedAt = record.confirmedAt ?: confirmedAt,
        )
    }
}
