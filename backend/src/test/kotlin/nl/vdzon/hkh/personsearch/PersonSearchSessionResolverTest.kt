package nl.vdzon.hkh.personsearch

import jakarta.servlet.http.Cookie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class PersonSearchSessionResolverTest {

    @Test
    fun `issues a new non-blank session cookie when none exists yet`() {
        val resolver = PersonSearchSessionResolver()
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val sessionId = resolver.resolve(request, response)

        assertTrue(sessionId.isNotBlank())
        val setCookieHeader = response.getHeader("Set-Cookie")
        assertTrue(setCookieHeader != null && setCookieHeader.contains(PERSON_SEARCH_SESSION_COOKIE_NAME))
        assertTrue(setCookieHeader!!.contains("HttpOnly"))
    }

    @Test
    fun `reuses an existing session cookie instead of issuing a new one`() {
        val resolver = PersonSearchSessionResolver()
        val request = MockHttpServletRequest()
        request.setCookies(Cookie(PERSON_SEARCH_SESSION_COOKIE_NAME, "existing-session-id"))
        val response = MockHttpServletResponse()

        val sessionId = resolver.resolve(request, response)

        assertEquals("existing-session-id", sessionId)
        assertEquals(null, response.getHeader("Set-Cookie"))
    }

    @Test
    fun `two resolutions without a cookie yield two different unguessable session ids`() {
        val resolver = PersonSearchSessionResolver()

        val first = resolver.resolve(MockHttpServletRequest(), MockHttpServletResponse())
        val second = resolver.resolve(MockHttpServletRequest(), MockHttpServletResponse())

        assertNotEquals(first, second)
    }
}
