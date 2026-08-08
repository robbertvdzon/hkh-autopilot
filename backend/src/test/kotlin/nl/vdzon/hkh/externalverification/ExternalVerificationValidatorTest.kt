package nl.vdzon.hkh.externalverification

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ExternalVerificationValidatorTest {

    private val validator = ExternalVerificationValidator()

    @Test
    fun `complete request is valid`() {
        val result = validator.validate(validRequest())

        assertTrue(result.valid)
        assertEquals(emptyList(), result.fieldErrorPaths)
    }

    @ParameterizedTest(name = "missing {0} is reported")
    @MethodSource("missingFieldScenarios")
    fun `every mandatory field is reported when missing`(
        expectedPath: String,
        mutate: (ExternalVerificationRequest) -> ExternalVerificationRequest,
    ) {
        val result = validator.validate(mutate(validRequest()))

        assertTrue(expectedPath in result.fieldErrorPaths)
    }

    @Test
    fun `an entirely empty request reports every field`() {
        val result = validator.validate(ExternalVerificationRequest())

        assertEquals(
            listOf(
                ExternalVerificationFieldPaths.ADTID,
                ExternalVerificationFieldPaths.BIRTH_DATE,
                ExternalVerificationFieldPaths.DEATH_DATE,
                ExternalVerificationFieldPaths.GUID,
                ExternalVerificationFieldPaths.LOCAL_IDENTIFIER,
                ExternalVerificationFieldPaths.NAME,
            ),
            result.fieldErrorPaths,
        )
    }

    private fun validRequest() = ExternalVerificationRequest(
        localIdentifier = "HKH-2026-0010",
        name = "Jan Jansen",
        birthDate = "1900-01-01",
        deathDate = "1980-05-05",
        adtid = "1234",
        guid = "abcd-ef01",
    )

    private companion object {
        @JvmStatic
        fun missingFieldScenarios(): List<Arguments> = listOf(
            Arguments.of(
                ExternalVerificationFieldPaths.LOCAL_IDENTIFIER,
                { request: ExternalVerificationRequest -> request.copy(localIdentifier = null) },
            ),
            Arguments.of(
                ExternalVerificationFieldPaths.NAME,
                { request: ExternalVerificationRequest -> request.copy(name = "  ") },
            ),
            Arguments.of(
                ExternalVerificationFieldPaths.BIRTH_DATE,
                { request: ExternalVerificationRequest -> request.copy(birthDate = null) },
            ),
            Arguments.of(
                ExternalVerificationFieldPaths.DEATH_DATE,
                { request: ExternalVerificationRequest -> request.copy(deathDate = "") },
            ),
            Arguments.of(
                ExternalVerificationFieldPaths.ADTID,
                { request: ExternalVerificationRequest -> request.copy(adtid = null) },
            ),
            Arguments.of(
                ExternalVerificationFieldPaths.GUID,
                { request: ExternalVerificationRequest -> request.copy(guid = null) },
            ),
        )
    }
}
