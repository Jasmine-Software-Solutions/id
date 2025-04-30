package app.sql.oauth2

import app.etc.Token
import app.etc.transformInstant
import app.etc.transformNullableInstant
import app.sql.account.Session
import app.sql.account.SessionsTable
import app.sql.client.Client
import app.sql.client.ClientRedirectUri
import app.sql.client.ClientRedirectUrisTable
import app.sql.client.ClientsTable
import app.sql.tenant.Tenant
import app.sql.tenant.TenantsTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.time.Instant
import java.util.*

object SessionAccessTokensTable : UUIDTable("session_access_tokens") {
    val session = reference("session", SessionsTable, onDelete = ReferenceOption.CASCADE)
    val client = reference("client", ClientsTable, onDelete = ReferenceOption.CASCADE)
    val redirectUri = reference("redirect_uri", ClientRedirectUrisTable, onDelete = ReferenceOption.CASCADE)

    val issuedAt = long("issued_at").clientDefault { System.currentTimeMillis() }
    val accessToken = varchar("access_token", 64).uniqueIndex()
    val refreshToken = varchar("refresh_token", 64).uniqueIndex().nullable()

    val scope = varchar("scope", 256).nullable()
    val tenant = reference("tenant", TenantsTable, onDelete = ReferenceOption.CASCADE)

    val lastRefreshed = long("last_refreshed").clientDefault { System.currentTimeMillis() }

    val authorizationCode = varchar("authorization_code", 64).nullable()
    val authorizationCodeExpiration = long("authorization_code_expiration").nullable()
}

@Suppress("unused")
class SessionAccessTokens(id: EntityID<UUID>) : UUIDEntity(id), OAuth2Authorized {
    companion object : UUIDEntityClass<SessionAccessTokens>(SessionAccessTokensTable)

    var session by Session referencedOn SessionAccessTokensTable.session
    var client by Client referencedOn SessionAccessTokensTable.client
    var redirectUri by ClientRedirectUri referencedOn SessionAccessTokensTable.redirectUri

    var issuedAt by SessionAccessTokensTable.issuedAt.transformInstant()
    var accessToken by SessionAccessTokensTable.accessToken
    var refreshToken by SessionAccessTokensTable.refreshToken

    var scope by SessionAccessTokensTable.scope
    var tenant by Tenant referencedOn SessionAccessTokensTable.tenant

    var lastRefreshed by SessionAccessTokensTable.lastRefreshed.transformInstant()

    var authorizationCode by SessionAccessTokensTable.authorizationCode
    var authorizationCodeExpiration by SessionAccessTokensTable.authorizationCodeExpiration.transformNullableInstant()

    override fun isAccessTokenActive(): Boolean {
        return session.isValid() && lastRefreshed.toEpochMilli() > System.currentTimeMillis() - 5 * 60 * 1000
    }

    fun isRefreshTokenActive(): Boolean {
        return session.isValid()
    }

    fun isAuthorizationCodeActive(): Boolean {
        return authorizationCodeExpiration?.isAfter(Instant.now()) ?: false
    }

    fun refreshAccessToken() {
        if (!isRefreshTokenActive())
            throw IllegalStateException("Refresh token is not active")

        lastRefreshed = Instant.now()
        accessToken = Token()
    }

    override fun authorizedFor(scope: String, tenant: UUID?): Boolean {
        if (tenant != null && this.tenant.id.value != tenant)
            return false

        val scopes = this.scope?.split(" ") ?: return true
        val hasScope = scopes.contains(scope)
        return hasScope
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SessionAccessTokens)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}