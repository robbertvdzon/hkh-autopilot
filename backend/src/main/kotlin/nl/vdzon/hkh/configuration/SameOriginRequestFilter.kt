package nl.vdzon.hkh.configuration

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import java.util.Collections
import java.util.Enumeration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Verbergt de `Origin`-header van verzoeken die aantoonbaar van dezelfde herkomst komen als het
 * verzoek zelf.
 *
 * Achtergrond: de webapp wordt same-origin geserveerd; nginx proxyt `/api/` naar de backend met
 * `Host $host`, dus de backend ziet dezelfde hostnaam als de browser. Browsers sturen bij een POST
 * ook op een same-origin verzoek een `Origin`-header mee, en Spring Framework beschouwt sinds 6.0
 * elk verzoek met zo'n header als CORS-verzoek. Zonder deze filter toetst Spring dus ook
 * same-origin verkeer tegen `hkh.cors-allowed-origin-patterns`, waardoor een afwijkende of lege
 * patroonlijst de volledige API in de browser met 403 `Invalid CORS request` blokkeert.
 *
 * Een verzoek geldt alleen als same-origin wanneer de herkomst http(s) is, de hostnaam exact gelijk
 * is aan de host waaraan het verzoek is gericht, en de herkomst geen afwijkende poort noemt. De
 * publieke poort is achter de OpenShift-route niet zichtbaar voor de backend (de `Host`-header
 * bevat er geen), dus een herkomst zonder expliciete poort wordt als dezelfde poort gelezen; een
 * herkomst met expliciete poort moet wel overeenkomen. Daardoor blijft bijvoorbeeld een lokale
 * frontend op `http://localhost:3000` tegenover een backend op poort 8080 een echt cross-origin
 * verzoek dat gewoon langs de CORS-patronen gaat.
 *
 * Dit verzwakt de cross-site-bescherming niet. De filter grijpt uitsluitend in wanneer de herkomst
 * van de pagina gelijk is aan de host waaraan het verzoek is gericht; een pagina op een andere
 * host houdt haar eigen `Origin` en wordt dus nog steeds tegen de patronen getoetst. Een browser
 * stuurt cookies alleen naar de host waar ze bij horen, dus een aanvallerspagina kan met deze
 * filter geen sessie van een andere host meeliften.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class SameOriginRequestFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val origin = request.getHeader(HttpHeaders.ORIGIN)
        if (origin != null && isSameOrigin(origin, request)) {
            filterChain.doFilter(OriginHiddenHttpServletRequest(request), response)
        } else {
            filterChain.doFilter(request, response)
        }
    }

    companion object {
        fun isSameOrigin(origin: String, request: HttpServletRequest): Boolean {
            val uri = runCatching { URI(origin) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "http" && scheme != "https") {
                return false
            }
            val host = uri.host ?: return false
            if (!host.equals(request.serverName, ignoreCase = true)) {
                return false
            }
            return uri.port == -1 || uri.port == request.serverPort
        }
    }
}

private class OriginHiddenHttpServletRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    override fun getHeader(name: String): String? = if (isOrigin(name)) null else super.getHeader(name)

    override fun getHeaders(name: String): Enumeration<String> =
        if (isOrigin(name)) Collections.emptyEnumeration() else super.getHeaders(name)

    override fun getHeaderNames(): Enumeration<String> =
        Collections.enumeration(super.getHeaderNames().toList().filterNot(::isOrigin))

    private fun isOrigin(name: String): Boolean = name.equals(HttpHeaders.ORIGIN, ignoreCase = true)
}
