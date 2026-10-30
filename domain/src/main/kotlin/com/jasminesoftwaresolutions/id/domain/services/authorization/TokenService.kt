package com.jasminesoftwaresolutions.id.domain.services.authorization

import com.google.gson.*
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IAccessToken
import com.jasminesoftwaresolutions.id.domain.models.authorization.IIDToken
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRefreshToken
import com.jasminesoftwaresolutions.id.domain.models.authorization.IServiceToken
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.accounts.IJWTService
import java.lang.reflect.Type
import java.time.Instant
import java.util.*
import kotlin.time.Duration

interface ITokenService<
        TAccessToken : IAccessToken,
        TRefreshToken : IRefreshToken,
        TServiceToken : IServiceToken,
        TIDToken : IIDToken> {

    // Delegated Sessions
    // ------------------

    fun generateDelegatedSessionAccessToken(
        delegatedSession: IDelegatedSession,
    ): TAccessToken?

    fun generateDelegatedSessionRefreshToken(
        delegatedSession: IDelegatedSession
    ): TRefreshToken?

    fun renewDelegatedSessionAccessToken(
        refreshToken: TRefreshToken
    ): TAccessToken?

    // Service Sessions
    // ------------------

    fun generateServiceSessionAccessToken(
        client: IClient,
        scopes: Set<String>?
    ): TServiceToken?

    // OIDC
    // ------------------

    fun generateIDToken(
        delegatedSession: IDelegatedSession,
        claims: Map<String, Any?>,
    ): TIDToken?

    // Generic
    // ------------------

    fun decode(
        token: String
    ): Any?
}

open class JWTTokenService(
    private val jwtService: IJWTService,
    private val accountRepository: IAccountRepository,
    private val clientRepository: IClientRepository,
    private val delegatedSessionRepository: IDelegatedSessionRepository,
    private val sessionRepository: ISessionRepository,
    val accessTokenLifetime: Duration,
    val refreshTokenLifetime: Duration
) : ITokenService<IAccessToken, IRefreshToken, IServiceToken, IIDToken> {

    private companion object {
        private const val CLAIM_TOKEN_TYPE = "https://id.jasmine.software/token_type"
        private const val CLAIM_DELEGATED_SESSION_ID = "https://id.jasmine.software/delegated_session_id"
        private const val CLAIM_SESSION_ID = "https://id.jasmine.software/session_id"

        private const val TYPE_DELEGATED_ACCESS = "delegated_session_access"
        private const val TYPE_DELEGATED_REFRESH = "delegated_session_refresh"
        private const val TYPE_SERVICE = "service_session_access"
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantAdapter())
        .registerTypeAdapter(IAccessToken::class.java, AccessTokenSerializer())
        .registerTypeAdapter(IRefreshToken::class.java, RefreshTokenSerializer())
        .registerTypeAdapter(IServiceToken::class.java, ServiceTokenSerializer())
        .disableHtmlEscaping()
        .create()

    // ----- Serializers -----

    /**
     * Serializes [Instant] as epoch seconds to avoid reflective field access
     * that fails under Java 26's strong module encapsulation of java.time.
     */
    private inner class InstantAdapter : JsonSerializer<Instant>, JsonDeserializer<Instant> {
        override fun serialize(src: Instant?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement =
            JsonPrimitive(src?.epochSecond)

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Instant? =
            json?.asJsonPrimitive?.asLong?.let { Instant.ofEpochSecond(it) }
    }

    private fun List<String>.serialize(): JsonArray = JsonArray().apply {
        for (str in this@serialize)
            add(str)
    }

    private inner class AccessTokenSerializer : JsonSerializer<IAccessToken>, JsonDeserializer<IAccessToken> {
        override fun serialize(src: IAccessToken?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
            if (src == null) return null
            return with(src) {
                JsonObject().apply {
                    addProperty("jti", id.toString())
                    addProperty("iat", issuedAt.epochSecond)
                    addProperty("exp", expiresAt.epochSecond)
                    addProperty("nbf", notBefore.epochSecond)

                    addProperty("sub", subject.id.toString())
                    add("aud", audience.serialize())

                    addProperty("scope", scopes.joinToString(" "))

                    addProperty(CLAIM_TOKEN_TYPE, TYPE_DELEGATED_ACCESS)
                    addProperty(CLAIM_DELEGATED_SESSION_ID, delegatedSession.id.toString())
                    addProperty(CLAIM_SESSION_ID, session.id.toString())
                }
            }
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): IAccessToken? {
            if (json == null || json.isJsonNull || !json.isJsonObject) return null
            val obj = json.asJsonObject

            return object : IAccessToken {
                override val id: UUID = UUID.fromString(obj["jti"].asString)

                override val issuedAt = Instant.ofEpochSecond(obj["iat"].asLong)
                override val expiresAt = Instant.ofEpochSecond(obj["exp"].asLong)
                override val notBefore = Instant.ofEpochSecond(obj["nbf"].asLong)

                override val subject by lazy {
                    accountRepository.findById(UUID.fromString(obj["sub"].asString))
                        ?: throw IllegalArgumentException("Invalid subject")
                }

                override val audience = obj["aud"].asJsonArray.map { it.asString }

                override val scopes: Set<String> =
                    obj["scope"].asString.split(" ").filter { it.isNotEmpty() }.toSet()

                override val delegatedSession by lazy {
                    delegatedSessionRepository.findById(UUID.fromString(obj[CLAIM_DELEGATED_SESSION_ID].asString))
                        ?: throw IllegalArgumentException("Invalid delegated session")
                }

                override val session by lazy {
                    sessionRepository.findById(UUID.fromString(obj[CLAIM_SESSION_ID].asString))
                        ?: throw IllegalArgumentException("Invalid session")
                }

                override val token: String =
                    jwtService.encode(gson.toJsonTree(this, IAccessToken::class.java).asJsonObject)
            }
        }
    }

    private inner class RefreshTokenSerializer : JsonSerializer<IRefreshToken>, JsonDeserializer<IRefreshToken> {
        override fun serialize(
            src: IRefreshToken?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement? {
            if (src == null) return null
            return with(src) {
                JsonObject().apply {
                    addProperty("jti", id.toString())
                    addProperty("iat", issuedAt.epochSecond)
                    addProperty("exp", expiresAt.epochSecond)
                    addProperty("nbf", notBefore.epochSecond)

                    addProperty("sub", subject.id.toString())
                    add("aud", audience.serialize())

                    addProperty(CLAIM_TOKEN_TYPE, TYPE_DELEGATED_REFRESH)
                    addProperty(CLAIM_DELEGATED_SESSION_ID, delegatedSession.id.toString())
                    addProperty(CLAIM_SESSION_ID, session.id.toString())
                }
            }
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): IRefreshToken? {
            if (json == null || json.isJsonNull || !json.isJsonObject) return null
            val obj = json.asJsonObject

            return object : IRefreshToken {
                override val id: UUID = UUID.fromString(obj["jti"].asString)

                override val issuedAt = Instant.ofEpochSecond(obj["iat"].asLong)
                override val expiresAt = Instant.ofEpochSecond(obj["exp"].asLong)
                override val notBefore = Instant.ofEpochSecond(obj["nbf"].asLong)

                override val subject by lazy {
                    accountRepository.findById(UUID.fromString(obj["sub"].asString))
                        ?: throw IllegalArgumentException("Invalid subject")
                }

                override val audience = obj["aud"].asJsonArray.map { it.asString }

                override val scopes: Set<String> = emptySet()

                override val delegatedSession by lazy {
                    delegatedSessionRepository.findById(UUID.fromString(obj[CLAIM_DELEGATED_SESSION_ID].asString))
                        ?: throw IllegalArgumentException("Invalid delegated session")
                }

                override val session by lazy {
                    sessionRepository.findById(UUID.fromString(obj[CLAIM_SESSION_ID].asString))
                        ?: throw IllegalArgumentException("Invalid session")
                }

                override val token: String =
                    jwtService.encode(gson.toJsonTree(this, IRefreshToken::class.java).asJsonObject)
            }
        }
    }

    private inner class ServiceTokenSerializer : JsonSerializer<IServiceToken>, JsonDeserializer<IServiceToken> {
        override fun serialize(
            src: IServiceToken?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement? {
            if (src == null) return null
            return with(src) {
                JsonObject().apply {
                    addProperty("jti", id.toString())
                    addProperty("iat", issuedAt.epochSecond)
                    addProperty("exp", expiresAt.epochSecond)
                    addProperty("nbf", notBefore.epochSecond)

                    addProperty("sub", "https://${(subject as IClient).fqdn}/")
                    add("aud", audience.serialize())

                    addProperty("scope", scopes.joinToString(" "))

                    addProperty(CLAIM_TOKEN_TYPE, TYPE_SERVICE)
                }
            }
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): IServiceToken? {
            if (json == null || json.isJsonNull || !json.isJsonObject) return null
            val obj = json.asJsonObject

            return object : IServiceToken {
                override val id: UUID = UUID.fromString(obj["jti"].asString)

                override val issuedAt = Instant.ofEpochSecond(obj["iat"].asLong)
                override val expiresAt = Instant.ofEpochSecond(obj["exp"].asLong)
                override val notBefore = Instant.ofEpochSecond(obj["nbf"].asLong)

                override val subject by lazy {
                    val sub = obj["sub"].asString
                    val fqdn = sub.removePrefix("https://").removeSuffix("/")
                    clientRepository.findByFqdn(fqdn)
                        ?: throw IllegalArgumentException("Invalid subject")
                }

                override val audience = obj["aud"].asJsonArray.map { it.asString }

                override val scopes: Set<String> =
                    if (obj.has("scope"))
                        obj["scope"].asString.split(" ").filter { it.isNotEmpty() }.toSet()
                    else
                        emptySet()

                override val token: String =
                    jwtService.encode(gson.toJsonTree(this, IServiceToken::class.java).asJsonObject)
            }
        }
    }

    // ----- Token generation -----

    override fun generateDelegatedSessionAccessToken(
        delegatedSession: IDelegatedSession
    ): IAccessToken? {
        if (delegatedSession.session.expiresAt <= Instant.now())
            return null

        if (delegatedSession.expiresAt <= Instant.now())
            return null

        val now = Instant.now()

        return object : IAccessToken {
            override val id = UUID.randomUUID()

            override val issuedAt = now
            override val expiresAt = now.plusMillis(accessTokenLifetime.inWholeMilliseconds)
            override val notBefore = issuedAt

            override val subject = delegatedSession.session.account
            override val audience = listOf("https://${delegatedSession.client.fqdn}/")

            override val scopes: Set<String> = delegatedSession.scopes.map { it.id }.toSet()

            override val delegatedSession: IDelegatedSession = delegatedSession
            override val session = delegatedSession.session

            override val token: String = jwtService.encode(gson.toJsonTree(this, IAccessToken::class.java).asJsonObject)
        }
    }

    override fun generateDelegatedSessionRefreshToken(
        delegatedSession: IDelegatedSession
    ): IRefreshToken? {
        if (delegatedSession.session.expiresAt <= Instant.now())
            return null

        if (delegatedSession.expiresAt <= Instant.now())
            return null

        val now = Instant.now()

        return object : IRefreshToken {
            override val id = UUID.randomUUID()

            override val issuedAt = now
            override val expiresAt = minOf(
                now.plusMillis(refreshTokenLifetime.inWholeMilliseconds),
                delegatedSession.session.expiresAt
            )
            override val notBefore = issuedAt

            override val subject = delegatedSession.session.account
            override val audience = listOf("https://${delegatedSession.client.fqdn}/")

            override val scopes: Set<String> = emptySet()

            override val delegatedSession = delegatedSession
            override val session = delegatedSession.session

            override val token: String =
                jwtService.encode(gson.toJsonTree(this, IRefreshToken::class.java).asJsonObject)
        }
    }

    override fun renewDelegatedSessionAccessToken(refreshToken: IRefreshToken): IAccessToken? {
        if (Instant.now() < refreshToken.notBefore || Instant.now() > refreshToken.expiresAt)
            return null

        if (refreshToken.session.expiresAt <= Instant.now())
            return null

        // Add a 1-second grace window because JWT iat is truncated to seconds while
        // ds.refreshedAt carries millisecond (or finer) precision. Without the window,
        // a refresh that happens within the same second as token issuance falsely
        // marks the token as superseded.
        if (refreshToken.delegatedSession.refreshedAt != null &&
            refreshToken.issuedAt.plusSeconds(1) < refreshToken.delegatedSession.refreshedAt
        )
            return null

        delegatedSessionRepository.update(refreshToken.delegatedSession) {
            refreshedAt = Instant.now()
        }

        return generateDelegatedSessionAccessToken(refreshToken.delegatedSession)
    }

    override fun generateServiceSessionAccessToken(
        client: IClient,
        scopes: Set<String>?
    ): IServiceToken? {
        val now = Instant.now()

        return object : IServiceToken {
            override val id = UUID.randomUUID()

            override val issuedAt = now
            override val expiresAt = now.plusMillis(accessTokenLifetime.inWholeMilliseconds)
            override val notBefore = issuedAt

            override val subject = client
            override val audience = listOf("https://${client.fqdn}/", "https://witchwood.jasmine.software/")

            override val scopes: Set<String> = scopes ?: emptySet()

            override val token: String =
                jwtService.encode(gson.toJsonTree(this, IServiceToken::class.java).asJsonObject)
        }
    }

    // ----- ID Token -----

    override fun generateIDToken(
        delegatedSession: IDelegatedSession,
        claims: Map<String, Any?>,
    ): IIDToken? {
        if (delegatedSession.session.expiresAt <= Instant.now())
            return null
        if (delegatedSession.expiresAt <= Instant.now())
            return null

        val now = Instant.now()
        val account = delegatedSession.session.account

        val claimsObj = JsonObject().apply {
            addProperty("iss", "https://id.jasmine.software/")
            addProperty("sub", account.id.toString())
            addProperty("aud", "https://${delegatedSession.client.fqdn}/")
            addProperty("iat", now.epochSecond)
            addProperty("exp", now.plusMillis(accessTokenLifetime.inWholeMilliseconds).epochSecond)
            delegatedSession.nonce?.let { addProperty("nonce", it) }

            for ((key, value) in claims) {
                when (value) {
                    null -> add(key, null)
                    is String -> addProperty(key, value)
                    is Number -> addProperty(key, value)
                    is Boolean -> addProperty(key, value)
                    else -> addProperty(key, value.toString())
                }
            }
        }

        return object : IIDToken {
            override val id: UUID = UUID.randomUUID()
            override val token: String = jwtService.encode(claimsObj)
            override val issuedAt: Instant = now
            override val expiresAt: Instant = now.plusMillis(accessTokenLifetime.inWholeMilliseconds)
            override val subject: String = account.id.toString()
            override val audience: String = "https://${delegatedSession.client.fqdn}/"
            override val issuer: String = "https://id.jasmine.software/"
            override val nonce: String? = delegatedSession.nonce
            override val claims: Map<String, Any?> = claims
        }
    }

    // ----- Decode -----

    override fun decode(token: String): Any? {
        val decoded = jwtService.decode(token)
        val type = decoded[CLAIM_TOKEN_TYPE]?.asString ?: return null

        return when (type) {
            TYPE_DELEGATED_ACCESS -> gson.fromJson(decoded, IAccessToken::class.java)
            TYPE_DELEGATED_REFRESH -> gson.fromJson(decoded, IRefreshToken::class.java)
            TYPE_SERVICE -> gson.fromJson(decoded, IServiceToken::class.java)
            else -> null
        }
    }
}
