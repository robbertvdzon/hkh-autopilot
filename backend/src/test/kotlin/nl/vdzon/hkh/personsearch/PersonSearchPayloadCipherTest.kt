package nl.vdzon.hkh.personsearch

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PersonSearchPayloadCipherTest {

    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun `encrypts and decrypts back to the original plain text`() {
        val cipher = PersonSearchPayloadCipher(key)

        val encrypted = cipher.encrypt("Wie was Nicolaas Jacobus Sinnige?")

        assertFalse(encrypted.contains("Nicolaas"))
        assertEquals("Wie was Nicolaas Jacobus Sinnige?", cipher.decrypt(encrypted))
    }

    @Test
    fun `encrypts and decrypts a stored payload with an answer`() {
        val cipher = PersonSearchPayloadCipher(key)
        val payload = PersonSearchStoredPayload(
            answer = PersonSearchAnswer(
                sentences = listOf(PersonSearchAnswerSentence("Nicolaas is geboren.", listOf(1))),
                sources = emptyList(),
                connections = emptyList(),
                disclaimer = "Geen volledig levensverhaal.",
            ),
            context = PersonSearchWikidataContext("Heemskerk", "gemeente"),
        )

        val encrypted = cipher.encryptPayload(payload)

        assertFalse(encrypted.contains("Nicolaas"))
        assertEquals(payload, cipher.decryptPayload(encrypted))
    }

    @Test
    fun `fails closed when no key is configured`() {
        val cipher = PersonSearchPayloadCipher("")

        assertFailsWith<IllegalStateException> { cipher.encrypt("Wie was Jansen?") }
        assertFailsWith<IllegalStateException> {
            cipher.encryptPayload(PersonSearchStoredPayload(refinementMessage = "Verfijn de vraag."))
        }
    }
}
