package app.infrastructure.models.oauth2

import app.infrastructure.etc.transformInstant
import app.infrastructure.etc.transformNullableInstant
import app.infrastructure.models.account.Session
import app.infrastructure.models.account.SessionsTable
import app.infrastructure.models.client.Client
import app.infrastructure.models.client.ClientRedirectUri
import app.infrastructure.models.client.ClientRedirectUrisTable
import app.infrastructure.models.client.ClientsTable
import app.infrastructure.models.tenant.Tenant
import app.infrastructure.models.tenant.TenantsTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
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
class SessionAccessToken(id: EntityID<UUID>) : UUIDEntity(id), AccessToken {
    companion object : UUIDEntityClass<SessionAccessToken>(SessionAccessTokensTable)

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

    override fun equals(other: Any?): Boolean {
        if (other !is SessionAccessToken)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}