package nl.vdzon.hkh.externalverification

import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class ExternalVerificationTokenCipherTest {

    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun `encrypts and decrypts back to the original token`() {
        val cipher = ExternalVerificationTokenCipher(key)

        val encrypted = cipher.encrypt("s3cr3t-archive-token")

        assertFalse(encrypted.contains("s3cr3t-archive-token"))
        assertEquals("s3cr3t-archive-token", cipher.decrypt(encrypted))
    }

    @Test
    fun `two encryptions of the same token never produce the same ciphertext`() {
        val cipher = ExternalVerificationTokenCipher(key)

        val first = cipher.encrypt("s3cr3t-archive-token")
        val second = cipher.encrypt("s3cr3t-archive-token")

        assertFalse(first == second)
    }

    @Test
    fun `fails closed when no key is configured`() {
        val cipher = ExternalVerificationTokenCipher("")

        assertFailsWith<IllegalStateException> { cipher.encrypt("s3cr3t-archive-token") }
    }
}
