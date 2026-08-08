package nl.vdzon.hkh.externalverification

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Versleutelt en ontsleutelt een optioneel archiefendpoint-toegangstoken met AES-256-GCM. Het
 * toegangstoken is alleen relevant wanneer het archiefendpoint dat expliciet eist (vandaag niet het
 * geval); het wordt nooit in leesbare vorm opgeslagen, gelogd of teruggegeven. De sleutel komt uit
 * configuratie (`hkh.externalverification.token-key`, env `HKH_EXTERNAL_VERIFICATION_TOKEN_KEY`),
 * naar het patroon van de bestaande `secrets.env`-aanpak; zonder geconfigureerde sleutel faalt
 * versleuteling fail-closed.
 */
@Component
class ExternalVerificationTokenCipher(
    @param:Value("\${hkh.externalverification.token-key:}") private val base64Key: String,
) {
    private val secureRandom = SecureRandom()

    /** Retourneert `Base64(iv + ciphertext)`. Werpt fail-closed wanneer geen sleutel is geconfigureerd. */
    fun encrypt(plainToken: String): String {
        val key = secretKey()
        val iv = ByteArray(IV_LENGTH_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plainToken.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    /** Ontsleutelt een eerder met [encrypt] geproduceerde waarde. Nooit gebruikt voor logging of respons. */
    fun decrypt(encoded: String): String {
        val key = secretKey()
        val raw = Base64.getDecoder().decode(encoded)
        val iv = raw.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = raw.copyOfRange(IV_LENGTH_BYTES, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKeySpec {
        check(base64Key.isNotBlank()) { "No external verification token key is configured" }
        val keyBytes = Base64.getDecoder().decode(base64Key)
        return SecretKeySpec(keyBytes, "AES")
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
