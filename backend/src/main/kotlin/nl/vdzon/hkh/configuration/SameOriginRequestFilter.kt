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
 * Het herkomstschema wordt bewust niet met dat van het verzoek vergeleken, hoewel het schema deel
 * uitmaakt van de origin-definitie. Achter de OpenShift-route termineert de edge TLS, dus de
 * backend ziet elk verzoek als plain HTTP terwijl de browserherkomst `https` is; een directe
 * vergelijking zou same-origin verkeer daardoor altijd afwijzen en precies de storing terugbrengen
 * die deze filter oplost. `X-Forwarded-Proto` is hier geen betrouwbare vervanging, omdat de
 * tussenliggende frontend-nginx die header met `$scheme` overschrijft. De versoepeling blijft
 * beperkt tot dezelfde hostnaam: hooguit telt een pagina op `http://<host>` als same-origin voor
 * een verzoek aan `https://<host>`.
 *
 * Dit verzwakt de cross-site-bescherming niet. De filter grijpt uitsluitend in wanneer de herkomst
 * van de pagina gelijk is aan de host waaraan het verzoek is gericht; een pagina op een andere
 * host houdt haar eigen `Origin` en wordt dus nog steeds tegen de patronen getoetst. Een browser
 * stuurt cookies alleen naar de host waar ze bij horen, dus een aanvallerspagina kan met deze
 * filter geen sessie van een andere host meeliften.
 *
 * Het verbergen geldt voor de volledige filterketen, dus ook voor applicatiecode die de header zelf
 * leest. Die code mag de herkomst niet verliezen: de agenttoegangscontrole toetst de herkomst van
 * een aanmelding tegen een eigen allowlist en zou anders stilzwijgend worden overgeslagen. De
 * oorspronkelijke waarde blijft daarom beschikbaar als requestattribuut
 * [ORIGINAL_ORIGIN_ATTRIBUTE].
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
            request.setAttribute(ORIGINAL_ORIGIN_ATTRIBUTE, origin)
            filterChain.doFilter(OriginHiddenHttpServletRequest(request), response)
        } else {
            filterChain.doFilter(request, response)
        }
    }

    companion object {
        /**
         * Requestattribuut met de `Origin`-header die voor de CORS-toetsing is verborgen. Alleen
         * gezet wanneer de filter daadwerkelijk ingrijpt; bij een cross-origin verzoek blijft de
         * header zelf staan. Applicatiecode die de herkomst nodig heeft leest dit attribuut met de
         * header als terugval, zodat een eigen herkomstcontrole niet van deze filter afhangt.
         * Modulegrenzen verbieden een directe verwijzing vanuit andere modules, dus de naam staat
         * daar als letterlijke constante; `AgentAccessOriginAllowlistTest` toetst dat beide
         * gelijk blijven.
         */
        const val ORIGINAL_ORIGIN_ATTRIBUTE: String =
            "nl.vdzon.hkh.configuration.SameOriginRequestFilter.ORIGINAL_ORIGIN"

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
