package nl.vdzon.hkh.recordintake

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@Configuration
class RecordIntakeAuthConfiguration {
    @Bean
    fun recordIntakeTokenVerifier(config: RecordIntakeAuthConfig): RecordIntakeTokenVerifier =
        if (config.enabled) {
            NimbusRecordIntakeTokenVerifier(config.jwksUrl)
        } else {
            RecordIntakeTokenVerifier {
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Record intake is not configured")
            }
        }
}
