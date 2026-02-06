package app.application.oauth2

import app.infrastructure.etc.SecureToken
import app.infrastructure.models.account.Account
import app.infrastructure.models.account.Session
import app.infrastructure.models.account.SessionsTable
import app.infrastructure.models.account.isNullOrInvalid
import app.infrastructure.models.client.Client
import app.infrastructure.models.client.ClientRedirectUri
import app.infrastructure.models.oauth2.SessionAccessToken
import app.infrastructure.models.tenant.Tenant
import app.infrastructure.models.tenant.TenantAccountLinksTable
import app.infrastructure.models.tenant.TenantsTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

data class OAuth2AuthorizeCommand(
    val sessionToken: String?,
    val clientId: UUID,
    val redirectUri: String,
    val scope: String?,
    val state: String?,
    val tenantId: UUID?
)

interface OAuth2AuthorizeHandler {
    fun execute(command: OAuth2AuthorizeCommand): OAuth2AuthorizeResult
}

sealed class OAuth2AuthorizeResult {
    data class SelectTenant(val account: Account, val tenants: List<Tenant>) : OAuth2AuthorizeResult()
    data class Authorized(val redirectUrl: String) : OAuth2AuthorizeResult()

    object Unauthorized : OAuth2AuthorizeResult()
    object InvalidClient : OAuth2AuthorizeResult()
    object InvalidRedirectUri : OAuth2AuthorizeResult()
    object InvalidTenant : OAuth2AuthorizeResult()
}

class OAuth2AuthorizeService : OAuth2AuthorizeHandler {
    override fun execute(command: OAuth2AuthorizeCommand): OAuth2AuthorizeResult = transaction {
        val client = Client.findById(command.clientId)
        if (client == null) {
            return@transaction OAuth2AuthorizeResult.InvalidClient
        }

        val redirectUri: ClientRedirectUri = client.redirectUris.firstOrNull { it.uri == command.redirectUri }
            ?: return@transaction OAuth2AuthorizeResult.InvalidRedirectUri

        if (command.sessionToken == null) {
            return@transaction OAuth2AuthorizeResult.Unauthorized
        }

        val session = Session.find { SessionsTable.token eq command.sessionToken }.firstOrNull()
        if (session.isNullOrInvalid()) {
            return@transaction OAuth2AuthorizeResult.Unauthorized
        }

        session!!.accessedAt = Instant.now()
        session.account

        if (command.tenantId == null) {
            val linkedTenants = TenantAccountLinksTable.selectByAccount(session.account.id.value)
                .map { it[TenantAccountLinksTable.tenant] }
                .map { Tenant.findById(it)!! }

            val openboxTenants = Tenant.find { TenantsTable.openbox eq true }.toList()

            val tenants = if (session.account.systemAdmin) openboxTenants + linkedTenants else linkedTenants

            return@transaction OAuth2AuthorizeResult.SelectTenant(
                account = session.account,
                tenants = tenants
            )
        }

        val tenant = Tenant.findById(command.tenantId)
            ?: return@transaction OAuth2AuthorizeResult.InvalidTenant

        val linkedTenants = TenantAccountLinksTable
            .select { TenantAccountLinksTable.account eq session.account.id }
            .map { it[TenantAccountLinksTable.tenant].value }

        if (!session.account.systemAdmin && !linkedTenants.contains(tenant.id.value)) {
            return@transaction OAuth2AuthorizeResult.InvalidTenant
        }

        if (!client.automaticGrant) {
            throw UnsupportedOperationException("Explicit grant is not supported")
        }

        val tokens = SessionAccessToken.new {
            this.session = session
            this.client = client
            this.redirectUri = redirectUri

            this.accessToken = SecureToken()
            this.refreshToken = SecureToken()

            this.tenant = tenant
            this.scope = command.scope

            this.authorizationCode = SecureToken()
            this.authorizationCodeExpiration = Instant.now().plus(10, ChronoUnit.MINUTES)
        }

        val redirectUrl = "${redirectUri.uri}?code=${tokens.authorizationCode}${if (command.state != null) "&state=${command.state}" else ""}&tenant=${tenant.id.value}"
        return@transaction OAuth2AuthorizeResult.Authorized(redirectUrl)
    }
}