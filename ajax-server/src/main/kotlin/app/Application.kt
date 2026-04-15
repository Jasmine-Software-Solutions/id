package app

import app.application.ForgotPasswordService
import app.application.LoginService
import app.application.MagicLinkService
import app.application.oauth2.OAuth2AuthorizeService
import app.controllers.ForgotPasswordController
import app.controllers.LoginController
import app.controllers.MagicLinkController
import app.controllers.oauth2.OAuth2Controller
import app.infrastructure.ExposedLoginAuditLogger
import app.infrastructure.ForgotPasswordEmailSender
import app.infrastructure.MailMagicLinkIssuer
import app.infrastructure.etc.exception.FormErrorException
import app.infrastructure.etc.hxReswap
import app.infrastructure.etc.hxRetarget
import app.infrastructure.etc.renderWithContext
import app.infrastructure.etc.write
import app.infrastructure.models.account.*
import app.infrastructure.models.audit.ClientAuditTable
import app.infrastructure.models.audit.GrantAuditTable
import app.infrastructure.models.audit.LoginAuditTable
import app.infrastructure.models.audit.SessionAuditTable
import app.infrastructure.models.client.ClientRedirectUrisTable
import app.infrastructure.models.client.ClientTenantEntitlementsTable
import app.infrastructure.models.client.ClientsTable
import app.infrastructure.models.oauth2.MachineAccessTokensTable
import app.infrastructure.models.oauth2.SessionAccessTokensTable
import app.infrastructure.models.tenant.TenantAccountLinksTable
import app.infrastructure.models.tenant.TenantsTable
import com.zaxxer.hikari.HikariDataSource
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import io.javalin.Javalin
import io.javalin.community.routing.annotations.AnnotatedRouting
import io.javalin.http.HttpStatus
import io.javalin.http.staticfiles.Location
import io.javalin.rendering.template.JavalinJte
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.SQLIntegrityConstraintViolationException
import java.util.*

class AjaxApplication {
    private val templateEngine = if (Env.HOT_RELOAD_JTE_TEMPLATES) {
        val codeResolver = DirectoryCodeResolver(Path.of("src", "main", "kotlin", "jte"))
        TemplateEngine.create(codeResolver, ContentType.Html)
    } else TemplateEngine.createPrecompiled(Path.of("jte-classes"), ContentType.Html)

    private val database: Database
    private val app: Javalin

    init {
        val dataSource = createDataSource()
        database = connectDatabase(dataSource)
        createTables()

        val javalin = createApp()
        registerMiddleware(javalin)
        registerErrorHandlers(javalin)

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

                MagicLinksTable,
                MagicLinkDecisionsTable,

                TOTPConfigurationTable,
                TOTPUsageTable,

                ForgotPasswordCodesTable,

                LoginAuditTable,
                SessionAuditTable,
                GrantAuditTable,
                ClientAuditTable,

                TenantsTable,
                TenantAccountLinksTable,

                ClientsTable,
                ClientRedirectUrisTable,
                ClientTenantEntitlementsTable,

                SessionAccessTokensTable,
                MachineAccessTokensTable
            )
        }
    }

    private fun createApp(): Javalin =
        Javalin.create { config ->
            config.fileRenderer(JavalinJte(templateEngine))

            config.bundledPlugins.enableCors { cors ->
                cors.addRule {
                    it.anyHost()
                }
            }

            config.router.mount(AnnotatedRouting) { routing ->
                val loginAuditLogger = ExposedLoginAuditLogger()
                val loginMagicLinkIssuer = MailMagicLinkIssuer(templateEngine)
                val loginHandler = LoginService(loginAuditLogger, loginMagicLinkIssuer)
                val magicLinkHandler = MagicLinkService()
                val forgotPasswordEmailSender = ForgotPasswordEmailSender(templateEngine)
                val accountPasswordUpdater = AccountPasswordUpdater()
                val forgotPasswordHandler = ForgotPasswordService(forgotPasswordEmailSender, accountPasswordUpdater)
                val oauth2AuthorizeHandler = OAuth2AuthorizeService()

                val loginController = LoginController(loginHandler)
                val magicLinkController = MagicLinkController(magicLinkHandler)
                val forgotPasswordController = ForgotPasswordController(forgotPasswordHandler)
                val oauth2Controller = OAuth2Controller(oauth2AuthorizeHandler)

                routing.registerEndpoints(
                    loginController,
                    magicLinkController,
                    forgotPasswordController,
                    oauth2Controller
                )
            }

            config.staticFiles.add {
                it.hostedPath = "/"
                it.directory = "/public"
                it.location = Location.CLASSPATH
            }

            config.validation.register(UUID::class.java, UUID::fromString)
        }

    private fun registerMiddleware(app: Javalin) {
        app.before { ctx ->
            if (ctx.cookie("session") != null) {
                transaction {
                    try {
                        SessionAuditTable.write(ctx, "Accessed path (${ctx.path()}).")
                        commit()
                    } catch (e: ExposedSQLException) {
                        if (e.cause is SQLIntegrityConstraintViolationException) return@transaction
                        throw e
                    }
                }
            }
        }
    }

    private fun registerErrorHandlers(app: Javalin) {
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
}

fun main() {
    val application = AjaxApplication()
    application.start()
}
