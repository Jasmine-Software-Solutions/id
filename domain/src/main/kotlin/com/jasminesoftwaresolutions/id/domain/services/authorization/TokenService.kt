package com.jasminesoftwaresolutions.id.domain.services.authorization

import com.google.gson.*
import com.jasminesoftwaresolutions.id.domain.models.*
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.tenant.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITeam
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
    private val organizationRepository: IOrganizationRepository<out IOrganization>,
    private val teamRepository: ITeamRepository<out ITeam>,
    private val assignmentRepository: IRoleAssignmentRepository<out IRoleAssignment>,
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
        private const val CLAIM_ATTACHMENT = "attachment"
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

    private fun generateAttachmentInfo(attachment: IAttachment) = JsonObject().apply {
        addProperty("type", attachment.type.name.lowercase())

        when (attachment) {
            is ITenantAttachment -> addProperty("tenant_id", attachment.tenant.id.toString())
            is IOrganizationAttachment -> addProperty("organization_id", attachment.organization.id.toString())
            is ITeamAttachment -> addProperty("team_id", attachment.team.id.toString())
        }
    }

    private fun generateRoleInfo(role: IRole) = JsonObject().apply {
        addProperty("id", role.id)
        addProperty("name", role.name)
        addProperty("description", role.description)
        add(CLAIM_ATTACHMENT, generateAttachmentInfo(role.attachment))
    }

    private fun generateRoleInfo(roles: Set<IRole>) = JsonArray().apply {
        for (role in roles)
            add(generateRoleInfo(role))
    }

    private fun deserializeAttachmentInfo(obj: JsonObject): IAttachment? {
        if (obj.has(CLAIM_ATTACHMENT)) {
            val attachment = obj[CLAIM_ATTACHMENT].asJsonObject
            return deserializeAttachmentInfoFromClaim(attachment)
        }

        val level = obj.get("level")?.asString
        return when (level) {
            AttachmentType.GLOBAL.name.lowercase() -> PlatformAttachment
            AttachmentType.TENANT.name.lowercase() -> obj.get("tenant_id")?.asString
                ?.let { tenantRepository.findById(UUID.fromString(it)) }
                ?.let { TenantAttachment(it) }
            AttachmentType.ORGANIZATION.name.lowercase() -> obj.get("organization_id")?.asString
                ?.let { organizationRepository.findById(UUID.fromString(it)) }
                ?.let { OrganizationAttachment(it) }
            AttachmentType.TEAM.name.lowercase() -> obj.get("team_id")?.asString
                ?.let { teamRepository.findById(UUID.fromString(it)) }
                ?.let { TeamAttachment(it) }
            else -> obj.get("tenant_id")?.asString
                ?.let { tenantRepository.findById(UUID.fromString(it)) }
                ?.let { TenantAttachment(it) }
                ?: PlatformAttachment
        }
    }

    private fun deserializeAttachmentInfoFromClaim(obj: JsonObject): IAttachment? {
        return when (obj.get("type")?.asString) {
            AttachmentType.GLOBAL.name.lowercase() -> PlatformAttachment
            AttachmentType.TENANT.name.lowercase() -> obj.get("tenant_id")?.asString
                ?.let { tenantRepository.findById(UUID.fromString(it)) }
                ?.let { TenantAttachment(it) }
            AttachmentType.ORGANIZATION.name.lowercase() -> obj.get("organization_id")?.asString
                ?.let { organizationRepository.findById(UUID.fromString(it)) }
                ?.let { OrganizationAttachment(it) }
            AttachmentType.TEAM.name.lowercase() -> obj.get("team_id")?.asString
                ?.let { teamRepository.findById(UUID.fromString(it)) }
                ?.let { TeamAttachment(it) }
            else -> null
        }
    }

    private fun deserializeRoleInfo(obj: JsonObject): IRole? {
        val id = obj["id"].asString
        val name = obj.get("name")?.asString ?: id
        val description = obj.get("description")?.asString ?: ""
        val attachment = deserializeAttachmentInfo(obj) ?: return null

        return when (attachment) {
            is ITenantAttachment -> TenantRole(attachment.tenant, id, name, description)
            is IOrganizationAttachment -> OrganizationRole(attachment.organization, id, name, description)
            is ITeamAttachment -> TeamRole(attachment.team, id, name, description)
            else -> PlatformRole(id, name, description)
        }
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

                    obj[CLAIM_ROLES].asJsonArray
                        .mapNotNull { deserializeRoleInfo(it.asJsonObject) }
                        .toSet()
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

                    obj[CLAIM_ROLES].asJsonArray
                        .mapNotNull { deserializeRoleInfo(it.asJsonObject) }
                        .toSet()
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

                for (role in assignmentRepository.findByAccount(session.account).map { it.role })
                    roles.add(role)

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
            override val roles: Set<IRole> = assignmentRepository.findByClient(client).map { it.role }.toSet()

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
