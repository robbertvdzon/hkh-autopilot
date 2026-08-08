package nl.vdzon.hkh.recordintake

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class RecordIntakeValidatorTest {

    private val validator = RecordIntakeValidator()

    @Test
    fun `complete intake is valid and unblocked`() {
        val result = validator.validate(validIntake())

        assertTrue(result.valid)
        assertEquals(emptyList(), result.fieldErrorPaths)
        assertFalse(result.privacyBlocked)
    }

    @ParameterizedTest(name = "missing {0} blocks storage")
    @MethodSource("missingFieldScenarios")
    fun `every mandatory field blocks storage when missing`(
        expectedPath: String,
        mutate: (RecordIntake) -> RecordIntake,
    ) {
        val result = validator.validate(mutate(validIntake()))

        assertTrue(expectedPath in result.fieldErrorPaths)
        assertFalse(result.valid)
    }

    @Test
    fun `blank alternative pair reports both title and description`() {
        val result = validator.validate(validIntake().copy(title = "  ", description = ""))

        assertEquals(
            listOf(RecordIntakeFieldPaths.DESCRIPTION, RecordIntakeFieldPaths.TITLE),
            result.fieldErrorPaths,
        )
    }

    @Test
    fun `one filled alternative between title and description is enough`() {
        val result = validator.validate(validIntake().copy(title = null, description = "Een beschrijving."))

        assertTrue(result.valid)
    }

    @Test
    fun `non absolute access url is rejected`() {
        val result = validator.validate(validIntake().copy(accessUrl = "not-a-url"))

        assertEquals(listOf(RecordIntakeFieldPaths.ACCESS_URL), result.fieldErrorPaths)
    }

    @Test
    fun `unrecognised privacy classification is a plain field error, not a block`() {
        val result = validator.validate(validIntake().copy(privacyClassification = "onbekend"))

        assertEquals(listOf(RecordIntakeFieldPaths.PRIVACY_CLASSIFICATION), result.fieldErrorPaths)
        assertFalse(result.privacyBlocked)
    }

    @Test
    fun `possible personal data is blocked independently of other field errors`() {
        val result = validator.validate(
            validIntake().copy(privacyClassification = "mogelijk persoonsgegevens", localIdentifier = null),
        )

        assertTrue(result.privacyBlocked)
        assertEquals(listOf(RecordIntakeFieldPaths.LOCAL_IDENTIFIER), result.fieldErrorPaths)
        assertFalse(result.valid)
    }

    @Test
    fun `personal data is blocked`() {
        val result = validator.validate(validIntake().copy(privacyClassification = "persoonsgegevens"))

        assertTrue(result.privacyBlocked)
        assertEquals(emptyList(), result.fieldErrorPaths)
    }

    @Test
    fun `no personal data is the only accepted classification`() {
        val result = validator.validate(validIntake().copy(privacyClassification = "Geen Persoonsgegevens"))

        assertFalse(result.privacyBlocked)
    }

    @Test
    fun `external link is only valid when all three fields are valid`() {
        val complete = RecordIntakeExternalLinkInput(
            durableUrl = "https://noord-hollandsarchief.nl/record/1",
            linkRationale = "Zelfde herkomst en datering.",
            uncertainty = "laag",
        )
        assertTrue(validator.isValidExternalLink(complete))
        assertFalse(validator.isValidExternalLink(complete.copy(durableUrl = "not-a-url")))
        assertFalse(validator.isValidExternalLink(complete.copy(linkRationale = " ")))
        assertFalse(validator.isValidExternalLink(complete.copy(uncertainty = "onbekend")))
        assertFalse(validator.isValidExternalLink(null))
    }

    private fun validIntake() = RecordIntake(
        localIdentifier = "HKH-2026-0001",
        title = "Poldermolen De Eendracht",
        description = null,
        dating = "circa 1890",
        provenance = "Streekarchief Waterland",
        rightsStatus = "publicatie toegestaan",
        privacyClassification = "geen persoonsgegevens",
        accessUrl = "https://collectie.hkh-autopilot.local/records/hkh-2026-0001",
    )

    private companion object {
        @JvmStatic
        fun missingFieldScenarios(): List<Arguments> = listOf(
            Arguments.of(
                RecordIntakeFieldPaths.LOCAL_IDENTIFIER,
                { intake: RecordIntake -> intake.copy(localIdentifier = null) },
            ),
            Arguments.of(
                RecordIntakeFieldPaths.DATING,
                { intake: RecordIntake -> intake.copy(dating = "  ") },
            ),
            Arguments.of(
                RecordIntakeFieldPaths.PROVENANCE,
                { intake: RecordIntake -> intake.copy(provenance = null) },
            ),
            Arguments.of(
                RecordIntakeFieldPaths.RIGHTS_STATUS,
                { intake: RecordIntake -> intake.copy(rightsStatus = "") },
            ),
            Arguments.of(
                RecordIntakeFieldPaths.ACCESS_URL,
                { intake: RecordIntake -> intake.copy(accessUrl = null) },
            ),
            Arguments.of(
                RecordIntakeFieldPaths.PRIVACY_CLASSIFICATION,
                { intake: RecordIntake -> intake.copy(privacyClassification = null) },
            ),
        )
    }
}
