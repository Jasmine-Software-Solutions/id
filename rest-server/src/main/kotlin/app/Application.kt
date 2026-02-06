package app

import app.application.account.GetAccountInfoService
import app.application.account.GetAccountLinksService
import app.application.accounts.GetAccountService
import app.application.accounts.ListAccountsService
import app.application.oauth2.OAuth2AuthorizationCodeTokenService
import app.application.oauth2.OAuth2ClientCredentialsTokenService
import app.application.oauth2.OAuth2IntrospectService
import app.application.oauth2.OAuth2RefreshTokenService
import app.application.tenant.GetTenantInfoService
import app.controllers.RestAccountController
import app.controllers.RestAccountsController
import app.controllers.RestTenantController
import app.controllers.oauth2.OAuth2IntrospectController
import app.controllers.oauth2.OAuth2TokenController
import app.infrastructure.models.account.AccountsTable
import app.infrastructure.models.account.ForgotPasswordCodesTable
import app.infrastructure.models.account.PasswordsTable
import app.infrastructure.models.account.SessionsTable
import app.infrastructure.models.audit.ClientAuditTable
import app.infrastructure.models.audit.GrantAuditTable
import app.infrastructure.models.audit.LoginAuditTable
import app.infrastructure.models.audit.SessionAuditTable
import app.infrastructure.models.client.ClientRedirectUrisTable
import app.infrastructure.models.client.ClientsTable
import app.infrastructure.models.oauth2.MachineAccessTokensTable
import app.infrastructure.models.oauth2.SessionAccessTokensTable
import app.infrastructure.models.tenant.TenantAccountLinksTable
import app.infrastructure.models.tenant.TenantsTable
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import io.javalin.community.routing.annotations.AnnotatedRouting
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class RestApplication {
    private val database: Database
    private val app: Javalin

    init {
        val dataSource = createDataSource()
        database = connectDatabase(dataSource)
        createTables()

        val javalin = createApp()
        app = javalin
    }

    fun start() {
        app.start(Env.PORT)
    }

    fun stop() {
        app.stop()
    }

    private fun createDataSource(): HikariDataSource =
        HikariDataSource().apply {
            maximumPoolSize = Env.DATABASE_POOL_SIZE
            driverClassName = Env.DATABASE_DRIVER
            jdbcUrl = Env.DATABASE_URL
            username = Env.DATABASE_USERNAME
            password = Env.DATABASE_PASSWORD
            isAutoCommit = false
        }

    private fun connectDatabase(dataSource: HikariDataSource): Database =
        Database.connect(dataSource, databaseConfig = DatabaseConfig {
            keepLoadedReferencesOutOfTransaction = true
        })

    private fun createTables() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                AccountsTable,
                PasswordsTable,
                SessionsTable,

                ForgotPasswordCodesTable,

                LoginAuditTable,
                SessionAuditTable,
                GrantAuditTable,
                ClientAuditTable,

                TenantsTable,
                TenantAccountLinksTable,

                ClientsTable,
                ClientRedirectUrisTable,

                SessionAccessTokensTable,
                MachineAccessTokensTable
            )
        }
    }

    private fun createApp(): Javalin =
        Javalin.create { config ->
            config.bundledPlugins.enableCors { cors ->
                cors.addRule {
                    it.anyHost()
                }
            }

            config.router.mount(AnnotatedRouting) { routing ->
                val getAccountInfo = GetAccountInfoService()
                val getAccountLinks = GetAccountLinksService()
                val listAccounts = ListAccountsService()
                val getAccount = GetAccountService()
                val getTenantInfo = GetTenantInfoService()
                val authorizationCodeToken = OAuth2AuthorizationCodeTokenService()
                val refreshTokenService = OAuth2RefreshTokenService()
                val clientCredentialsToken = OAuth2ClientCredentialsTokenService()
                val introspectService = OAuth2IntrospectService()

                val restAccountController = RestAccountController(getAccountInfo, getAccountLinks)
                val restAccountsController = RestAccountsController(listAccounts, getAccount)
                val restTenantController = RestTenantController(getTenantInfo)
                val oauth2TokenController = OAuth2TokenController(
                    authorizationCodeToken,
                    refreshTokenService,
                    clientCredentialsToken,
                )
                val oauth2IntrospectController = OAuth2IntrospectController(introspectService)

                routing.registerEndpoints(
                    oauth2IntrospectController,
                    oauth2TokenController,

                    restAccountController,
                    restAccountsController,

                    restTenantController,
                )
            }

            config.validation.register(UUID::class.java, UUID::fromString)
        }
}

fun main() {
    val application = RestApplication()
    application.start()
}
