package nl.vdzon.hkh.auth

import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.*

data class AgentSessionRequest(val email: String = "")

/**
 * Naam van het requestattribuut waarin `nl.vdzon.hkh.configuration.SameOriginRequestFilter` de voor
 * de CORS-toetsing verborgen `Origin`-header bewaart.
 *
 * De aanmeldpagina wordt op dezelfde origin geserveerd als de frontend, dus haar `fetch` naar
 * `/api/auth/agent-session` is een same-origin verzoek waarvan die filter de header verbergt.
 * Zonder dit attribuut zou [AgentAccessVerifier] hier altijd `null` zien en zijn herkomst-allowlist
 * stilzwijgend overslaan. De modulegrenzen staan geen verwijzing naar de `configuration`-module toe,
 * daarom staat de naam hier letterlijk; `AgentAccessOriginAllowlistTest` bewaakt dat beide constanten
 * gelijk blijven.
 */
const val HIDDEN_ORIGIN_ATTRIBUTE: String = "nl.vdzon.hkh.configuration.SameOriginRequestFilter.ORIGINAL_ORIGIN"

@RestController
class AgentAccessController(private val access: AgentAccessVerifier, private val sessions: AgentAdminSessions) {
    @PostMapping("/api/auth/agent-session")
    fun login(@RequestHeader("X-AI-Access-Token", required = false) token: String?,
              @RequestHeader("Origin", required = false) originHeader: String?,
              @RequestAttribute(name = HIDDEN_ORIGIN_ATTRIBUTE, required = false) hiddenOrigin: String?,
              @RequestBody body: AgentSessionRequest, response: HttpServletResponse): Map<String, Any> {
        response.setHeader("Cache-Control", "no-store")
        val email = access.verify(token, body.email, hiddenOrigin ?: originHeader)
        return sessions.create(email)
    }

    @GetMapping("/api/auth/agent-login", produces = ["text/html"])
    fun page(response: HttpServletResponse): String {
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("Referrer-Policy", "no-referrer")
        response.setHeader("X-Frame-Options", "DENY")
        return """<!doctype html><html lang="nl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><meta name="referrer" content="no-referrer"><title>Agenttoegang</title>
<body><main><h1>Agenttoegang</h1><p>Productietoegang uitsluitend na toestemming van Robbert voor deze taak.</p>
<form id="login"><p><label>Token <input id="token" type="password" autocomplete="off" required></label></p><p><label>Gebruiker <input id="email" autocomplete="off" required></label></p><button>Sessie openen</button></form><p id="status" role="status"></p><a id="open" href="/" hidden>Applicatie openen</a></main>
<script>document.getElementById('login').onsubmit=async(event)=>{event.preventDefault();const field=document.getElementById('token');const status=document.getElementById('status');try{const result=await fetch('/api/auth/agent-session',{method:'POST',headers:{'Content-Type':'application/json','X-AI-Access-Token':field.value},body:JSON.stringify({email:document.getElementById('email').value})});field.value='';if(!result.ok)throw new Error('Aanmelden geweigerd ('+result.status+').');const data=await result.json();localStorage.setItem('flutter.hkh_admin_session_token', JSON.stringify(data.token));
localStorage.setItem('flutter.hkh_admin_session_email', JSON.stringify(data.email)); status.textContent='Je werkt nu als '+data.email;document.getElementById('open').hidden=false;}catch(error){field.value='';status.textContent=error.message;}};</script></body></html>"""
    }
}
