package app.application

import app.infrastructure.repositories.accounts.*
import app.infrastructure.repositories.authentication.ExposedAuthenticationFlowRepository
import app.infrastructure.repositories.authorization.ExposedPolicyAssignmentRepository
import app.infrastructure.repositories.authorization.ExposedPolicyRepository
import app.infrastructure.repositories.authorization.ExposedRoleAssignmentRepository
import app.infrastructure.repositories.authorization.ExposedRoleRepository
import app.infrastructure.repositories.clients.ExposedClientRepository
import app.infrastructure.repositories.clients.ExposedDelegatedSessionRepository
import app.infrastructure.repositories.scopes.ExposedScopeRepository
import app.infrastructure.repositories.security.ExposedRestrictionRepository
import app.infrastructure.repositories.tenants.ExposedMembershipInvitationRepository
import app.infrastructure.repositories.tenants.ExposedTenantMembershipRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import app.infrastructure.services.AES256EncryptionFunction
import app.infrastructure.services.Argon2HashFunction
import app.infrastructure.services.HmacSHA256SigningFunction
import app.infrastructure.services.SHA256HashFunction
import app.infrastructure.services.SnsEventDispatcher
import app.infrastructure.services.StandardRestrictionService
import app.infrastructure.services.WitchwoodAuthorizationService
import app.infrastructure.services.accounts.AsymmetricJWTService
import app.infrastructure.services.accounts.GoogleAuthenticatorTOTPService
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.TypeAdapterFactory
import com.jasminesoftwaresolutions.id.domain.IDServer
import com.jasminesoftwaresolutions.id.domain.events.Event
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IPassword
import com.jasminesoftwaresolutions.id.domain.models.authorization.IIDToken
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.registries.*
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.repositories.IRestrictionRepository
import com.jasminesoftwaresolutions.id.domain.services.IEncryptionFunction
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import com.jasminesoftwaresolutions.id.domain.services.ISigningFunction
import com.jasminesoftwaresolutions.id.domain.services.accounts.*
import com.jasminesoftwaresolutions.id.domain.services.authentication.IAuthenticationService
import com.jasminesoftwaresolutions.id.domain.services.authentication.StandardAuthenticationService
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterEmailAddressAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterNameAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterPasswordAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterTOTPAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.PollMagicLinkAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.services.authorization.IOIDCClaimsResolver
import com.jasminesoftwaresolutions.id.domain.services.authorization.ITokenService
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.services.authorization.JWTTokenService
import com.jasminesoftwaresolutions.id.domain.services.authorization.StandardOIDCClaimsResolver
import com.jasminesoftwaresolutions.id.domain.services.security.IRestrictionService
import com.jasminesoftwaresolutions.idinterfaces.GsonJsonMapper
import com.jasminesoftwaresolutions.idinterfaces.application.IDJavalinApplicationInterface
import com.jasminesoftwaresolutions.witchwood.client.WitchwoodClient
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class IDServerImpl : IDServer {
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

    override var gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(Instant::class.java, JsonSerializer<Instant> { src, _, _ ->
            JsonPrimitive(src.epochSecond)
        })
        .registerTypeAdapterFactory(object : TypeAdapterFactory {
            override fun <T> create(gson: Gson, type: com.google.gson.reflect.TypeToken<T>): com.google.gson.TypeAdapter<T>? {
                if (!Event::class.java.isAssignableFrom(type.rawType)) return null
                val delegate = gson.getDelegateAdapter(this, type)
                return object : com.google.gson.TypeAdapter<T>() {
                    override fun write(out: com.google.gson.stream.JsonWriter, value: T) {
                        val tree = delegate.toJsonTree(value).asJsonObject
                        tree.addProperty("event_type", (value as Event).eventType)
                        gson.toJson(tree, out)
                    }
                    override fun read(reader: com.google.gson.stream.JsonReader): T = delegate.read(reader)
                }
            }
        })
        .create()

    var passwordHashFunction: IHashFunction = Argon2HashFunction(64,
        Env.PASSWORD_SALT_LENGTH,
        Env.PASSWORD_HASH_ITERATIONS,
        Env.ARGON2_MEMORY,
        Env.ARGON2_PARALLELISM)

    override var hashFunction: IHashFunction = SHA256HashFunction()

    override var encryptionFunction: IEncryptionFunction = AES256EncryptionFunction(
        Env.ENCRYPTED_PARAMETER_SECRET.toCharArray(),
        Env.ENCRYPTED_PARAMETER_SALT.toByteArray(),
    )

    override var signingFunction: ISigningFunction = HmacSHA256SigningFunction(Env.ENCRYPTED_PARAMETER_SECRET.toByteArray())

    override var authenticationStepRegistry: IAuthenticationStepRegistry = AuthenticationStepRegistry()
    override var authenticationStepHandlerRegistry: IAuthenticationStepHandlerRegistry = AuthenticationStepHandlerRegistry()
    override var scopeRegistry: IScopeRegistry<IScope> = ScopeRegistry()

    override var tenantRepository: ITenantRepository = ExposedTenantRepository()

    override var accountRepository: IAccountRepository = ExposedAccountRepository()
    override var passwordRepository: IPasswordRepository = ExposedPasswordRepository(passwordHashFunction, accountRepository)
    override var magicLinkRepository: IMagicLinkRepository = ExposedMagicLinkRepository(hashFunction, accountRepository)
    override var emailChangeRepository: IEmailChangeRepository = ExposedEmailChangeRepository(hashFunction, accountRepository)
    override var totpConfigurationRepository: ITOTPConfigurationRepository = ExposedTOTPConfigurationRepository(encryptionFunction, accountRepository)
    override var sessionRepository: ISessionRepository = ExposedSessionRepository(hashFunction, accountRepository)

    override var clientRepository: IClientRepository = ExposedClientRepository(hashFunction)
    override var scopeRepository: IScopeRepository = ExposedScopeRepository(clientRepository)

    override var roleRepository: IRoleRepository = ExposedRoleRepository(tenantRepository)

    override var roleAssignmentRepository: IRoleAssignmentRepository = ExposedRoleAssignmentRepository(
        accountRepository,
        clientRepository,
        roleRepository
    )

    override var policyRepository: IPolicyRepository = ExposedPolicyRepository(tenantRepository)

    override var policyAssignmentRepository: IPolicyAssignmentRepository = ExposedPolicyAssignmentRepository(
        accountRepository,
        clientRepository,
        roleRepository,
        policyRepository
    )

    override var tenantMembershipRepository: ITenantMembershipRepository = ExposedTenantMembershipRepository(tenantRepository, accountRepository)

    override var membershipInvitationRepository: IMembershipInvitationRepository = ExposedMembershipInvitationRepository(hashFunction, tenantRepository)

    override var authenticationFlowRepository: IAuthenticationFlowRepository = ExposedAuthenticationFlowRepository(authenticationStepRegistry, tenantRepository, accountRepository)

    override var delegatedSessionRepository: IDelegatedSessionRepository = ExposedDelegatedSessionRepository(hashFunction, sessionRepository, clientRepository, scopeRepository, scopeRegistry)

    override var eventDispatcher: IEventDispatcher = SnsEventDispatcher(gson)

    override var jwtService: IJWTService = AsymmetricJWTService("RSA", Env.JWT_PUBLIC_KEY!!, Env.JWT_PRIVATE_KEY!!, "https://id.jasmine.software/")

    override var magicLinkService: IMagicLinkService = StandardMagicLinkService(
        magicLinkRepository,
        accountRepository,
        lifetime = 10.minutes
    )

    override var emailChangeService: IEmailChangeService = StandardEmailChangeService(
        emailChangeRepository,
        lifetime = 15.minutes
    )

    override var sessionService: ISessionService = StandardSessionService(sessionRepository, 12.hours)

    override var totpService: ITOTPService = GoogleAuthenticatorTOTPService(
        totpConfigurationRepository,
        issuer = "Jasmine ID"
    )

    override var authenticationService: IAuthenticationService = StandardAuthenticationService(
        stepRegistry = authenticationStepRegistry,
        handlerRegistry = authenticationStepHandlerRegistry,
        flowRepository = authenticationFlowRepository,
        sessionService = sessionService,
        eventDispatcher = eventDispatcher,
        lifetime = 8.hours
    )

    override var oidcClaimsResolver: IOIDCClaimsResolver = StandardOIDCClaimsResolver()

    override var tokenService: ITokenService<IAccessToken, IRefreshToken, IServiceToken, IIDToken> = JWTTokenService(
        jwtService,
        accountRepository,
        clientRepository,
        delegatedSessionRepository,
        sessionRepository,
        accessTokenLifetime = 5.minutes,
        refreshTokenLifetime = 3.hours
    )

    // -- Witchwood -----------------------------------------------------------

    /** Generates short-lived JWTs for Witchwood gRPC auth. Issuer and subject are "id". */
    private val witchwoodJwtSupplier: () -> String = {
        val now = Instant.now()
        val payload = JsonObject().apply {
            addProperty("sub", "https://id.jasmine.software/")
            addProperty("aud", "https://witchwood.jasmine.software/")
            addProperty("iat", now.epochSecond)
            addProperty("nbf", now.epochSecond)
            addProperty("exp", now.plus(5, ChronoUnit.MINUTES).epochSecond)
        }
        jwtService.encode(payload)
    }

    private val witchwoodClient = WitchwoodClient(
        Env.WITCHWOOD_HOST,
        Env.WITCHWOOD_PORT,
        witchwoodJwtSupplier,
    )

    /** Called at startup to register ID's service and actions in Witchwood. */
    fun registerServiceAndActions() {
        witchwoodClient.registerService(IdActions.SERVICE).get()
        witchwoodClient.putActions(IdActions.SERVICE, IdActions.ALL).get()
    }

    override var authorizationService: IAuthorizationService = WitchwoodAuthorizationService(witchwoodClient)

    override var restrictionRepository: IRestrictionRepository = ExposedRestrictionRepository()

    override var restrictionService: IRestrictionService = StandardRestrictionService(
        restrictionRepository,
        eventDispatcher,
    )

    var applicationInterface: IDJavalinApplicationInterface = IDJavalinApplicationInterface(this, Env.EXTERNAL_BASE_URL)

    init {
        configureRegistries()

        app = Javalin.create { config ->
            config.jsonMapper(GsonJsonMapper(gson))
            // Behind a proxy every request's socket peer is the proxy, so
            // `ctx.ip()` — restrictions, flows, audit events — would name the
            // proxy rather than the client. When configured, take the client's
            // address from the named header instead.
            Env.CLIENT_IP_HEADER?.let { header ->
                config.contextResolver.ip = { ctx ->
                    clientIp(ctx.header(header), ctx.req().remoteAddr)
                }
            }
            applicationInterface.install(config)
        }

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
            register(EnterEmailAddressAuthenticationFlowStep, null)
            register(
                step = PollMagicLinkAuthenticationFlowStep,
                after = EnterEmailAddressAuthenticationFlowStep,
                priority = if (Env.DEV_ENVIRONMENT) -1 else 1,
            )
            register(
                step = EnterPasswordAuthenticationFlowStep,
                after = EnterEmailAddressAuthenticationFlowStep,
            )
            register(
                step = EnterTOTPAuthenticationFlowStep,
                afterAnyOf = setOf(PollMagicLinkAuthenticationFlowStep, EnterPasswordAuthenticationFlowStep),
                condition = {
                    it.account != null && totpConfigurationRepository.findByAccount(it.account!!.id).enabled == true
                }
            )
            // Only accounts created without a name — the magic-link sign-up path — qualify, so
            // existing and invitation-created accounts never see this step. Priority keeps it
            // behind TOTP when an account qualifies for both: a name is collected after the
            // second factor, never instead of it.
            register(
                step = EnterNameAuthenticationFlowStep,
                afterAnyOf = setOf(
                    PollMagicLinkAuthenticationFlowStep,
                    EnterPasswordAuthenticationFlowStep,
                    EnterTOTPAuthenticationFlowStep,
                ),
                condition = {
                    it.account != null && it.account!!.firstName.isBlank() && it.account!!.lastName.isBlank()
                },
                priority = -1,
            )
        }

        authenticationStepHandlerRegistry.apply {
            register(
                step = EnterEmailAddressAuthenticationFlowStep,
                handler = EnterEmailAddressAuthenticationFlowStep.Handler(
                    accountRepository,
                    tenantMembershipRepository,
                    authenticationFlowRepository,
                    eventDispatcher
                )
            )

            register(
                step = EnterPasswordAuthenticationFlowStep,
                handler = EnterPasswordAuthenticationFlowStep.Handler(
                    accountRepository,
                    tenantMembershipRepository,
                    passwordRepository,
                    authenticationFlowRepository,
                    eventDispatcher,
                    object : IPassword {
                        private val dummyHash
                            = passwordHashFunction.hash(SecureToken().toByteArray())

                        override fun verify(password: String): Boolean {
                            passwordHashFunction.verify(password.toByteArray(), dummyHash)
                            return false
                        }

                        override var account: IAccount
                            get() = throw IllegalAccessException()
                            set(value) { throw IllegalAccessException() }
                        override val id: UUID
                            get() = throw IllegalAccessException()
                        override val createdAt: Instant
                            get() = throw IllegalAccessException()
                    }
                )
            )

            register(
                step = PollMagicLinkAuthenticationFlowStep,
                handler = PollMagicLinkAuthenticationFlowStep.Handler(
                    magicLinkRepository,
                    magicLinkService,
                    eventDispatcher,
                    sender = Env.RESEND_SENDER.second,
                )
            )

            register(
                step = EnterTOTPAuthenticationFlowStep,
                handler = EnterTOTPAuthenticationFlowStep.Handler(
                    totpService,
                    eventDispatcher
                )
            )

            register(
                step = EnterNameAuthenticationFlowStep,
                handler = EnterNameAuthenticationFlowStep.Handler(
                    accountRepository,
                    eventDispatcher
                )
            )
        }
    }
}
