package nl.vdzon.hkh.privacyclassification

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PrivacyClassifierTest {

    private val classifier = PrivacyClassifier()

    @Test
    fun `deceased record without next of kin fields is processable`() {
        val result = classifier.classify(GenealogicalRecord(deceasedStatus = "overleden"))

        assertEquals(PrivacyClassificationStatus.PROCESSABLE, result.status)
        assertTrue(result.processable)
        assertTrue(result.reason.isNotBlank())
    }

    @ParameterizedTest(name = "deceased record with {0} set is blocked")
    @MethodSource("livingNextOfKinScenarios")
    fun `deceased record with a living next of kin field is blocked`(
        fieldName: String,
        nextOfKin: LivingNextOfKinFields,
    ) {
        val result = classifier.classify(
            GenealogicalRecord(deceasedStatus = "overleden", nextOfKin = nextOfKin),
        )

        assertEquals(PrivacyClassificationStatus.BLOCKED, result.status)
        assertFalse(result.processable)
        assertEquals("Bevat gegevens van levende nabestaande", result.reason)
    }

    @ParameterizedTest(name = "deceased status {0} is blocked")
    @MethodSource("nonDeceasedStatusScenarios")
    fun `records without a confirmed deceased status are blocked`(rawStatus: String?) {
        val result = classifier.classify(GenealogicalRecord(deceasedStatus = rawStatus))

        assertEquals(PrivacyClassificationStatus.BLOCKED, result.status)
        assertFalse(result.processable)
        assertTrue(result.reason.isNotBlank())
    }

    @Test
    fun `unrecognized deceased status value is blocked as fail closed default`() {
        val result = classifier.classify(GenealogicalRecord(deceasedStatus = "vermist"))

        assertEquals(PrivacyClassificationStatus.BLOCKED, result.status)
        assertTrue(result.reason.isNotBlank())
    }

    companion object {
        @JvmStatic
        fun livingNextOfKinScenarios() = listOf(
            Arguments.of("contactName", LivingNextOfKinFields(contactName = "Jan Janssen")),
            Arguments.of("contactAddress", LivingNextOfKinFields(contactAddress = "Kerkstraat 1")),
            Arguments.of("contactPhoneNumber", LivingNextOfKinFields(contactPhoneNumber = "0612345678")),
        )

        @JvmStatic
        fun nonDeceasedStatusScenarios() = listOf(
            Arguments.of(null),
            Arguments.of(""),
            Arguments.of("onbekend"),
            Arguments.of("levend"),
        )
    }
}
