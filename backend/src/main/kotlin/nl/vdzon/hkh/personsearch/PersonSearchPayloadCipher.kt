package nl.vdzon.hkh.personsearch

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/** Antwoordbeweringen en bronrecords die tijdelijk (versleuteld) bij een job horen. */
data class PersonSearchStoredPayload(
    val refinementMessage: String? = null,
    val answer: PersonSearchAnswer? = null,
    val context: PersonSearchWikidataContext? = null,
)

fun PersonSearchOutcome.toStoredPayload(): PersonSearchStoredPayload = when (this) {
    is PersonSearchOutcome.SupportedAnswer -> PersonSearchStoredPayload(answer = answer, context = context)
    is PersonSearchOutcome.NoResults -> PersonSearchStoredPayload(context = context)
    is PersonSearchOutcome.Partial -> PersonSearchStoredPayload(refinementMessage = refinementMessage, context = context)
    is PersonSearchOutcome.SourceOutage -> PersonSearchStoredPayload(context = context)
}

private val payloadObjectMapper: JsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()

/**
 * Versleutelt en ontsleutelt de tijdelijk bewaarde jobgegevens (oorspronkelijke vraag, gevalideerde
 * bronrecords, antwoordbeweringen) met AES-256-GCM, naar het patroon van de bestaande
 * `ExternalVerificationTokenCipher`. Eigen `@Component` binnen deze module (die geen
 * `allowedDependencies` op andere modules heeft) in plaats van hergebruik van de
 * externalverification-cipher. De sleutel komt uit configuratie
 * (`hkh.personsearch.payload-key`, env `HKH_PERSON_SEARCH_PAYLOAD_KEY`); zonder geconfigureerde
 * sleutel faalt versleuteling fail-closed.
 */
@Component
class PersonSearchPayloadCipher(
    @param:Value("\${hkh.personsearch.payload-key:}") private val base64Key: String,
) {
    private val secureRandom = SecureRandom()

    /** Retourneert `Base64(iv + ciphertext)`. Werpt fail-closed wanneer geen sleutel is geconfigureerd. */
    fun encrypt(plainText: String): String {
        val key = secretKey()
        val iv = ByteArray(IV_LENGTH_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    /** Ontsleutelt een eerder met [encrypt] geproduceerde waarde. */
    fun decrypt(encoded: String): String {
        val key = secretKey()
        val raw = Base64.getDecoder().decode(encoded)
        val iv = raw.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = raw.copyOfRange(IV_LENGTH_BYTES, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun encryptPayload(payload: PersonSearchStoredPayload): String = encrypt(payloadObjectMapper.writeValueAsString(payload))

    fun decryptPayload(encoded: String): PersonSearchStoredPayload =
        payloadObjectMapper.readValue(decrypt(encoded), PersonSearchStoredPayload::class.java)

    private fun secretKey(): SecretKeySpec {
        check(base64Key.isNotBlank()) { "No person search payload key is configured" }
        val keyBytes = Base64.getDecoder().decode(base64Key)
        return SecretKeySpec(keyBytes, "AES")
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
