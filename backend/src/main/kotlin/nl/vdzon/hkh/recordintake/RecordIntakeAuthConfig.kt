package nl.vdzon.hkh.recordintake

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class RecordIntakeAuthConfig(
    @param:Value("\${hkh.recordintake.jwks-url:}") val jwksUrl: String,
) {
    val enabled: Boolean = jwksUrl.isNotBlank()
}
