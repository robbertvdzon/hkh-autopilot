package nl.vdzon.hkh.auth

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@Component
class AgentAdminSessions(private val config: AdminAuthConfig) {
    private data class Session(val email: String, val expiresAt: Instant)
    private val sessions = ConcurrentHashMap<String, Session>()
    fun create(email: String): Map<String, Any> {
        if (!config.isAllowed(email)) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        sessions.entries.removeIf { !it.value.expiresAt.isAfter(Instant.now()) }
        if (sessions.size >= 1000) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)
        val token = "ai_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        sessions[token] = Session(email, Instant.now().plusSeconds(3600))
        return mapOf("token" to token, "email" to email)
    }
    fun resolve(authorization: String?): AuthenticatedAdmin? {
        val token = authorization?.removePrefix("Bearer ") ?: return null
        if (!token.startsWith("ai_")) return null
        val session = sessions[token]?.takeIf { it.expiresAt.isAfter(Instant.now()) && config.isAllowed(it.email) }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return AuthenticatedAdmin(session.email)
    }
}
