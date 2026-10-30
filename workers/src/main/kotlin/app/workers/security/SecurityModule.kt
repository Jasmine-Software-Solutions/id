package app.workers.security

import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.repositories.accounts.ExposedPasswordRepository
import app.infrastructure.repositories.accounts.ExposedSessionRepository
import app.infrastructure.repositories.security.ExposedRestrictionRepository
import app.infrastructure.services.SnsEventDispatcher
import app.workers.WorkerModule
import app.workers.infrastructure.IpGeolocationService
import app.workers.infrastructure.SqsClientFactory
import app.workers.infrastructure.SqsEventListener
import app.workers.infrastructure.WorkerModuleName
import app.workers.infrastructure.workerGson
import app.workers.security.detectors.BruteForceDetector
import app.workers.security.detectors.CredentialStuffingDetector
import app.workers.security.detectors.EnumerationDetector
import app.workers.security.detectors.ImpossibleTravelDetector
import app.workers.security.detectors.RapidCreationDetector
import app.workers.security.detectors.TotpDisableDetector
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.UUID

/**
 * Runs the ID platform's security detectors against events on `id-security`.
 *
 * Unlike the other modules this one owns a database pool and a geolocation
 * lookup thread, both of which only come into existence when the security
 * module is actually selected.
 */
class SecurityModule(private val env: SecurityWorkerEnv = SecurityWorkerEnv.Default) : WorkerModule {

    override val name = WorkerModuleName.SECURITY
    override val queueUrl: String = env.SQS_QUEUE_URL_SECURITY

    private val geoLocation = IpGeolocationService()

    private var listener: SqsEventListener? = null
    private var dataSource: HikariDataSource? = null

    init {
        require(queueUrl.isNotBlank()) {
            "IdWorker: security module is enabled but SQS_QUEUE_URL_SECURITY is not set"
        }
    }

    override fun start() {
        // -- Database (shared with ID server) ----------------------------------------

        val dataSource = HikariDataSource().apply {
            maximumPoolSize = env.DATABASE_POOL_SIZE
            driverClassName = env.DATABASE_DRIVER
            jdbcUrl = env.DATABASE_URL
            username = env.DATABASE_USERNAME
            password = env.DATABASE_PASSWORD
            isAutoCommit = false
        }
        this.dataSource = dataSource

        Database.connect(
            dataSource,
            databaseConfig = DatabaseConfig {
                keepLoadedReferencesOutOfTransaction = true
            }
        )

        // -- Infrastructure ----------------------------------------------------------

        val clientId = UUID.nameUUIDFromBytes("security-worker".toByteArray())

        val restrictionRepository = ExposedRestrictionRepository()
        val eventDispatcher = SnsEventDispatcher(
            gson = workerGson,
            topicArn = env.SNS_TOPIC_ARN,
            region = env.SNS_REGION,
            accessKey = env.AWS_ACCESS_KEY_ID,
            secretKey = env.AWS_SECRET_ACCESS_KEY,
            awsProfile = env.AWS_PROFILE,
            endpointOverride = env.SNS_ENDPOINT_OVERRIDE,
        )

        val enforcer = RestrictionEnforcer(restrictionRepository, eventDispatcher, clientId)

        val bruteForce = BruteForceDetector(eventDispatcher, enforcer, clientId)
        val enumeration = EnumerationDetector(eventDispatcher, enforcer, clientId)
        val rapidCreation = RapidCreationDetector(
            eventDispatcher,
            enforcer,
            clientId,
            creationThreshold = env.CREATION_THRESHOLD,
            windowMinutes = env.CREATION_WINDOW_MINUTES,
        )
        val impossibleTravel = ImpossibleTravelDetector(geoLocation, eventDispatcher, clientId)
        val totpDisable = TotpDisableDetector(geoLocation, eventDispatcher, clientId)
        val sessionTracker = SessionTracker(eventDispatcher, clientId)

        // -- Credential stuffing detection -------------------------------------------
        // These repositories are only used for session revocation and password deletion
        // when a compromised password is detected — they do NOT hash anything.

        val accountRepository = ExposedAccountRepository()
        // We reuse the server's Argon2HashFunction only to construct the repositories
        // (they require it as a constructor parameter for token/password hashing in normal
        // create/update paths, but we only call findByAccount + delete on them).
        val placeholderHash = object : IHashFunction {
            override fun hash(value: ByteArray): String =
                MessageDigest.getInstance("SHA-256")
                    .digest(value).joinToString("") { "%02x".format(it) }

            override fun verify(value: ByteArray, hash: String): Boolean = false
        }
        val sessionRepository = ExposedSessionRepository(placeholderHash, accountRepository)
        val passwordRepository = ExposedPasswordRepository(placeholderHash, accountRepository)

        val sessionRevoker = ExposedSessionRevoker(sessionRepository)
        val passwordDeleter = ExposedPasswordDeleter(passwordRepository)

        val credentialStuffing = CredentialStuffingDetector(
            eventDispatcher = eventDispatcher,
            sessionRevoker = sessionRevoker,
            passwordDeleter = passwordDeleter,
            clientId = clientId,
        )

        val worker = SecurityWorker(
            bruteForce = bruteForce,
            enumeration = enumeration,
            rapidCreation = rapidCreation,
            impossibleTravel = impossibleTravel,
            totpDisable = totpDisable,
            sessionTracker = sessionTracker,
            credentialStuffing = credentialStuffing,
        )

        val listener = SqsEventListener(
            queueUrl = queueUrl,
            eventTypes = worker.eventTypes,
            handler = worker::handle,
            client = SqsClientFactory.create(env),
        )
        this.listener = listener

        log.info("IdWorker: security module polling $queueUrl")
        geoLocation.start()
        listener.start()
    }

    override fun stop() {
        log.info("IdWorker: stopping security module")
        geoLocation.stop()
        listener?.stop()
        dataSource?.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(SecurityModule::class.java)
    }
}
