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

    @Test
    fun `record with exactly one likely living named person among several is blocked`() {
        val recentBirthYear = java.time.LocalDate.now().minusYears(30).toString()
        val oldDeathDate = java.time.LocalDate.now().minusYears(5).toString()
        val record = GenealogicalRecord(
            deceasedStatus = "overleden",
            namedPersons = listOf(
                NamedPerson(deathDate = oldDeathDate),
                NamedPerson(birthDate = recentBirthYear),
                NamedPerson(deathDate = oldDeathDate),
            ),
        )

        val result = classifier.classify(record)

        assertEquals(PrivacyClassificationStatus.BLOCKED, result.status)
        assertEquals(PrivacyClassificationReasons.NAMED_PERSON_LIKELY_LIVING, result.reason)
    }

    @Test
    fun `record with a named person with an unreadable date field is blocked as fail closed`() {
        val record = GenealogicalRecord(
            deceasedStatus = "overleden",
            namedPersons = listOf(NamedPerson(birthDate = "not-a-date")),
        )

        val result = classifier.classify(record)

        assertEquals(PrivacyClassificationStatus.BLOCKED, result.status)
        assertEquals(PrivacyClassificationReasons.NAMED_PERSON_AGE_UNKNOWN_FAILCLOSED, result.reason)
    }

    @Test
    fun `record where all named persons are deceased and no other signal blocks is processable`() {
        val oldDeathDate = java.time.LocalDate.now().minusYears(5).toString()
        val record = GenealogicalRecord(
            deceasedStatus = "overleden",
            namedPersons = listOf(
                NamedPerson(deathDate = oldDeathDate),
                NamedPerson(burialDate = oldDeathDate),
            ),
        )

        val result = classifier.classify(record)

        assertEquals(PrivacyClassificationStatus.PROCESSABLE, result.status)
        assertTrue(result.processable)
    }

    @Test
    fun `record blocked by GEDCOM RESN at record level overrides an otherwise deceased outcome`() {
        val oldDeathDate = java.time.LocalDate.now().minusYears(5).toString()
        val record = GenealogicalRecord(
            deceasedStatus = "overleden",
            namedPersons = listOf(NamedPerson(deathDate = oldDeathDate)),
            gedcomSource = """
                0 @I1@ INDI
                1 RESN CONFIDENTIAL
            """.trimIndent(),
        )

        val result = classifier.classify(record)

        assertEquals(PrivacyClassificationStatus.BLOCKED, result.status)
        assertEquals(PrivacyClassificationReasons.GEDCOM_RESN_BLOCKED, result.reason)
    }

    @Test
    fun `record blocked by GEDCOM RESN at fact level overrides a likely deceased named person`() {
        val oldDeathDate = java.time.LocalDate.now().minusYears(5).toString()
        val record = GenealogicalRecord(
            deceasedStatus = "overleden",
            namedPersons = listOf(NamedPerson(deathDate = oldDeathDate)),
            gedcomSource = """
                0 @I1@ INDI
                1 BIRT
                2 DATE 1 JAN 1990
                2 RESN LOCKED
            """.trimIndent(),
        )

        val result = classifier.classify(record)

        assertEquals(PrivacyClassificationStatus.BLOCKED, result.status)
        assertEquals(PrivacyClassificationReasons.GEDCOM_RESN_BLOCKED, result.reason)
    }

    @Test
    fun `gedcom source without RESN does not block a deceased record`() {
        val oldDeathDate = java.time.LocalDate.now().minusYears(5).toString()
        val record = GenealogicalRecord(
            deceasedStatus = "overleden",
            namedPersons = listOf(NamedPerson(deathDate = oldDeathDate)),
            gedcomSource = """
                0 @I1@ INDI
                1 NAME Jan /Janssen/
            """.trimIndent(),
        )

        val result = classifier.classify(record)

        assertEquals(PrivacyClassificationStatus.PROCESSABLE, result.status)
        assertTrue(result.processable)
    }

    @Test
    fun `absent gedcom source does not affect an otherwise processable record`() {
        val oldDeathDate = java.time.LocalDate.now().minusYears(5).toString()
        val record = GenealogicalRecord(
            deceasedStatus = "overleden",
            namedPersons = listOf(NamedPerson(deathDate = oldDeathDate)),
            gedcomSource = null,
        )

        val result = classifier.classify(record)

        assertEquals(PrivacyClassificationStatus.PROCESSABLE, result.status)
        assertTrue(result.processable)
    }

    @Test
    fun `syntactically invalid gedcom source blocks a record via fail closed RESN signal`() {
        val record = GenealogicalRecord(
            deceasedStatus = "overleden",
            gedcomSource = "not gedcom at all",
        )

        val result = classifier.classify(record)

        assertEquals(PrivacyClassificationStatus.BLOCKED, result.status)
        assertEquals(PrivacyClassificationReasons.GEDCOM_RESN_BLOCKED, result.reason)
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
