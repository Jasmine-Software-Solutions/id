package app

import app.etc.exception.FormErrorException
import app.etc.hxReswap
import app.etc.hxRetarget
import app.etc.renderWithContext
import app.routes.oauth2.OAuth2AuthorizationRoute
import app.routes.LoginRoutes
import app.routes.api.RestAccountRoutes
import app.routes.api.RestAccountsRoutes
import app.routes.api.RestTenantRoutes
import app.routes.oauth2.OAuth2IntrospectRoute
import app.routes.oauth2.OAuth2TokenRoute
import app.sql.account.AccountsTable
import app.sql.account.ForgotPasswordCodesTable
import app.sql.account.PasswordsTable
import app.sql.account.SessionsTable
import app.sql.audit.ClientAuditTable
import app.sql.audit.GrantAuditTable
import app.sql.audit.LoginAuditTable
import app.sql.audit.SessionAuditTable
import app.sql.client.ClientRedirectUrisTable
import app.sql.client.ClientsTable
import app.sql.oauth2.MachineAccessTokensTable
import app.sql.oauth2.SessionAccessTokensTable
import app.sql.tenant.TenantAccountLinksTable
import app.sql.tenant.TenantsTable
import com.zaxxer.hikari.HikariDataSource
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import io.javalin.Javalin
import io.javalin.community.routing.annotations.AnnotatedRouting
import io.javalin.http.HttpStatus
import io.javalin.http.staticfiles.Location
import io.javalin.rendering.template.JavalinJte
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.util.*

val templateEngine: TemplateEngine = if (Env.HOT_RELOAD_JTE_TEMPLATES) {
    val codeResolver = DirectoryCodeResolver(Path.of("src", "main", "kotlin", "jte"))
    TemplateEngine.create(codeResolver, ContentType.Html)
} else TemplateEngine.createPrecompiled(Path.of("jte-classes"), ContentType.Html)

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
        config.fileRenderer(JavalinJte(templateEngine))

        config.bundledPlugins.enableCors { cors ->
            cors.addRule {
                it.anyHost()
            }
        }

        config.router.mount(AnnotatedRouting) { routing ->
            routing.registerEndpoints(
                LoginRoutes,

                OAuth2AuthorizationRoute,
                OAuth2TokenRoute,
                OAuth2IntrospectRoute,

                RestAccountRoutes,
                RestAccountsRoutes,

                RestTenantRoutes
            )
        }

        config.staticFiles.add {
            it.hostedPath = "/"
            it.directory = "/public"
            it.location = Location.CLASSPATH
        }

        config.validation.register(UUID::class.java, UUID::fromString)
    }.start(Env.PORT)

    app.before { ctx ->
        if (ctx.cookie("session") != null)
            transaction { SessionAuditTable.write(ctx, "Accessed path (${ctx.path()}).") }
    }

    app.error(HttpStatus.NOT_FOUND) { ctx -> ctx.renderWithContext("pages/status/4xx.kte") }
    app.error(HttpStatus.BAD_REQUEST) { ctx ->
        if (ctx.path().startsWith("/oauth2/") || ctx.path().startsWith("/api/"))
            return@error

        ctx.renderWithContext("pages/status/4xx.kte")
    }

    app.exception(FormErrorException::class.java) { ex, ctx ->
        ctx.hxRetarget(ex.formErrorElement)
        ctx.hxReswap("innerHTML transition:true")

        ctx.renderWithContext("components/alert.kte", "summary" to ex.summary, "detail" to ex.detail)
    }
}