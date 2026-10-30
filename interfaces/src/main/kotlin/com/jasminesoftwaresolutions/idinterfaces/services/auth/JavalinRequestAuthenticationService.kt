package com.jasminesoftwaresolutions.idinterfaces.services.auth

import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import com.jasminesoftwaresolutions.id.domain.services.authentication.IRequestAuthenticationService
import com.jasminesoftwaresolutions.id.domain.services.authorization.ITokenService
import io.javalin.http.Context
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.Instant
import java.util.*

/**
 * Authorization context with either an [ISession] (cookie-based auth) or an [IToken] (bearer auth).
 * The resource server verifies scopes. Witchwood evaluates policies via evaluate(principal, action, resource).
 */
sealed class PrincipalAuthorizationContext {
    /** The principal's UUID (account.id for sessions, client.id for service tokens, subject.id for other tokens). */
    abstract val principalId: UUID
    abstract val scopes: Set<String>

    data class SessionAuth(val session: ISession) : PrincipalAuthorizationContext() {
        override val principalId: UUID get() = session.account.id
        override val scopes: Set<String> get() = emptySet()
    }

    data class TokenAuth(val token: IToken) : PrincipalAuthorizationContext() {
        override val principalId: UUID get() = token.subject.id
        override val scopes: Set<String> get() = token.scopes
    }
}

class JavalinRequestAuthenticationService(
    val sessionRepository: ISessionRepository,
    val clientRepository: IClientRepository,
    val tokenService: ITokenService<out IAccessToken, out IRefreshToken, out IServiceToken, out IIDToken>,
) : IRequestAuthenticationService<Context, PrincipalAuthorizationContext> {
    private val AUTHENTICATE_CACHE_KEY = "JavalinAuthorizationService#authenticate(io.javalin.http.Context)"
    private val SESSION_ID_COOKIE_KEY = "session_id"
    private val SESSION_TOKEN_COOKIE_KEY = "session_token"

    @WithSpan
    override fun authenticate(request: Context): PrincipalAuthorizationContext? {
        // Try request cache
        val cacheResult = request.attribute<Optional<PrincipalAuthorizationContext>>(
            key = AUTHENTICATE_CACHE_KEY
        )

        if (cacheResult != null)
            return cacheResult.get()

        // Try session cookie
        val sessionId = request.cookie(SESSION_ID_COOKIE_KEY)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        val sessionToken = request.cookie(SESSION_TOKEN_COOKIE_KEY)

        if (sessionId != null && sessionToken != null) {
            val session = sessionRepository.findById(sessionId)
                ?: return null

            if (session.verify(sessionToken) && session.expiresAt > Instant.now())
                return PrincipalAuthorizationContext.SessionAuth(session)

            return null
        }

        // Try Authorization header
        val authorizationHeader = request.header("Authorization") ?: return null
        val parts = authorizationHeader.split(" ")
        if (parts.size != 2) return null

        val type = parts[0]
        val token = parts[1]

        return when (type) {
            "Basic" -> authenticateBasic(token)
            "Bearer" -> authenticateBearer(token)
            else -> null
        }
    }

    private fun authenticateBasic(token: String): PrincipalAuthorizationContext.TokenAuth? {
        val decoded = String(Base64.getDecoder().decode(token))
        val parts = decoded.split(":", limit = 2)
        if (parts.size != 2) return null

        val clientId = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return null
        val clientSecret = parts[1]

        val client = clientRepository.findById(clientId) ?: return null
        if (!client.verify(clientSecret) || client.suspended) return null

        return PrincipalAuthorizationContext.TokenAuth(object : IToken {
            override val id: UUID = UUID.randomUUID()
            override val token: String = ""
            override val expiresAt: Instant = Instant.MAX
            override val issuedAt: Instant = Instant.now()
            override val notBefore: Instant = issuedAt
            override val subject = client
            override val audience = listOf("https://${client.fqdn}/")
            override val scopes: Set<String> = emptySet()
        })
    }

    private fun authenticateBearer(token: String): PrincipalAuthorizationContext.TokenAuth? {
        val decoded = tokenService.decode(token) ?: return null

        return when (decoded) {
            is IToken -> PrincipalAuthorizationContext.TokenAuth(decoded)
            else -> null
        }
    }
}
