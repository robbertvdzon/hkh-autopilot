package nl.vdzon.hkh.recordintake

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.KeyPairGenerator
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class RecordIntakeTokenVerifierTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val rsaKey = RSAKey.Builder(keyPair.public as java.security.interfaces.RSAPublicKey)
        .privateKey(keyPair.private)
        .keyID(UUID.randomUUID().toString())
        .build()
    private val verifier = NimbusRecordIntakeTokenVerifier(ImmutableJWKSet(com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK())))

    @Test
    fun `accepts a token that satisfies every fixed requirement`() {
        val identity = verifier.verify(token())

        assertEquals("collection-manager-1", identity.subject)
    }

    @Test
    fun `rejects a token signed by an unknown key`() {
        val otherKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val otherKey = RSAKey.Builder(otherKeyPair.public as java.security.interfaces.RSAPublicKey)
            .privateKey(otherKeyPair.private)
            .keyID(UUID.randomUUID().toString())
            .build()

        assertFailsWith<ResponseStatusException> { verifier.verify(sign(defaultClaims(), otherKey)) }
    }

    @Test
    fun `rejects the wrong issuer`() {
        assertFailsWith<ResponseStatusException> {
            verifier.verify(token { it.issuer("https://not-hkh-autopilot.local") })
        }
    }

    @Test
    fun `rejects the wrong audience`() {
        assertFailsWith<ResponseStatusException> {
            verifier.verify(token { it.audience(listOf("some-other-audience")) })
        }
    }

    @Test
    fun `rejects a missing subject`() {
        assertFailsWith<ResponseStatusException> { verifier.verify(token { it.subject(null) }) }
    }

    @Test
    fun `rejects an expired token`() {
        val now = Instant.now()
        assertFailsWith<ResponseStatusException> {
            verifier.verify(
                token {
                    it.issueTime(Date.from(now.minus(Duration.ofMinutes(30))))
                    it.expirationTime(Date.from(now.minus(Duration.ofMinutes(15))))
                },
            )
        }
    }

    @Test
    fun `rejects a token that exceeds the maximum lifetime`() {
        val now = Instant.now()
        assertFailsWith<ResponseStatusException> {
            verifier.verify(
                token {
                    it.issueTime(Date.from(now))
                    it.expirationTime(Date.from(now.plus(Duration.ofMinutes(16))))
                },
            )
        }
    }

    @Test
    fun `rejects a missing scope`() {
        assertFailsWith<ResponseStatusException> { verifier.verify(token { it.claim("scope", "some:other-scope") }) }
    }

    @Test
    fun `rejects a token without expiry or issued at claims`() {
        assertFailsWith<ResponseStatusException> { verifier.verify(token { it.expirationTime(null) }) }
        assertFailsWith<ResponseStatusException> { verifier.verify(token { it.issueTime(null) }) }
    }

    private fun defaultClaims(customize: (JWTClaimsSet.Builder) -> Unit = {}): JWTClaimsSet {
        val now = Instant.now()
        val builder = JWTClaimsSet.Builder()
            .issuer("https://hkh-autopilot.local")
            .audience("hkh-autopilot-record-intake")
            .subject("collection-manager-1")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(10))))
            .claim("scope", "record:intake")
        customize(builder)
        return builder.build()
    }

    private fun token(customize: (JWTClaimsSet.Builder) -> Unit = {}): String = sign(defaultClaims(customize), rsaKey)

    private fun sign(claims: JWTClaimsSet, key: RSAKey): String {
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
        jwt.sign(RSASSASigner(key))
        return jwt.serialize()
    }
}
