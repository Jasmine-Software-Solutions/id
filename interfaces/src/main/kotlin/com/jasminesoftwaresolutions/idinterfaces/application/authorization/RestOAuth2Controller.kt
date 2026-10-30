package com.jasminesoftwaresolutions.idinterfaces.application.authorization

import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IAccessToken
import com.jasminesoftwaresolutions.id.domain.models.authorization.IIDToken
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRefreshToken
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.IServiceToken
import com.jasminesoftwaresolutions.id.domain.models.authorization.IToken
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IDelegatedSessionRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IScopeRepository
import com.jasminesoftwaresolutions.id.domain.services.IEncryptionFunction
import com.jasminesoftwaresolutions.id.domain.services.authorization.IOIDCClaimsResolver
import com.jasminesoftwaresolutions.id.domain.services.authorization.ITokenService
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import com.jasminesoftwaresolutions.idinterfaces.services.auth.ScopedListHelper
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.http.*
import java.net.URI
import java.time.Instant
import java.util.*
import java.util.logging.Logger

class RestOAuth2Controller(
    private val clientRepository: IClientRepository,
    private val delegatedSessionRepository: IDelegatedSessionRepository,
    private val encryptionFunction: IEncryptionFunction,
    private val tokenService: ITokenService<IAccessToken, IRefreshToken, IServiceToken, IIDToken>,
    private val scopeRegistry: IScopeRegistry<IScope>,
    private val scopeRepository: IScopeRepository,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val scopedListHelper: ScopedListHelper,
    private val oidcClaimsResolver: IOIDCClaimsResolver,
    private val issuerBaseUrl: String,
) {
    private val log = Logger.getLogger(RestOAuth2Controller::class.java.name)

    private fun findScopeById(id: String): IScope? =
        scopeRegistry.findById(id) ?: scopeRepository.findById(id)

    // ---- OIDC Discovery ----

    @Get("/.well-known/openid-configuration")
    fun openIdConfiguration(ctx: Context) {
        ctx.json(
            mapOf(
                "issuer" to issuerBaseUrl.trimEnd('/'),
                "authorization_endpoint" to "$issuerBaseUrl/api/v1/oauth2/authorize",
                "token_endpoint" to "$issuerBaseUrl/api/v1/oauth2/token",
                "userinfo_endpoint" to "$issuerBaseUrl/api/v1/oauth2/userinfo",
                "jwks_uri" to "$issuerBaseUrl/.well-known/jwks.json",
                "scopes_supported" to listOf("openid", "profile", "email"),
                "response_types_supported" to listOf("code"),
                "subject_types_supported" to listOf("public"),
                "id_token_signing_alg_values_supported" to listOf("RS256"),
                "token_endpoint_auth_methods_supported" to listOf("client_secret_basic", "client_secret_post"),
                "claims_supported" to listOf(
                    "sub",
                    "iss",
                    "aud",
                    "iat",
                    "exp",
                    "nonce",
                    "name",
                    "given_name",
                    "family_name",
                    "email",
                    "email_verified"
                ),
            )
        )
    }

    // ---- OAuth2 Authorization Endpoint ----

    @Get("/api/v1/oauth2/authorize")
    fun authorize(ctx: Context) {
        System.out.println("[OAuth2 authorize] START fullUrl=" + ctx.fullUrl())
        System.out.println("[OAuth2 authorize] cookies=" + ctx.cookieMap().keys)

        val auth = authorizationService.authenticate(ctx)
        System.out.println("[OAuth2 authorize] authenticate result=" + auth)
        if (auth == null) {
            val sessionId = ctx.cookie("session_id")
            val sessionToken = ctx.cookie("session_token")
            System.out.println("[OAuth2 authorize] NOT AUTHENTICATED - session_id=" + sessionId + " session_token=" + sessionToken)
            // Not logged in — redirect to login page, preserving the full authorize URL
            // so the user is returned here after authentication.
            val loginUrl = "$issuerBaseUrl/#/login"
            val returnUrl = java.net.URLEncoder.encode(ctx.fullUrl(), "UTF-8")
            System.out.println("[OAuth2 authorize] redirecting to=" + loginUrl)
            ctx.redirect("$loginUrl?return_url=$returnUrl")
            return
        }

        System.out.println("[OAuth2 authorize] auth type=" + auth.javaClass.simpleName)

        // Only session-authenticated users can authorize
        val sessionAuth = auth as? PrincipalAuthorizationContext.SessionAuth
            ?: throw ForbiddenResponse("Authorization requires a user session")

        val responseType = ctx.queryParam("response_type") ?: throw BadRequestResponse("Missing response_type")
        if (responseType != "code")
            throw BadRequestResponse("Unsupported response_type; only 'code' is supported")

        val clientId = ctx.queryParam("client_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw BadRequestResponse("Missing or invalid client_id")

        val redirectUri = ctx.queryParam("redirect_uri")
            ?: throw BadRequestResponse("Missing redirect_uri")

        val state = ctx.queryParam("state")

        val client = clientRepository.findById(clientId)
            ?: throw BadRequestResponse("Invalid client_id")

        if (client.redirectUris.values.none { it.toString() == redirectUri })
            throw BadRequestResponse("Invalid redirect_uri")

        val scopeParam = ctx.queryParam("scope") ?: ""
        val scopes: Set<IScope> = scopeParam
            .split(" ")
            .mapNotNull { findScopeById(it) }
            .toSet()

        val nonce = ctx.queryParam("nonce")

        // Create delegated session with authorization code
        val code = SecureToken()
        val hashedSession = sessionAuth.session as? IHashedSession
            ?: throw ForbiddenResponse()
        val delegatedSession = delegatedSessionRepository.create {
            session = hashedSession
            this.client = client
            this.redirectUri = URI.create(redirectUri)
            this.scopes = scopes
            this.nonce = nonce
            this.code.code = code
            this.code.expiresAt = Instant.now().plusSeconds(600) // 10 minutes
            this.expiresAt = hashedSession.expiresAt
        }

        val codeParam = Base64.getEncoder().encodeToString(
            "${delegatedSession.id}:$code".toByteArray(Charsets.UTF_8)
        )

        // Build redirect URL
        val redirectParams = mutableMapOf("code" to codeParam)
        if (state != null) redirectParams["state"] = state

        val redirectUrl = buildRedirectUrl(redirectUri, redirectParams)
        ctx.redirect(redirectUrl)
    }

    // ---- Entity Listing ----

    @Get("/api/v1/entities/clients")
    fun listClients(ctx: Context) {
        val auth = authorizationService.authenticate(ctx) ?: throw UnauthorizedResponse()
        val all = clientRepository.entries()
        val visible = scopedListHelper.filterVisible(
            principalId = auth.principalId,
            action = IdActions.LIST_CLIENTS,
            entities = all,
            resourceFn = { IdResources.client(it.id) },
        )
        ctx.json(visible.map { mapOf("id" to it.id, "fqdn" to it.fqdn) })
    }

    // ---- Token Endpoint ----

    @Post("/api/v1/oauth2/token")
    fun token(ctx: Context) {
        val grantType = ctx.queryParam("grant_type")
        val rtPreview = if (grantType == "refresh_token") {
            val rt = ctx.queryParam("refresh_token") ?: "null"
            rt.take(16) + "..."
        } else "N/A"

        log.info("[TOKEN] grant_type=%s refresh_token=%s".format(grantType, rtPreview))

        val auth = authorizationService.authenticate(ctx)
        if (auth == null) {
            log.warning("[TOKEN] UNAUTHORIZED grant_type=%s — no auth context".format(grantType))
            throw UnauthorizedResponse()
        }

        log.info("[TOKEN] grant_type=%s authType=%s principalId=%s".format(
            grantType, auth.javaClass.simpleName, auth.principalId))

        when (grantType) {
            "authorization_code" -> tokenWithCode(ctx, auth)
            "client_credentials" -> tokenWithCredentials(ctx, auth)
            "refresh_token" -> tokenWithRefresh(ctx, auth)
            else -> throw BadRequestResponse("Invalid grant_type")
        }
    }

    private fun tokenWithCode(ctx: Context, auth: PrincipalAuthorizationContext) {
        val clientId = ctx.queryParam("client_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw BadRequestResponse("Missing client_id")
        val redirectUri = ctx.queryParam("redirect_uri")
            ?: throw BadRequestResponse("Missing redirect_uri")
        val code = ctx.queryParam("code")
            ?: throw BadRequestResponse("Missing code")

        log.info("[AUTHZ_CODE] START clientId=%s".format(clientId))

        val client = clientRepository.findById(clientId)
            ?: throw BadRequestResponse("Invalid client_id")

        if (client.redirectUris.values.none { it.toString() == redirectUri })
            throw BadRequestResponse("Invalid redirect_uri")

        val decoded = Base64.getDecoder().decode(code).toString(Charsets.UTF_8).split(":")
        if (decoded.size != 2) throw BadRequestResponse("Invalid code")

        val sessionId = runCatching { UUID.fromString(decoded[0]) }.getOrNull()
            ?: throw BadRequestResponse("Invalid code")
        val sessionCode = decoded[1]

        val delegatedSession = delegatedSessionRepository.findById(sessionId)
            ?: throw BadRequestResponse("Invalid code")

        if (!delegatedSession.code.verify(sessionCode))
            throw BadRequestResponse("Invalid code")

        log.info("[AUTHZ_CODE] dsId=%s dsExpiresAt=%s sessionExpiresAt=%s sessionId=%s".format(
            delegatedSession.id, delegatedSession.expiresAt,
            delegatedSession.session.expiresAt, delegatedSession.session.id))

        val accessToken = tokenService.generateDelegatedSessionAccessToken(delegatedSession)
        if (accessToken == null) {
            log.warning("[AUTHZ_CODE] FORBIDDEN dsId=%s — generateDelegatedSessionAccessToken returned null (dsExp=%s sessionExp=%s now=%s)".format(
                delegatedSession.id, delegatedSession.expiresAt, delegatedSession.session.expiresAt, Instant.now()))
            throw ForbiddenResponse()
        }
        val refreshToken = tokenService.generateDelegatedSessionRefreshToken(delegatedSession)
        if (refreshToken == null) {
            log.warning("[AUTHZ_CODE] FORBIDDEN dsId=%s — generateDelegatedSessionRefreshToken returned null (dsExp=%s sessionExp=%s now=%s)".format(
                delegatedSession.id, delegatedSession.expiresAt, delegatedSession.session.expiresAt, Instant.now()))
            throw ForbiddenResponse()
        }

        log.info("[AUTHZ_CODE] SUCCESS dsId=%s atJti=%s rtJti=%s".format(
            delegatedSession.id, accessToken.id, refreshToken.id))

        val response = mutableMapOf<String, Any>(
            "token_type" to "Bearer",
            "access_token" to accessToken.token,
            "expires_in" to (accessToken.expiresAt.epochSecond - Instant.now().epochSecond),
            "refresh_token" to refreshToken.token,
            "refresh_token_expires_in" to (delegatedSession.session.expiresAt.epochSecond - Instant.now().epochSecond),
        )

        // Include ID Token if openid scope was granted
        if (delegatedSession.scopes.any { it.id == "openid" }) {
            val claims = oidcClaimsResolver.resolveClaims(delegatedSession)
            val idToken = tokenService.generateIDToken(delegatedSession, claims)
            if (idToken != null) {
                response["id_token"] = idToken.token
            }
        }

        ctx.status(200)
        ctx.json(response)
    }

    private fun tokenWithCredentials(ctx: Context, auth: PrincipalAuthorizationContext) {
        val scope = ctx.queryParam("scope")

        log.info("[CLIENT_CREDS] START clientId=%s scope=%s".format(auth.principalId, scope))

        val client = clientRepository.findById(auth.principalId)
        if (client == null) {
            log.warning("[CLIENT_CREDS] FORBIDDEN clientId=%s — client not found".format(auth.principalId))
            throw ForbiddenResponse()
        }

        if (!client.confidential) {
            log.warning("[CLIENT_CREDS] FORBIDDEN clientId=%s fqdn=%s — client is not confidential".format(
                client.id, client.fqdn))
            throw ForbiddenResponse()
        }

        val scopes: Set<String> = scope
            ?.split(" ")
            ?.mapNotNull { findScopeById(it)?.id }
            ?.toSet()
            ?: emptySet()

        val accessToken = tokenService.generateServiceSessionAccessToken(client, scopes)
        if (accessToken == null) {
            log.warning("[CLIENT_CREDS] FORBIDDEN clientId=%s — generateServiceSessionAccessToken returned null".format(client.id))
            throw ForbiddenResponse()
        }

        log.info("[CLIENT_CREDS] SUCCESS clientId=%s atJti=%s".format(client.id, accessToken.id))

        ctx.status(200)
        ctx.json(
            mapOf(
                "token_type" to "Bearer",
                "access_token" to accessToken.token,
                "expires_in" to (accessToken.expiresAt.epochSecond - Instant.now().epochSecond),
            )
        )
    }

    private fun tokenWithRefresh(ctx: Context, auth: PrincipalAuthorizationContext) {
        val refreshTokenStr = ctx.queryParam("refresh_token")
            ?: throw BadRequestResponse("Missing refresh_token")

        log.info("[REFRESH] START clientId=%s rtPrefix=%s".format(
            auth.principalId, refreshTokenStr.take(16)))

        // -- Step 1: Look up the authenticating client
        val client = clientRepository.findById(auth.principalId)
        if (client == null) {
            log.warning("[REFRESH] FORBIDDEN clientId=%s — client not found".format(auth.principalId))
            throw ForbiddenResponse()
        }

        log.info("[REFRESH] client found: id=%s fqdn=%s confidential=%s suspended=%s".format(
            client.id, client.fqdn, client.confidential, client.suspended))

        if (!client.confidential) {
            log.warning("[REFRESH] FORBIDDEN clientId=%s fqdn=%s — client is not confidential".format(
                client.id, client.fqdn))
            throw ForbiddenResponse()
        }

        // -- Step 2: Decode the refresh token JWT
        val decoded = tokenService.decode(refreshTokenStr)
        if (decoded == null) {
            log.warning("[REFRESH] FORBIDDEN clientId=%s — tokenService.decode returned null".format(client.id))
            throw BadRequestResponse("Invalid refresh_token")
        }

        val refreshToken = decoded as? IRefreshToken
        if (refreshToken == null) {
            log.warning("[REFRESH] FORBIDDEN clientId=%s — decoded token is %s, not IRefreshToken".format(
                client.id, decoded.javaClass.simpleName))
            throw BadRequestResponse("Invalid refresh_token")
        }

        val ds = refreshToken.delegatedSession
        val sess = refreshToken.session

        log.info("[REFRESH] TOKEN_DECODED jti=%s sub=%s delegatedSessionId=%s sessionId=%s".format(
            refreshToken.id, refreshToken.subject.id, ds.id, sess.id))
        log.info("[REFRESH] TOKEN_TIMING now=%s iat=%s exp=%s nbf=%s".format(
            Instant.now(), refreshToken.issuedAt, refreshToken.expiresAt, refreshToken.notBefore))
        log.info("[REFRESH] SESSION_STATE sessionExpiresAt=%s sessionExpired=%s".format(
            sess.expiresAt, sess.expiresAt <= Instant.now()))
        log.info("[REFRESH] DELEGATED_STATE dsExpiresAt=%s dsExpired=%s refreshedAt=%s".format(
            ds.expiresAt, ds.expiresAt <= Instant.now(), ds.refreshedAt))

        // Check refresh token validity window
        val now = Instant.now()
        if (now < refreshToken.notBefore) {
            log.warning("[REFRESH] FORBIDDEN jti=%s — now(%s) < nbf(%s)".format(
                refreshToken.id, now, refreshToken.notBefore))
            throw ForbiddenResponse()
        }
        if (now > refreshToken.expiresAt) {
            log.warning("[REFRESH] FORBIDDEN jti=%s — now(%s) > exp(%s)".format(
                refreshToken.id, now, refreshToken.expiresAt))
            throw ForbiddenResponse()
        }

        // Check session expiration
        if (sess.expiresAt <= now) {
            log.warning("[REFRESH] FORBIDDEN jti=%s — session %s expired at %s".format(
                refreshToken.id, sess.id, sess.expiresAt))
            throw ForbiddenResponse()
        }

        // Check delegated session expiration
        if (ds.expiresAt <= now) {
            log.warning("[REFRESH] FORBIDDEN jti=%s — delegatedSession %s expired at %s (now=%s)".format(
                refreshToken.id, ds.id, ds.expiresAt, now))
            throw ForbiddenResponse()
        }

        // Check refresh token hasn't been superseded by a newer refresh.
        // Add a 1-second grace window because JWT iat is truncated to seconds while
        // ds.refreshedAt carries millisecond (or finer) precision. Without the window,
        // a refresh that happens within the same second as token issuance falsely
        // marks the token as superseded.
        if (ds.refreshedAt != null && refreshToken.issuedAt.plusSeconds(1) < ds.refreshedAt) {
            log.warning("[REFRESH] FORBIDDEN jti=%s — token iat(%s) + 1s < ds.refreshedAt(%s) (token superseded)".format(
                refreshToken.id, refreshToken.issuedAt, ds.refreshedAt))
            throw ForbiddenResponse()
        }

        // -- Step 3: Renew the access token
        val accessToken = tokenService.renewDelegatedSessionAccessToken(refreshToken)
        if (accessToken == null) {
            log.warning("[REFRESH] FORBIDDEN jti=%s — renewDelegatedSessionAccessToken returned null".format(
                refreshToken.id))
            throw ForbiddenResponse()
        }

        log.info("[REFRESH] ACCESS_TOKEN_GENERATED atJti=%s atExp=%s".format(
            accessToken.id, accessToken.expiresAt))

        // -- Step 4: Rotate the refresh token
        val newRefreshToken = tokenService.generateDelegatedSessionRefreshToken(refreshToken.delegatedSession)
        if (newRefreshToken == null) {
            log.warning("[REFRESH] FORBIDDEN jti=%s — generateDelegatedSessionRefreshToken returned null (dsExp=%s sessionExp=%s)".format(
                refreshToken.id, ds.expiresAt, sess.expiresAt))
            throw ForbiddenResponse()
        }

        log.info("[REFRESH] SUCCESS oldJti=%s newAtJti=%s newRtJti=%s".format(
            refreshToken.id, accessToken.id, newRefreshToken.id))

        val response = mutableMapOf<String, Any>(
            "token_type" to "Bearer",
            "access_token" to accessToken.token,
            "expires_in" to (accessToken.expiresAt.epochSecond - Instant.now().epochSecond),
            "refresh_token" to newRefreshToken.token,
            "refresh_token_expires_in" to (newRefreshToken.expiresAt.epochSecond - Instant.now().epochSecond),
        )

        // Include ID Token if openid scope was granted on the delegated session
        if (refreshToken.delegatedSession.scopes.any { it.id == "openid" }) {
            val claims = oidcClaimsResolver.resolveClaims(refreshToken.delegatedSession)
            val idToken = tokenService.generateIDToken(refreshToken.delegatedSession, claims)
            if (idToken != null) {
                response["id_token"] = idToken.token
            }
        }

        ctx.status(200)
        ctx.json(response)
    }

    // ---- UserInfo Endpoint ----

    @Get("/api/v1/oauth2/userinfo")
    fun userinfo(ctx: Context) {
        val auth = authorizationService.authenticate(ctx) ?: throw UnauthorizedResponse()

        // UserInfo requires a Bearer token (not Basic auth or session)
        if (auth !is PrincipalAuthorizationContext.TokenAuth)
            throw ForbiddenResponse("UserInfo requires a Bearer token")

        // Extract the raw token string from the Authorization header
        val rawToken = ctx.header("Authorization")?.removePrefix("Bearer ")?.trim()
            ?: throw ForbiddenResponse()

        val token = tokenService.decode(rawToken) as? IAccessToken
            ?: throw ForbiddenResponse("Invalid access token")

        val delegatedSession = token.delegatedSession
        val claims = oidcClaimsResolver.resolveClaims(delegatedSession)

        ctx.json(claims)
    }

    // ---- Introspection Endpoint ----

    @Post("/api/v1/oauth2/introspect")
    fun introspect(ctx: Context) {
        authorizationService.authenticate(ctx) ?: throw UnauthorizedResponse()

        val rawToken = ctx.queryParam("token")
            ?: throw BadRequestResponse("Missing token parameter")

        val decoded = try {
            tokenService.decode(rawToken)
        } catch (e: Exception) {
            ctx.json(mapOf("active" to false))
            return
        }

        if (decoded == null) {
            ctx.json(mapOf("active" to false))
            return
        }

        val token = decoded as? IToken ?: run {
            ctx.json(mapOf("active" to false))
            return
        }

        val response = mutableMapOf<String, Any>(
            "active" to true,
            "sub" to token.subject.id,
            "exp" to token.expiresAt.epochSecond,
            "iat" to token.issuedAt.epochSecond,
        )

        when (token) {
            is IAccessToken -> {
                response["token_type"] = "Bearer"
                response["scope"] = token.scopes.joinToString(" ")
                response["client_id"] = token.delegatedSession.client.fqdn
            }

            is IRefreshToken -> {
                response["token_type"] = "refresh"
                response["client_id"] = token.delegatedSession.client.fqdn
            }

            is IServiceToken -> {
                response["token_type"] = "service"
                response["scope"] = token.scopes.joinToString(" ")
                (token.subject as? IClient)?.let { response["client_id"] = it.fqdn }
            }
        }

        ctx.json(response)
    }

    // ---- Private helpers ----

    private fun buildRedirectUrl(baseUri: String, params: Map<String, String>): String {
        val queryString = params.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        val separator = if (baseUri.contains('?')) "&" else "?"
        return "$baseUri$separator$queryString"
    }
}
