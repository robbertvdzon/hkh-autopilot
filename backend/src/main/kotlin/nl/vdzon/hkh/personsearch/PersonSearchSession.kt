package nl.vdzon.hkh.personsearch

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.SecureRandom
import java.util.Base64
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

/** Naam van de route-gebonden, cookie-gebaseerde sessie voor anonieme bezoekers. Geen login. */
const val PERSON_SEARCH_SESSION_COOKIE_NAME = "hkh_person_search_session"

/**
 * Minimale, aan de persoonszoekroute gebonden serverside sessie voor anonieme bezoekers: een
 * server-uitgegeven, niet-raadbare sessiecookie zonder login, uitsluitend gebruikt om jobs en
 * idempotentiesleutels aan een bezoeker te binden. Dit is geen uitbreiding van het bestaande
 * admin/Google-authenticatiemechanisme.
 */
@Component
class PersonSearchSessionResolver {
    private val random = SecureRandom()

    fun resolve(request: HttpServletRequest, response: HttpServletResponse): String {
        val existing = request.cookies
            ?.firstOrNull { it.name == PERSON_SEARCH_SESSION_COOKIE_NAME }
            ?.value
            ?.takeIf { it.isNotBlank() }
        if (existing != null) return existing

        val sessionId = newSessionId()
        val cookie = ResponseCookie.from(PERSON_SEARCH_SESSION_COOKIE_NAME, sessionId)
            .httpOnly(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(java.time.Duration.ofHours(24))
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        return sessionId
    }

    private fun newSessionId(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
