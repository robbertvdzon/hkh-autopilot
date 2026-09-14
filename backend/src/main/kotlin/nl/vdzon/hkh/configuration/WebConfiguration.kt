package nl.vdzon.hkh.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfiguration(
    @param:Value("\${hkh.cors-allowed-origin-patterns}") private val allowedOriginPatterns: String,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            // `CorsRegistry.addMapping` start met de permit-all standaard `allowedOrigins = ["*"]`.
            // Spring wist die standaard alleen wanneer er daadwerkelijk een patroon wordt
            // toegevoegd; bij een lege patroonlijst zou hij blijven staan en juist alle
            // cross-origin toegang toelaten. Expliciet leegmaken houdt het gedrag fail-closed.
            .allowedOrigins()
            .allowedOriginPatterns(*parseAllowedOriginPatterns(allowedOriginPatterns).toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
    }

    companion object {
        /**
         * Lege elementen tellen niet mee als patroon: een lege of alleen uit scheidingstekens
         * bestaande configuratie leverde anders het patroon `""` op, dat op geen enkele herkomst
         * matcht maar wel elk verzoek met een `Origin`-header met 403 afwees. Zonder patronen
         * blijft het gedrag fail-closed: cross-origin toegang wordt dan geweigerd.
         */
        fun parseAllowedOriginPatterns(configured: String): List<String> =
            configured.split(',').map(String::trim).filter(String::isNotEmpty)
    }
}
