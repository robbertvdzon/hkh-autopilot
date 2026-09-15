package nl.vdzon.hkh.configuration

import jakarta.servlet.http.HttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.cors.CorsUtils

class SameOriginRequestFilterTest {
    @Test
    fun `a same origin browser post is no longer treated as a cors request`() {
        val request = requestTo(host = "hkh-autopilot-acceptance.vdzonsoftware.nl", port = 80)
        request.addHeader(HttpHeaders.ORIGIN, "https://hkh-autopilot-acceptance.vdzonsoftware.nl")

        val forwarded = filter(request)

        assertNull(forwarded.getHeader(HttpHeaders.ORIGIN))
        assertFalse(forwarded.getHeaders(HttpHeaders.ORIGIN).hasMoreElements())
        assertFalse(forwarded.headerNames.toList().any { it.equals(HttpHeaders.ORIGIN, ignoreCase = true) })
        assertFalse(CorsUtils.isCorsRequest(forwarded))
    }

    @Test
    fun `other headers of a same origin request stay untouched`() {
        val request = requestTo(host = "hkh-autopilot-acceptance.vdzonsoftware.nl", port = 80)
        request.addHeader(HttpHeaders.ORIGIN, "https://hkh-autopilot-acceptance.vdzonsoftware.nl")
        request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json")

        val forwarded = filter(request)

        assertEquals("application/json", forwarded.getHeader(HttpHeaders.CONTENT_TYPE))
        assertTrue(forwarded.headerNames.toList().any { it.equals(HttpHeaders.CONTENT_TYPE, ignoreCase = true) })
    }

    @Test
    fun `a cross origin request keeps its origin header so the cors patterns still decide`() {
        val request = requestTo(host = "hkh-autopilot-acceptance.vdzonsoftware.nl", port = 80)
        request.addHeader(HttpHeaders.ORIGIN, "https://aanvaller.example.com")

        val forwarded = filter(request)

        assertEquals("https://aanvaller.example.com", forwarded.getHeader(HttpHeaders.ORIGIN))
        assertTrue(CorsUtils.isCorsRequest(forwarded))
    }

    @Test
    fun `a request without an origin header passes through unchanged`() {
        val request = requestTo(host = "hkh-autopilot-acceptance.vdzonsoftware.nl", port = 80)

        val forwarded = filter(request)

        assertNull(forwarded.getHeader(HttpHeaders.ORIGIN))
        assertFalse(CorsUtils.isCorsRequest(forwarded))
    }

    @Test
    fun `the public port is invisible behind the route so an origin without port counts as same origin`() {
        val request = requestTo(host = "hkh-autopilot.vdzonsoftware.nl", port = 80)

        assertTrue(SameOriginRequestFilter.isSameOrigin("https://hkh-autopilot.vdzonsoftware.nl", request))
        assertTrue(SameOriginRequestFilter.isSameOrigin("http://hkh-autopilot.vdzonsoftware.nl", request))
    }

    @Test
    fun `a different port in the origin stays cross origin`() {
        val request = requestTo(host = "localhost", port = 8080)

        assertFalse(SameOriginRequestFilter.isSameOrigin("http://localhost:3000", request))
        assertTrue(SameOriginRequestFilter.isSameOrigin("http://localhost:8080", request))
    }

    @Test
    fun `a different host stays cross origin`() {
        val request = requestTo(host = "hkh-autopilot.vdzonsoftware.nl", port = 80)

        assertFalse(SameOriginRequestFilter.isSameOrigin("https://hkh-autopilot-acceptance.vdzonsoftware.nl", request))
        assertFalse(
            SameOriginRequestFilter.isSameOrigin("https://hkh-autopilot.vdzonsoftware.nl.example.com", request),
        )
    }

    @Test
    fun `hostnames are compared case insensitively`() {
        val request = requestTo(host = "HKH-Autopilot.vdzonsoftware.nl", port = 80)

        assertTrue(SameOriginRequestFilter.isSameOrigin("https://hkh-autopilot.vdzonsoftware.nl", request))
    }

    @Test
    fun `an opaque or unparsable origin never counts as same origin`() {
        val request = requestTo(host = "hkh-autopilot.vdzonsoftware.nl", port = 80)

        assertFalse(SameOriginRequestFilter.isSameOrigin("null", request))
        assertFalse(SameOriginRequestFilter.isSameOrigin("", request))
        assertFalse(SameOriginRequestFilter.isSameOrigin("geen geldige uri", request))
        assertFalse(SameOriginRequestFilter.isSameOrigin("file://hkh-autopilot.vdzonsoftware.nl", request))
    }

    private fun requestTo(host: String, port: Int): MockHttpServletRequest {
        val request = MockHttpServletRequest("POST", "/api/place-search")
        request.serverName = host
        request.serverPort = port
        return request
    }

    private fun filter(request: MockHttpServletRequest): HttpServletRequest {
        val chain = MockFilterChain()
        SameOriginRequestFilter().doFilter(request, MockHttpServletResponse(), chain)
        return chain.request as HttpServletRequest
    }
}
