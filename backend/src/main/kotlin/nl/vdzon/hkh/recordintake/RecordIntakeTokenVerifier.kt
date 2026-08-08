package nl.vdzon.hkh.recordintake

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.net.URI
import java.time.Duration
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Alleen het subject; er wordt nooit een tokenwaarde, claim of header bewaard, gelogd of teruggegeven. */
data class RecordIntakeTokenIdentity(val subject: String)

fun interface RecordIntakeTokenVerifier {
    fun verify(token: String): RecordIntakeTokenIdentity
}

/**
 * RS256-tokenverificatie naar het patroon van `NimbusGoogleIdTokenVerifier`, maar met vaste,
 * versieerbare configuratie voor deze module: issuer, audience, vereiste scope en een maximale
 * levensduur van vijftien minuten. Elke afwijking is fail-closed: een ontbrekende, verlopen,
 * verkeerd ondertekende of anderszins ongeldige claim geeft HTTP 401 zonder tokenwaarde of claims
 * in de foutmelding.
 */
class NimbusRecordIntakeTokenVerifier(
    jwkSource: JWKSource<SecurityContext>,
) : RecordIntakeTokenVerifier {
    constructor(jwksUrl: String) : this(
        JWKSourceBuilder.create<SecurityContext>(URI.create(jwksUrl).toURL()).build(),
    )

    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
    }

    override fun verify(token: String): RecordIntakeTokenIdentity {
        val claims: JWTClaimsSet = try {
            processor.process(token, null)
        } catch (_: Exception) {
            throw unauthorized()
        }
        if (claims.issuer != ISSUER) throw unauthorized()
        if (AUDIENCE !in claims.audience.orEmpty()) throw unauthorized()

        val subject = claims.subject?.trim().orEmpty()
        if (subject.isBlank()) throw unauthorized()

        val issuedAt = claims.issueTime?.toInstant() ?: throw unauthorized()
        val expiry = claims.expirationTime?.toInstant() ?: throw unauthorized()
        val now = Instant.now()
        if (!expiry.isAfter(issuedAt)) throw unauthorized()
        if (expiry.isBefore(now)) throw unauthorized()
        if (Duration.between(issuedAt, expiry) > MAX_LIFETIME) throw unauthorized()

        val scopes = claims.getStringClaim("scope").orEmpty().split(' ').filter { it.isNotBlank() }
        if (REQUIRED_SCOPE !in scopes) throw unauthorized()

        return RecordIntakeTokenIdentity(subject)
    }

    private fun unauthorized() = ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid record intake token")

    companion object {
        const val ISSUER = "https://hkh-autopilot.local"
        const val AUDIENCE = "hkh-autopilot-record-intake"
        const val REQUIRED_SCOPE = "record:intake"
        val MAX_LIFETIME: Duration = Duration.ofMinutes(15)
    }
}
