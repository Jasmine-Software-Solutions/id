package app.infrastructure.handoffs

import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.repositories.accounts.ExposedSessionRepository
import app.infrastructure.repositories.clients.ExposedClientRepository
import app.infrastructure.repositories.clients.ExposedDelegatedSessionRepository
import app.infrastructure.repositories.handoffs.ExposedHandoffQrTokenRepository
import app.infrastructure.repositories.handoffs.ExposedHandoffRepository
import app.infrastructure.repositories.scopes.ExposedScopeRepository
import app.infrastructure.services.SHA256HashFunction
import app.infrastructure.services.StandardHandoffService
import com.jasminesoftwaresolutions.id.domain.events.Event
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.authorization.EmailScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.OpenIdScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.ProfileScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoffQrTokenRepository
import com.jasminesoftwaresolutions.id.domain.registries.ScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IDelegatedSessionRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IHandoffRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IScopeRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.StandardSessionService
import com.jasminesoftwaresolutions.id.domain.services.handoff.IHandoffService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.net.URI
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.time.Duration.Companion.hours

/**
 * A real SQLite database with the real Exposed repositories, wired the way
 * [app.application.IDServerImpl] wires them.
 *
 * The session used by tests deliberately outlives the default 12-hour session
 * service lifetime only by construction; tests that care about expiry build
 * their own session.
 */
class HandoffTestFixture(sessionExpiresAt: Instant = Instant.now().plus(12, ChronoUnit.HOURS)) {
    val hashFunction = SHA256HashFunction()
    val dispatcher = RecordingEventDispatcher()

    val accountRepository: IAccountRepository
    val sessionRepository: ISessionRepository
    val clientRepository: IClientRepository
    val scopeRepository: IScopeRepository
    val delegatedSessionRepository: IDelegatedSessionRepository
    val handoffRepository: IHandoffRepository
    val qrTokenRepository: IHandoffQrTokenRepository
    val service: IHandoffService

    val account: IAccount
    val session: IHashedSession
    val client: IClient
    val redirectUri: URI = URI.create("http://localhost/api/v1/auth/callback")

    init {
        val path = Files.createTempFile("id-handoffs", ".db")
        path.toFile().deleteOnExit()

        val database = Database.connect(
            "jdbc:sqlite:${path.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
            // SQLite ignores foreign keys unless asked; Postgres — the real
            // database — enforces them. Turning them on keeps the cascade
            // assertions honest.
            setupConnection = { connection ->
                connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            },
        )
        // Database.connect only installs a default the first time; every test
        // gets a fresh file, so point the default at this one explicitly.
        TransactionManager.defaultDatabase = database

        accountRepository = ExposedAccountRepository()
        sessionRepository = ExposedSessionRepository(hashFunction, accountRepository)
        clientRepository = ExposedClientRepository(hashFunction)
        scopeRepository = ExposedScopeRepository(clientRepository)

        val scopeRegistry = ScopeRegistry()
        delegatedSessionRepository = ExposedDelegatedSessionRepository(
            hashFunction,
            sessionRepository,
            clientRepository,
            scopeRepository,
            scopeRegistry,
        )
        handoffRepository = ExposedHandoffRepository(
            hashFunction,
            sessionRepository,
            clientRepository,
            scopeRepository,
            scopeRegistry,
        )
        qrTokenRepository = ExposedHandoffQrTokenRepository(hashFunction, handoffRepository)

        val sessionService = StandardSessionService(sessionRepository, 12.hours)
        service = StandardHandoffService(
            handoffRepository,
            qrTokenRepository,
            sessionRepository,
            sessionService,
            delegatedSessionRepository,
            dispatcher,
        )

        account = accountRepository.create {
            email = "handoff-${UUID.randomUUID()}@example.com"
            firstName = "Ada"
            lastName = "Lovelace"
            emailVerified = true
        }

        session = sessionRepository.create {
            this.account = this@HandoffTestFixture.account
            token = SecureToken()
            expiresAt = sessionExpiresAt
        }

        client = clientRepository.create {
            fqdn = "handoff-${UUID.randomUUID()}.example.com"
            confidential = true
            secret = "client-secret"
            redirectUris.add(redirectUri)
        }
    }

    val scopes: Set<IScope> = setOf(OpenIdScope, ProfileScope, EmailScope)

    fun createHandoff() = service.createOrAttach(session, client, redirectUri, "test-state", scopes, "test-nonce")

    fun scan(handoffId: UUID, token: String) = service.scan(handoffId, token, "scan-ua", "203.0.113.7")
}

/** Every event the subject dispatched, in order. */
class RecordingEventDispatcher : IEventDispatcher {
    val events = mutableListOf<Event>()

    override fun dispatch(event: Event) {
        events += event
    }
}
