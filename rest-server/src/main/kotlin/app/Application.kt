package app

import app.models.account.AccountsTable
import app.models.account.ForgotPasswordCodesTable
import app.models.account.PasswordsTable
import app.models.account.SessionsTable
import app.models.audit.ClientAuditTable
import app.models.audit.GrantAuditTable
import app.models.audit.LoginAuditTable
import app.models.audit.SessionAuditTable
import app.models.client.ClientRedirectUrisTable
import app.models.client.ClientsTable
import app.models.oauth2.MachineAccessTokensTable
import app.models.oauth2.SessionAccessTokensTable
import app.models.tenant.TenantAccountLinksTable
import app.models.tenant.TenantsTable
import app.routes.RestAccountRoutes
import app.routes.RestAccountsRoutes
import app.routes.RestTenantRoutes
import app.routes.oauth2.OAuth2IntrospectRoute
import app.routes.oauth2.OAuth2TokenRoute
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import io.javalin.community.routing.annotations.AnnotatedRouting
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

lateinit var database: Database
lateinit var app: Javalin

fun main() {
    val dataSource = HikariDataSource().apply {
        maximumPoolSize = Env.DATABASE_POOL_SIZE
        driverClassName = Env.DATABASE_DRIVER
        jdbcUrl = Env.DATABASE_URL
        username = Env.DATABASE_USERNAME
        password = Env.DATABASE_PASSWORD
        isAutoCommit = false
    }

    database = Database.connect(dataSource, databaseConfig = DatabaseConfig {
        keepLoadedReferencesOutOfTransaction = true
    })

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

    app = Javalin.create { config ->
        config.bundledPlugins.enableCors { cors ->
            cors.addRule {
                it.anyHost()
            }
        }

        config.router.mount(AnnotatedRouting) { routing ->
            routing.registerEndpoints(
                OAuth2IntrospectRoute,
                OAuth2TokenRoute,

                RestAccountRoutes,
                RestAccountsRoutes,

                RestTenantRoutes
            )
        }

        config.validation.register(UUID::class.java, UUID::fromString)
    }.start(Env.PORT)
}