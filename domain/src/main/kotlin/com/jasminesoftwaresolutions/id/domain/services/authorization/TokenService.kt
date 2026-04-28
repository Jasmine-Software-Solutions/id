package com.jasminesoftwaresolutions.id.domain.services.authorization

import com.google.gson.*
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.accounts.IJWTService
import java.lang.reflect.Type
import java.time.Instant
import java.util.*
import kotlin.time.Duration

interface ITokenService<
        TDelegatedSessionAccessToken : IDelegatedSessionAccessToken,
        TDelegatedSessionRefreshToken : IDelegatedSessionRefreshToken,
        TServiceSessionAccessToken : IServiceSessionAccessToken> {
    // Delegated Sessions
    // ------------------

    fun generateDelegatedSessionAccessToken(
        delegatedSession: IDelegatedSession,
    ): TDelegatedSessionAccessToken?

    fun generateDelegatedSessionRefreshToken(
        delegatedSession: IDelegatedSession
    ): TDelegatedSessionRefreshToken?

    fun renewDelegatedSessionAccessToken(
        refreshToken: TDelegatedSessionRefreshToken
    ): TDelegatedSessionAccessToken?

    // Service Sessions
    // ------------------

    fun generateServiceSessionAccessToken(
        client: IClient,
        tenant: ITenant?,
        scopes: Set<IScope>?
    ): TServiceSessionAccessToken?

    // Generic
    // ------------------

    fun decode(
        token: String
    ): Any?
}

open class JWTTokenService (
    private val jwtService: IJWTService,
    private val accountRepository: IAccountRepository<out IAccount>,
    private val clientRepository: IClientRepository<out IClient>,
    private val tenantRepository: ITenantRepository<out ITenant>,
    private val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
    private val delegatedSessionRepository: IDelegatedSessionRepository<IDelegatedSession>,
    private val sessionRepository: ISessionRepository<out ISession>,
    private val scopeRepository: IScopeRepository<out IRegisteredScope>,
    private val scopeRegistry: IScopeRegistry<out IScope>,
    val accessTokenLifetime: Duration,
    val refreshTokenLifetime: Duration
) : ITokenService<IDelegatedSessionAccessToken, IDelegatedSessionRefreshToken, IServiceSessionAccessToken> {
    private companion object {
        private const val CLAIM_TOKEN_TYPE = "https://id.jasmine.software/token_type"
        private const val CLAIM_DELEGATED_SESSION_ID = "https://id.jasmine.software/delegated_session_id"
        private const val CLAIM_SESSION_ID = "https://id.jasmine.software/session_id"
        private const val CLAIM_TENANT = "https://id.jasmine.software/tenant"
        private const val CLAIM_ROLES = "https://id.jasmine.software/roles"
        private const val TYPE_DELEGATED_ACCESS = "delegated_session_access"
        private const val TYPE_DELEGATED_REFRESH = "delegated_session_refresh"
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(IDelegatedSessionAccessToken::class.java, DelegatedSessionAccessTokenSerializer())
        .registerTypeAdapter(IDelegatedSessionRefreshToken::class.java, DelegatedSessionRefreshTokenSerializer())
        .disableHtmlEscaping()
        .create()

    private fun generateTenantInfo(tenant: ITenant) = JsonObject().apply {
        addProperty("id", tenant.id.toString())
        addProperty("name", tenant.name)
    }

    private fun generateRoleInfo(role: IRole) = JsonObject().apply {
        addProperty("id", role.id)
        addProperty("description", role.name)

        if (role is ITenantRole)
            addProperty("tenant_id", role.tenant.id.toString())
    }

    private fun generateRoleInfo(roles: Set<IRole>) = JsonArray().apply {
        for (role in roles)
            add(generateRoleInfo(role))
    }

    private inner class DelegatedSessionAccessTokenSerializer : JsonSerializer<IDelegatedSessionAccessToken>, JsonDeserializer<IDelegatedSessionAccessToken> {
        override fun serialize(src: IDelegatedSessionAccessToken?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
            if (src == null)
                return null

            return with (src) {
                JsonObject().apply {
                    addProperty("jti", id.toString())

                    addProperty("iat", issuedAt.epochSecond)
                    addProperty("exp", expiresAt.epochSecond)
                    addProperty("nbf", notBefore.epochSecond)

                    addProperty("sub", subject.id.toString())

                    addProperty("aud", audience.id.toString())
                    addProperty("scope", scopes.joinToString(" ") { it.id })
                    addProperty(CLAIM_TOKEN_TYPE, TYPE_DELEGATED_ACCESS)

                    addProperty(CLAIM_DELEGATED_SESSION_ID, delegatedSession.id.toString())
                    addProperty(CLAIM_SESSION_ID, session.id.toString())

                    if (tenant != null)
                        add(CLAIM_TENANT, generateTenantInfo(tenant!!))

                    if (scopes.any { it.id == PermissionsScope.id })
                        add(CLAIM_ROLES, generateRoleInfo(roles!!))
                }
            }
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): IDelegatedSessionAccessToken? {
            if (json == null || json.isJsonNull || json.isJsonObject == false)
                return null

            val obj = json.asJsonObject

            return object : IDelegatedSessionAccessToken {
                override val id: UUID = UUID.fromString(obj["jti"].asString)

                override val issuedAt = Instant.ofEpochSecond(obj["iat"].asLong)
                override val expiresAt = Instant.ofEpochSecond(obj["exp"].asLong)
                override val notBefore = Instant.ofEpochSecond(obj["nbf"].asLong)

                override val subject by lazy {
                    accountRepository.findById(UUID.fromString(obj["sub"].asString))
                        ?: throw IllegalArgumentException("Invalid subject")
                }

                override val audience by lazy {
                    clientRepository.findById(UUID.fromString(obj["aud"].asString))
                        ?: throw IllegalArgumentException("Invalid audience")
                }

                override val scopes by lazy {
                    obj["scope"].asString.split(" ").mapNotNull {
                        scopeRegistry.findById(it) ?: scopeRepository.findById(it)
                    }.toSet()
                }

                override val delegatedSession by lazy {
                    delegatedSessionRepository.findById(UUID.fromString(obj["delegated_session_id"].asString))
                        ?: throw IllegalArgumentException("Invalid delegated session")
                }

                override val session by lazy {
                    sessionRepository.findById(UUID.fromString(obj["session_id"].asString))
                        ?: throw IllegalArgumentException("Invalid session")
                }

                override val tenant by lazy {
                    if (obj.has(CLAIM_TENANT))
                        tenantRepository.findById(UUID.fromString(obj[CLAIM_TENANT].asJsonObject["id"].asString))
                    else null
                }

                override val roles: Set<IRole>? by lazy {
                    if (!obj.has(CLAIM_ROLES))
                        return@lazy null

                    obj[CLAIM_ROLES].asJsonArray.mapNotNull {
                        val obj = it.asJsonObject
                        val id = obj["id"].asString
                        val tenantId = obj.get("tenant_id")?.asString

                        if (tenantId == null)
                            return@mapNotNull when (id) {
                                PlatformAdministrator.id -> PlatformAdministrator
                                PlatformTrustedClient.id -> PlatformTrustedClient
                                PlatformClient.id -> PlatformClient
                                PlatformMember.id -> PlatformMember
                                else -> null
                            }

                        val tenant = tenantRepository.findById(UUID.fromString(tenantId))!!

                        return@mapNotNull when (id) {
                            TenantAdministrator.id -> TenantAdministrator(tenant)
                            TenantMember.id -> TenantMember(tenant)
                            else -> null
                        }
                    }.toSet()
                }

                override val token: String = jwtService.encode(gson.toJsonTree(this).asJsonObject)
            }
        }
    }

    private inner class DelegatedSessionRefreshTokenSerializer : JsonSerializer<IDelegatedSessionRefreshToken>, JsonDeserializer<IDelegatedSessionRefreshToken> {
        override fun serialize(src: IDelegatedSessionRefreshToken?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
            if (src == null)
                return null

            return with (src) {
                JsonObject().apply {
                    addProperty("jti", id.toString())

                    addProperty("iat", issuedAt.epochSecond)
                    addProperty("exp", expiresAt.epochSecond)
                    addProperty("nbf", notBefore.epochSecond)

                    addProperty("sub", subject.id.toString())

                    addProperty("aud", audience.id.toString())
                    addProperty(CLAIM_TOKEN_TYPE, TYPE_DELEGATED_REFRESH)

                    addProperty(CLAIM_DELEGATED_SESSION_ID, delegatedSession.id.toString())
                    addProperty(CLAIM_SESSION_ID, session.id.toString())

                    if (tenant != null)
                        add(CLAIM_TENANT, generateTenantInfo(tenant!!))
                }
            }
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): IDelegatedSessionRefreshToken? {
            if (json == null || json.isJsonNull || json.isJsonObject == false)
                return null

            val obj = json.asJsonObject

            return object : IDelegatedSessionRefreshToken {
                override val id: UUID = UUID.fromString(obj["jti"].asString)

                override val issuedAt = Instant.ofEpochSecond(obj["iat"].asLong)
                override val expiresAt = Instant.ofEpochSecond(obj["exp"].asLong)
                override val notBefore = Instant.ofEpochSecond(obj["nbf"].asLong)

                override val subject by lazy {
                    accountRepository.findById(UUID.fromString(obj["sub"].asString))
                        ?: throw IllegalArgumentException("Invalid subject")
                }

                override val audience by lazy {
                    clientRepository.findById(UUID.fromString(obj["aud"].asString))
                        ?: throw IllegalArgumentException("Invalid audience")
                }

                override val delegatedSession by lazy {
                    delegatedSessionRepository.findById(UUID.fromString(obj["delegated_session_id"].asString))
                        ?: throw IllegalArgumentException("Invalid delegated session")
                }

                override val session by lazy {
                    sessionRepository.findById(UUID.fromString(obj["session_id"].asString))
                        ?: throw IllegalArgumentException("Invalid session")
                }

                override val tenant by lazy {
                    if (obj.has(CLAIM_TENANT))
                        tenantRepository.findById(UUID.fromString(obj[CLAIM_TENANT].asJsonObject["id"].asString))
                    else null
                }

                override val token: String = jwtService.encode(gson.toJsonTree(this).asJsonObject)
            }
        }
    }

    private inner class ServiceSessionAccessTokenSerializer : JsonSerializer<IServiceSessionAccessToken>, JsonDeserializer<IServiceSessionAccessToken> {
        override fun serialize(src: IServiceSessionAccessToken?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
            if (src == null)
                return null

            return with (src) {
                JsonObject().apply {
                    addProperty("jti", id.toString())

                    addProperty("iat", issuedAt.epochSecond)
                    addProperty("exp", expiresAt.epochSecond)
                    addProperty("nbf", notBefore.epochSecond)

                    addProperty("sub", subject.id.toString())

                    addProperty("aud", audience.id.toString())

                    if (scopes != null)
                        addProperty("scope", scopes!!.joinToString(" ") { it.id })

                    addProperty(CLAIM_TOKEN_TYPE, TYPE_DELEGATED_ACCESS)

                    if (tenant != null)
                        add(CLAIM_TENANT, generateTenantInfo(tenant!!))

                    if (scopes == null || scopes!!.any { it.id == PermissionsScope.id })
                        add(CLAIM_ROLES, generateRoleInfo(roles!!))
                }
            }
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): IServiceSessionAccessToken? {
            if (json == null || json.isJsonNull || json.isJsonObject == false)
                return null

            val obj = json.asJsonObject

            return object : IServiceSessionAccessToken {
                override val id: UUID = UUID.fromString(obj["jti"].asString)

                override val issuedAt = Instant.ofEpochSecond(obj["iat"].asLong)
                override val expiresAt = Instant.ofEpochSecond(obj["exp"].asLong)
                override val notBefore = Instant.ofEpochSecond(obj["nbf"].asLong)

                override val subject by lazy {
                    accountRepository.findById(UUID.fromString(obj["sub"].asString))
                        ?: throw IllegalArgumentException("Invalid subject")
                }

                override val audience by lazy {
                    clientRepository.findById(UUID.fromString(obj["aud"].asString))
                        ?: throw IllegalArgumentException("Invalid audience")
                }

                override val tenant by lazy {
                    if (obj.has(CLAIM_TENANT))
                        tenantRepository.findById(UUID.fromString(obj[CLAIM_TENANT].asJsonObject["id"].asString))
                    else null
                }

                override val scopes by lazy {
                    obj["scope"].asString.split(" ").mapNotNull {
                        scopeRegistry.findById(it) ?: scopeRepository.findById(it)
                    }.toSet()
                }

                override val roles: Set<IRole>? by lazy {
                    if (!obj.has(CLAIM_ROLES))
                        return@lazy null

                    obj[CLAIM_ROLES].asJsonArray.mapNotNull {
                        val obj = it.asJsonObject
                        val id = obj["id"].asString

                        return@mapNotNull when (id) {
                            PlatformAdministrator.id -> PlatformAdministrator
                            PlatformTrustedClient.id -> PlatformTrustedClient
                            PlatformClient.id -> PlatformClient
                            PlatformMember.id -> PlatformMember
                            else -> null
                        }
                    }.toSet()
                }

                override val token: String = jwtService.encode(gson.toJsonTree(this).asJsonObject)
            }
        }
    }

    override fun generateDelegatedSessionAccessToken(
        delegatedSession: IDelegatedSession
    ): IDelegatedSessionAccessToken? {
        if (delegatedSession.session.expiresAt <= Instant.now())
            return null

        if (delegatedSession.expiresAt <= Instant.now())
            return null

        return object : IDelegatedSessionAccessToken {
            override val id = UUID.randomUUID()

            override val issuedAt = Instant.now()
            override val expiresAt = Instant.now().plusMillis(accessTokenLifetime.inWholeMilliseconds)
            override val notBefore = issuedAt

            override val subject = delegatedSession.session.account
            override val audience = delegatedSession.client

            override val scopes = delegatedSession.scopes

            override val delegatedSession: IDelegatedSession = delegatedSession
            override val session = delegatedSession.session
            override val tenant = delegatedSession.tenant

            override val roles: Set<IRole> by lazy {
                val roles = mutableSetOf<IRole>()

                for (role in session.account.roles)
                    roles.add(role)

                val membership = tenantMembershipRepository.findByAccount(session.account)
                    .firstOrNull { it.tenant == tenant }

                if (membership != null) {
                    for (role in membership.roles)
                        roles.add(role)
                }

                return@lazy roles
            }

            override val token: String = jwtService.encode(gson.toJsonTree(this).asJsonObject)
        }
    }

    override fun generateDelegatedSessionRefreshToken(
        delegatedSession: IDelegatedSession
    ): IDelegatedSessionRefreshToken? {
        if (delegatedSession.session.expiresAt <= Instant.now())
            return null

        if (delegatedSession.expiresAt <= Instant.now())
            return null

        return object : IDelegatedSessionRefreshToken {
            override val id = UUID.randomUUID()

            override val issuedAt = Instant.now()
            override val expiresAt = minOf(
                Instant.now().plusMillis(refreshTokenLifetime.inWholeMilliseconds),
                delegatedSession.session.expiresAt
            )
            override val notBefore = issuedAt

            override val subject = delegatedSession.session.account
            override val audience = delegatedSession.client

            override val delegatedSession = delegatedSession
            override val session = delegatedSession.session
            override val tenant = delegatedSession.tenant

            override val token: String = jwtService.encode(gson.toJsonTree(this).asJsonObject)
        }
    }

    override fun renewDelegatedSessionAccessToken(refreshToken: IDelegatedSessionRefreshToken): IDelegatedSessionAccessToken? {
        if (Instant.now() < refreshToken.notBefore || Instant.now() > refreshToken.expiresAt)
            return null

        if (refreshToken.session.expiresAt <= Instant.now())
            return null

        if (refreshToken.delegatedSession.refreshedAt != null && refreshToken.issuedAt < refreshToken.delegatedSession.refreshedAt)
            return null

        delegatedSessionRepository.update(refreshToken.delegatedSession) {
            refreshedAt = Instant.now()
        }

        return generateDelegatedSessionAccessToken(refreshToken.delegatedSession)
    }

    override fun generateServiceSessionAccessToken(
        client: IClient,
        tenant: ITenant?,
        scopes: Set<IScope>?
    ): IServiceSessionAccessToken? {
        return object : IServiceSessionAccessToken {
            override val id = UUID.randomUUID()

            override val issuedAt = Instant.now()
            override val expiresAt = Instant.now().plusMillis(accessTokenLifetime.inWholeMilliseconds)
            override val notBefore = issuedAt

            override val subject = client
            override val audience = client

            override val tenant = tenant
            override val scopes = scopes
            override val roles: Set<IRole> = client.roles

            override val token = jwtService.encode(gson.toJsonTree(this).asJsonObject)
        }
    }

    override fun decode(token: String): Any? {
        val decoded = jwtService.decode(token)
        if (decoded[CLAIM_TOKEN_TYPE].asString != TYPE_DELEGATED_ACCESS && decoded[CLAIM_TOKEN_TYPE].asString != TYPE_DELEGATED_REFRESH)
            return null

        if (decoded[CLAIM_TOKEN_TYPE].asString == TYPE_DELEGATED_ACCESS)
            return gson.fromJson(decoded, IDelegatedSessionAccessToken::class.java)

        if (decoded[CLAIM_TOKEN_TYPE].asString == TYPE_DELEGATED_REFRESH)
            return gson.fromJson(decoded, IDelegatedSessionRefreshToken::class.java)

        return null
    }
}
