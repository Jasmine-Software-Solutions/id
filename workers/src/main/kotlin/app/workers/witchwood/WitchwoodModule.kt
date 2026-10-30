package app.workers.witchwood

import app.infrastructure.services.accounts.AsymmetricJWTService
import app.workers.WorkerModule
import app.workers.infrastructure.SqsClientFactory
import app.workers.infrastructure.SqsEventListener
import app.workers.infrastructure.WorkerModuleName
import com.google.gson.JsonObject
import com.jasminesoftwaresolutions.witchwood.client.WitchwoodClient
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * Synchronizes principals, roles and policies to Witchwood from events on
 * `id-witchwood`.
 *
 * The JWT keys are only decoded, and the gRPC client only dialed, when the
 * witchwood module is actually selected.
 */
class WitchwoodModule(private val env: WitchwoodWorkerEnv = WitchwoodWorkerEnv.Default) : WorkerModule {

    override val name = WorkerModuleName.WITCHWOOD
    override val queueUrl: String = env.SQS_QUEUE_URL_WITCHWOOD

    private var listener: SqsEventListener? = null
    private var witchwoodClient: WitchwoodClient? = null

    init {
        require(queueUrl.isNotBlank()) {
            "IdWorker: witchwood module is enabled but SQS_QUEUE_URL_WITCHWOOD is not set"
        }
    }

    override fun start() {
        // -- JWT service for Witchwood gRPC auth (shared with ID server) -----------

        val publicKeyData = Base64.getDecoder().decode(env.JWT_ENCODED_PUBLIC_KEY)
        val privateKeyData = Base64.getDecoder().decode(env.JWT_ENCODED_PRIVATE_KEY)

        val jwtService = AsymmetricJWTService(
            keyAlgorithm = "RSA",
            publicKeyData = publicKeyData,
            privateKeyData = privateKeyData,
            issueAs = "https://id.jasmine.software/",
        )

        val witchwoodTokenSupplier: () -> String = {
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

        val witchwoodClient = WitchwoodClient(
            env.WITCHWOOD_HOST,
            env.WITCHWOOD_PORT,
            witchwoodTokenSupplier,
        )
        this.witchwoodClient = witchwoodClient

        val principals = PrincipalSyncer(witchwoodClient)
        val roles = RoleSyncer(witchwoodClient)
        val policies = PolicySyncer(witchwoodClient, PolicyCompiler())

        val worker = WitchwoodSyncer(
            principals = principals,
            roles = roles,
            policies = policies,
        )

        val listener = SqsEventListener(
            queueUrl = queueUrl,
            eventTypes = worker.eventTypes,
            handler = worker::handle,
            client = SqsClientFactory.create(env),
        )
        this.listener = listener

        log.info("IdWorker: witchwood module polling $queueUrl")
        listener.start()
    }

    override fun stop() {
        log.info("IdWorker: stopping witchwood module")
        listener?.stop()
        witchwoodClient?.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(WitchwoodModule::class.java)
    }
}
