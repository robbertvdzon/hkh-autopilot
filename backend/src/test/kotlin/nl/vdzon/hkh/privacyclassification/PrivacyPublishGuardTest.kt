package nl.vdzon.hkh.privacyclassification

import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class PrivacyPublishGuardTest {

    @ParameterizedTest
    @EnumSource(PrivacyClassificationStatus::class, names = ["BLOCKED"])
    fun `publication is refused for every blocked record`(status: PrivacyClassificationStatus) {
        val result = PrivacyClassificationResult(status = status, reason = "Bevat gegevens van levende nabestaande")

        assertFailsWith<PrivacyPublishBlockedException> {
            PrivacyPublishGuard.assertPublishable(result)
        }
    }

    @ParameterizedTest
    @EnumSource(PrivacyClassificationStatus::class, names = ["PROCESSABLE"])
    fun `publication is allowed for every processable record`(status: PrivacyClassificationStatus) {
        val result = PrivacyClassificationResult(status = status, reason = "Persoon is overleden.")

        PrivacyPublishGuard.assertPublishable(result)
    }
}
