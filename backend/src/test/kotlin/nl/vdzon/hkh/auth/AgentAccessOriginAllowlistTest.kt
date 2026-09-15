package nl.vdzon.hkh.auth

import java.net.URI
import java.util.function.Supplier
import kotlin.test.assertEquals
import nl.vdzon.hkh.configuration.SameOriginRequestFilter
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

/**
 * Bewaakt de wisselwerking tussen `SameOriginRequestFilter` en de herkomst-allowlist van de
 * agenttoegang. De filter verbergt de `Origin`-header van same-origin verzoeken voor de volledige
 * filterketen; zonder het bewaarde requestattribuut zou [AgentAccessVerifier] hier altijd `null`
 * zien en zijn allowlist overslaan. De aanmeldpagina wordt same-origin geserveerd, dus dat is
 * precies het normale pad.
 */
class AgentAccessOriginAllowlistTest {
    private val token = "a".repeat(40)
    private val email = "tester@example.invalid"
    private val appHost = "hkh-autopilot-acceptance.vdzonsoftware.nl"
    private val appOrigin = "https://$appHost"
    private val adminOrigin = "https://hkh-autopilot-admin-acceptance.vdzonsoftware.nl"

    @Test
    fun `the filter and the controller name the same request attribute`() {
        assertEquals(SameOriginRequestFilter.ORIGINAL_ORIGIN_ATTRIBUTE, HIDDEN_ORIGIN_ATTRIBUTE)
    }

    @Test
    fun `a same origin login from an unconfigured origin is still rejected`() {
        mockMvc(allowedOrigins = adminOrigin)
            .perform(login(origin = appOrigin))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a same origin login from a configured origin still succeeds`() {
        mockMvc(allowedOrigins = "$appOrigin,$adminOrigin")
            .perform(login(origin = appOrigin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
    }

    @Test
    fun `an empty allowlist keeps rejecting a same origin browser login`() {
        mockMvc(allowedOrigins = "")
            .perform(login(origin = appOrigin))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a cross origin login keeps being checked against the allowlist`() {
        mockMvc(allowedOrigins = appOrigin)
            .perform(login(origin = "https://aanvaller.example.com"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a login without an origin header keeps its existing behaviour`() {
        mockMvc(allowedOrigins = appOrigin)
            .perform(login(origin = null))
            .andExpect(status().isOk)
    }

    private fun login(origin: String?) =
        post(URI.create("$appOrigin/api/auth/agent-session"))
            .header("X-AI-Access-Token", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email"}""")
            .apply { origin?.let { header(HttpHeaders.ORIGIN, it) } }

    private fun mockMvc(allowedOrigins: String): MockMvc {
        val context = GenericWebApplicationContext(MockServletContext())
        AnnotatedBeanDefinitionReader(context).register(AgentAccessTestConfiguration::class.java)
        context.registerBean(
            "agentAccessController",
            AgentAccessController::class.java,
            Supplier {
                AgentAccessController(
                    AgentAccessVerifier(token, email, allowedOrigins),
                    AgentAdminSessions(AdminAuthConfig("test-client", email)),
                )
            },
        )
        context.refresh()
        return MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(SameOriginRequestFilter())
            .build()
    }

    @Configuration
    @EnableWebMvc
    class AgentAccessTestConfiguration
}
