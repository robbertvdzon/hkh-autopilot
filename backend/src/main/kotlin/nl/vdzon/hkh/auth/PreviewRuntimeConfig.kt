package nl.vdzon.hkh.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Fail-closed boundary for disposable PR environments. */
@Component
class PreviewRuntimeConfig(
    @param:Value("\${hkh.preview.enabled:false}") val enabled: Boolean,
    @param:Value("\${hkh.preview.marker:}") val marker: String,
    @param:Value("\${HKH_DATABASE_URL:}") databaseUrl: String,
) {
    init {
        if (enabled) {
            require(marker == REQUIRED_MARKER) { "Preview mode requires the expected preview marker" }
            require(PREVIEW_DATABASE.matches(databaseUrl)) {
                "Preview mode may only use the in-namespace preview database"
            }
        } else {
            require(marker.isBlank()) { "The preview marker may not be set outside preview mode" }
        }
    }

    fun accepts(header: String?): Boolean = enabled && header == ADMIN_HEADER_VALUE

    companion object {
        const val REQUIRED_MARKER = "hkh-autopilot-pr-preview"
        const val ADMIN_HEADER = "X-HKH-Preview-Admin"
        const val ADMIN_HEADER_VALUE = "enabled"
        const val ADMIN_EMAIL = "preview-admin@hkh-autopilot.invalid"

        private val PREVIEW_DATABASE =
            Regex("^jdbc:postgresql://database(?::5432)?/hkh(?:\\?.*)?$")
    }
}
