package app.application

import app.infrastructure.repositories.accounts.*
import app.infrastructure.repositories.authentication.ExposedAuthenticationFlowRepository
import app.infrastructure.repositories.clients.ExposedClientRepository
import app.infrastructure.repositories.clients.ExposedDelegatedSessionRepository
import app.infrastructure.repositories.tenants.ExposedTenantMembershipRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import app.infrastructure.services.AES256EncryptionFunction
import app.infrastructure.services.Argon2HashFunction
import app.infrastructure.services.EmailService
import app.infrastructure.services.HmacSHA256SigningFunction
import app.infrastructure.services.accounts.AsymmetricJWTService
import app.infrastructure.services.accounts.EmailMagicLinkService
import app.infrastructure.services.accounts.GoogleAuthenticatorTOTPService
import com.google.gson.Gson
import com.jasminesoftwaresolutions.id.domain.IDServer
import com.jasminesoftwaresolutions.id.domain.models.account.*
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IHashedDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.registries.*
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.IEmailService
import com.jasminesoftwaresolutions.id.domain.services.IEncryptionFunction
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import com.jasminesoftwaresolutions.id.domain.services.ISigningFunction
import com.jasminesoftwaresolutions.id.domain.services.accounts.*
import com.jasminesoftwaresolutions.id.domain.services.authentication.IAuthenticationService
import com.jasminesoftwaresolutions.id.domain.services.authentication.StandardAuthenticationService
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterEmailAddressAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterPasswordAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterTOTPAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.PollMagicLinkAuthenticationFlowStep
import com.jasminesoftwaresolutions.idinterfaces.application.IDJavalinApplicationInterface
import com.jasminesoftwaresolutions.idinterfaces.user.IDJavalinUserInterface
import com.zaxxer.hikari.HikariDataSource
import gg.jte.ContentType
import gg.jte.TemplateEngine
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import io.javalin.rendering.template.JavalinJte
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class IDServerImpl : IDServer {
    val templateEngine = TemplateEngine.createPrecompiled(ContentType.Html)
    val app: Javalin

    private val database = HikariDataSource().apply {
        maximumPoolSize = Env.DATABASE_POOL_SIZE
        driverClassName = Env.DATABASE_DRIVER
        jdbcUrl = Env.DATABASE_URL
        username = Env.DATABASE_USERNAME
        password = Env.DATABASE_PASSWORD
        isAutoCommit = false
    }.let { Database.connect(it, databaseConfig = DatabaseConfig {
        keepLoadedReferencesOutOfTransaction = true
    }) }

    override var gson = Gson()

    override var hashFunction: IHashFunction = Argon2HashFunction(64,
        Env.PASSWORD_SALT_LENGTH,
        Env.PASSWORD_HASH_ITERATIONS,
        Env.ARGON2_MEMORY,
        Env.ARGON2_PARALLELISM)

    override var encryptionFunction: IEncryptionFunction = AES256EncryptionFunction(
        Env.ENCRYPTED_PARAMETER_SECRET.toCharArray(),
        Env.ENCRYPTED_PARAMETER_SALT.toByteArray(),
    )

    override var signingFunction: ISigningFunction = HmacSHA256SigningFunction(Env.ENCRYPTED_PARAMETER_SECRET.toByteArray())

    override var authenticationStepRegistry: IAuthenticationStepRegistry<IAuthenticationFlow> = AuthenticationStepRegistry()
    override var authenticationStepHandlerRegistry: IAuthenticationStepHandlerRegistry<IAuthenticationFlow> = AuthenticationStepHandlerRegistry()
    override var scopeRegistry: IScopeRegistry<IScope> = ScopeRegistry()

    override var accountRepository: IAccountRepository<IAccount> = ExposedAccountRepository()
    override var passwordRepository: IPasswordRepository<IHashedPassword> = ExposedPasswordRepository(hashFunction, accountRepository)
    override var magicLinkRepository: IMagicLinkRepository<IHashedMagicLink> = ExposedMagicLinkRepository(hashFunction, accountRepository)
    override var totpConfigurationRepository: ITOTPConfigurationRepository<ITOTPConfiguration> = ExposedTOTPConfigurationRepository(encryptionFunction, accountRepository)
    override var sessionRepository: ISessionRepository<IHashedSession> = ExposedSessionRepository(hashFunction, accountRepository)

    override var clientRepository: IClientRepository<IClient> = ExposedClientRepository(hashFunction)

    override var tenantRepository: ITenantRepository<ITenant> = ExposedTenantRepository()
    override var tenantMembershipRepository: ITenantMembershipRepository<ITenantMembership> = ExposedTenantMembershipRepository(tenantRepository, accountRepository)

    override var authenticationFlowRepository: IAuthenticationFlowRepository<IAuthenticationFlow> = ExposedAuthenticationFlowRepository(authenticationStepRegistry, tenantRepository, accountRepository)

    override var delegatedSessionRepository: IDelegatedSessionRepository<IHashedDelegatedSession> = ExposedDelegatedSessionRepository(hashFunction, sessionRepository, clientRepository, tenantRepository)

    override var emailService: IEmailService = EmailService(
        Env.SMTP_AUTH,
        Env.SMTP_STARTTLS_ENABLE,
        Env.SMTP_HOST,
        Env.PORT,
        Env.SMTP_STARTTLS_ENABLE,
        Env.SMTP_USERNAME,
        Env.SMTP_PASSWORD,
        Env.SMTP_EMAIL
    )

    override var jwtService: IJWTService = AsymmetricJWTService("RSA", Env.JWT_PUBLIC_KEY!!, Env.JWT_PRIVATE_KEY!!)

    override var magicLinkService: IMagicLinkService<IMagicLink> = EmailMagicLinkService(
        magicLinkRepository,
        lifetime = 10.minutes,
        emailService = emailService,
        templateEngine = templateEngine,
        externalBaseUrl = Env.EXTERNAL_BASE_URL
    )

    override var sessionService: ISessionService<ISession> = StandardSessionService(sessionRepository, 1.hours)

    override var totpService: ITOTPService<ISetTOTPConfiguration> = GoogleAuthenticatorTOTPService(
        totpConfigurationRepository
    )

    override var authenticationService: IAuthenticationService<IAuthenticationFlow> = StandardAuthenticationService(
        stepRegistry = authenticationStepRegistry,
        handlerRegistry = authenticationStepHandlerRegistry,
        flowRepository = authenticationFlowRepository,
        sessionService = sessionService,
        lifetime = 1.hours
    )

    var userInterface: IDJavalinUserInterface = IDJavalinUserInterface(this)
    var applicationInterface: IDJavalinApplicationInterface = IDJavalinApplicationInterface(this)

    init {
        configureRegistries()

        app = Javalin.create { config ->
            config.fileRenderer(JavalinJte(templateEngine))

            config.staticFiles.add {
                it.hostedPath = "/"
                it.directory = "/public"
                it.location = Location.CLASSPATH
            }

            userInterface.install(config)
            applicationInterface.install(config)
        }

        userInterface.install(app)
        applicationInterface.install(app)
    }

    override fun start(port: Int) {
        app.start(port)
    }

    override fun stop() {
        app.stop()
    }

    private fun configureRegistries() {
        authenticationStepRegistry.apply {
            register(EnterEmailAddressAuthenticationFlowStep)
            /*            register(
                            step = PollMagicLinkAuthenticationFlowStep,
                            after = EnterEmailAddressAuthenticationFlowStep
                        )*/
            register(
                step = EnterPasswordAuthenticationFlowStep,
                after = EnterEmailAddressAuthenticationFlowStep,
            )
            register(
                step = EnterTOTPAuthenticationFlowStep,
                after = EnterPasswordAuthenticationFlowStep,
                condition = {
                    it.account != null && totpConfigurationRepository.findByAccount(it.account!!.id).enabled
                }
            )
        }

        authenticationStepHandlerRegistry.apply {
            register(
                step = EnterEmailAddressAuthenticationFlowStep,
                handler = EnterEmailAddressAuthenticationFlowStep.Handler(
                    accountRepository,
                    tenantMembershipRepository,
                    authenticationFlowRepository
                )
            )

            register(
                step = EnterPasswordAuthenticationFlowStep,
                handler = EnterPasswordAuthenticationFlowStep.Handler(
                    accountRepository,
                    tenantMembershipRepository,
                    passwordRepository,
                    authenticationFlowRepository
                )
            )

            register(
                step = PollMagicLinkAuthenticationFlowStep,
                handler = PollMagicLinkAuthenticationFlowStep.Handler<IAuthenticationFlow, IMagicLink>(
                    magicLinkRepository,
                    magicLinkService
                )
            )

            register(
                step = EnterTOTPAuthenticationFlowStep,
                handler = EnterTOTPAuthenticationFlowStep.Handler(
                    totpService
                )
            )
        }
    }
}