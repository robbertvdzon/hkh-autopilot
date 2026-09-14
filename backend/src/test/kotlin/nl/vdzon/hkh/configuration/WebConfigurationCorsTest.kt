package nl.vdzon.hkh.configuration

import java.net.URI
import java.util.function.Supplier
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

/**
 * Bewijst het gedeelde storingspad uit hkh-208: een same-origin POST vanuit de browser stuurt een
 * `Origin`-header mee en werd daardoor tegen de CORS-patronen getoetst. Met een afwijkende of lege
 * patroonlijst - precies de situatie op preview en acceptatie - werd elke `/api/`-POST met 403
 * `Invalid CORS request` geweigerd, waardoor zowel de Europeana- als de Wikidata-route in de
 * browser structureel BRONUITVAL toonde. Curl liet dat niet zien, omdat curl standaard geen
 * `Origin`-header stuurt.
 */
class WebConfigurationCorsTest {
    private val appUrl = "https://hkh-autopilot-acceptance.vdzonsoftware.nl/api/topic-search"
    private val appOrigin = "https://hkh-autopilot-acceptance.vdzonsoftware.nl"

    @Test
    fun `a same origin browser post succeeds even without configured origin patterns`() {
        mockMvc(configuredPatterns = "")
            .perform(post(URI.create(appUrl)).header(HttpHeaders.ORIGIN, appOrigin))
            .andExpect(status().isOk)
    }

    @Test
    fun `a same origin browser post succeeds when only other origin patterns are configured`() {
        mockMvc(configuredPatterns = "http://localhost:*")
            .perform(post(URI.create(appUrl)).header(HttpHeaders.ORIGIN, appOrigin))
            .andExpect(status().isOk)
    }

    @Test
    fun `a post without an origin header keeps succeeding`() {
        mockMvc(configuredPatterns = "")
            .perform(post(URI.create(appUrl)))
            .andExpect(status().isOk)
    }

    @Test
    fun `an unconfigured cross origin post stays rejected`() {
        mockMvc(configuredPatterns = "")
            .perform(post(URI.create(appUrl)).header(HttpHeaders.ORIGIN, "https://aanvaller.example.com"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `an unconfigured cross origin preflight stays rejected`() {
        mockMvc(configuredPatterns = "")
            .perform(
                options(URI.create(appUrl))
                    .header(HttpHeaders.ORIGIN, "https://aanvaller.example.com")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"),
            )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a configured cross origin post is still answered with the allow origin header`() {
        mockMvc(configuredPatterns = "http://localhost:*")
            .perform(
                post(URI.create("http://localhost:8080/api/topic-search"))
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000"),
            )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
    }

    @Test
    fun `blank entries never become an origin pattern`() {
        assertEquals(emptyList(), WebConfiguration.parseAllowedOriginPatterns(""))
        assertEquals(emptyList(), WebConfiguration.parseAllowedOriginPatterns("   "))
        assertEquals(emptyList(), WebConfiguration.parseAllowedOriginPatterns(","))
        assertEquals(
            listOf("https://een.example.com", "https://twee.example.com"),
            WebConfiguration.parseAllowedOriginPatterns(" https://een.example.com , , https://twee.example.com "),
        )
    }

    private fun mockMvc(configuredPatterns: String): MockMvc {
        val context = GenericWebApplicationContext(MockServletContext())
        AnnotatedBeanDefinitionReader(context).register(CorsTestConfiguration::class.java)
        context.registerBean(
            "webConfiguration",
            WebConfiguration::class.java,
            Supplier { WebConfiguration(configuredPatterns) },
        )
        context.refresh()
        return MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(SameOriginRequestFilter())
            .build()
    }

    @Configuration
    @EnableWebMvc
    class CorsTestConfiguration {
        @Bean
        fun corsTestController(): CorsTestController = CorsTestController()
    }

    @RestController
    class CorsTestController {
        @PostMapping("/api/topic-search")
        fun search(): Map<String, String> = mapOf("status" to "READY")
    }
}
