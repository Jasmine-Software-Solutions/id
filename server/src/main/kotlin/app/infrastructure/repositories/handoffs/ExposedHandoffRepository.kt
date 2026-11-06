package app.infrastructure.repositories.handoffs

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedSessionRepository
import app.infrastructure.repositories.clients.ExposedClientRepository
import app.infrastructure.util.*
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.handoff.HandoffStatus
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoff
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IHandoffRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IScopeRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.time.Instant
import java.util.*

class ExposedHandoffRepository(
    val hashFunction: IHashFunction,
    val sessionRepository: ISessionRepository,
    val clientRepository: IClientRepository,
    val scopeRepository: IScopeRepository,
    val scopeRegistry: IScopeRegistry<out IScope>,
) : ExposedIdentifiedEntityRepository<IHandoff, ExposedHandoffRepository.Handoff>(Table),
    IHandoffRepository {

    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?) =
        Handoff(row, insert, update)

    override fun transform(entity: IHandoff): Handoff? = entity as? Handoff

    override fun clone(src: Handoff, dest: Handoff) {
        // The raw scan token is transient; on create there is none, which is
        // why this is best-effort in the same way as a delegated session code.
        runCatching { dest.scanToken = src.scanToken }
    }

    override fun findOwned(handoffId: UUID, originalSessionId: UUID): IHandoff? = transaction {
        Table.select { (Table.id eq handoffId) and (Table.originalSession eq originalSessionId) }
            .firstOrNull()
            ?.let { read(it, null, null) }
    }

    override fun findActiveForSession(originalSessionId: UUID): IHandoff? = transaction {
        Table.select {
            (Table.originalSession eq originalSessionId) and
                (Table.status inList ACTIVE_STATUSES)
        }
            .orderBy(Table.createdAt, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.let { read(it, null, null) }
    }

    override fun findLatestCompletedForSession(originalSessionId: UUID): IHandoff? = transaction {
        Table.select {
            (Table.originalSession eq originalSessionId) and
                (Table.status inList COMPLETED_STATUSES)
        }
            .orderBy(Table.createdAt, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.let { read(it, null, null) }
    }

    override fun createHandoff(
        originalSession: IHashedSession,
        client: IClient,
        redirectUri: URI,
        state: String?,
        scopes: Set<IScope>,
        nonce: String?,
        expiresAt: Instant,
        previousSessionId: UUID?,
    ): IHandoff = create {
        this.originalSession = originalSession
        this.client = client
        this.redirectUri = redirectUri
        this.state = state
        this.scopes = scopes
        this.nonce = nonce
        this.expiresAt = expiresAt
        this.previousSessionId = previousSessionId
        this.status = HandoffStatus.AWAITING_SCAN
    }

    override fun supersede(handoffId: UUID): IHandoff? = transaction {
        // Capture the +1 session before the transition: a caller may carry it
        // forward as the replacement handoff's revocation target.
        val updated = Table.update({ (Table.id eq handoffId) and (Table.status inList COMPLETED_STATUSES) }) {
            it[Table.status] = HandoffStatus.SUPERSEDED.name
        }

        if (updated != 1) return@transaction null
        return@transaction findById(handoffId)
    }

    override fun tryBeginScan(
        handoffId: UUID,
        scanToken: String,
        scannedAt: Instant,
        userAgent: String?,
        ipAddress: String?,
    ): IHandoff? = transaction {
        val updated = Table.update({
            (Table.id eq handoffId) and
                (Table.status eq HandoffStatus.AWAITING_SCAN.name) and
                (Table.expiresAt greater scannedAt.toEpochMilli())
        }) {
            it[Table.status] = HandoffStatus.PENDING_CONFIRMATION.name
            it[Table.scanTokenHash] = hashFunction.hash(scanToken.toByteArray())
            it[Table.scannedAt] = scannedAt.toEpochMilli()
            it[Table.scanUserAgent] = userAgent
            it[Table.scanIpAddress] = ipAddress
            it[Table.confirmedAt] = null
            it[Table.claimedAt] = null
        }

        if (updated != 1) return@transaction null
        return@transaction findById(handoffId)
    }

    override fun tryConfirm(handoffId: UUID, confirmedAt: Instant): IHandoff? = transaction {
        val updated = Table.update({
            (Table.id eq handoffId) and (Table.status eq HandoffStatus.PENDING_CONFIRMATION.name)
        }) {
            it[Table.status] = HandoffStatus.CONFIRMED.name
            it[Table.confirmedAt] = confirmedAt.toEpochMilli()
        }

        if (updated != 1) return@transaction null
        return@transaction findById(handoffId)
    }

    override fun tryReject(handoffId: UUID): IHandoff? = transaction {
        val updated = Table.update({
            (Table.id eq handoffId) and (Table.status eq HandoffStatus.PENDING_CONFIRMATION.name)
        }) {
            it[Table.status] = HandoffStatus.AWAITING_SCAN.name
            // scan_token_hash stays: the scanning device polls with it and is
            // told its request was declined rather than that the scan vanished.
            it[Table.scannedAt] = null
            it[Table.scanUserAgent] = null
            it[Table.scanIpAddress] = null
            it[Table.confirmedAt] = null
        }

        if (updated != 1) return@transaction null
        return@transaction findById(handoffId)
    }

    override fun tryClaim(handoffId: UUID, newSessionId: UUID, claimedAt: Instant): IHandoff? = transaction {
        val updated = Table.update({
            (Table.id eq handoffId) and (Table.status eq HandoffStatus.CONFIRMED.name)
        }) {
            it[Table.status] = HandoffStatus.CLAIMED.name
            it[Table.claimedAt] = claimedAt.toEpochMilli()
            it[Table.newSessionId] = newSessionId
        }

        if (updated != 1) return@transaction null
        return@transaction findById(handoffId)
    }

    override fun tryChangeDevices(handoffId: UUID): IHandoff? = transaction {
        val updated = Table.update({ (Table.id eq handoffId) and (Table.status inList COMPLETED_STATUSES) }) {
            it[Table.status] = HandoffStatus.AWAITING_SCAN.name
            it[Table.newSessionId] = null
            it[Table.scannedAt] = null
            it[Table.scanUserAgent] = null
            it[Table.scanIpAddress] = null
            it[Table.confirmedAt] = null
            it[Table.claimedAt] = null
        }

        if (updated != 1) return@transaction null
        return@transaction findById(handoffId)
    }

    inner class Handoff(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null,
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHandoff {
        private var _scanToken: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)

        override var originalSession: IHashedSession by column(
            Table.originalSession,
            EntityTransformer(ExposedSessionRepository.Table, sessionRepository),
        )

        override var client: IClient by column(
            Table.client,
            EntityTransformer(ExposedClientRepository.Table, clientRepository),
        )

        override var redirectUri: URI by column(Table.redirectUri, ExposedColumnTransformer(
            fromColumn = { URI.create(it) },
            toColumn = { it.toString() },
        ))

        override var state: String? by nullableColumn(Table.state)
        override var nonce: String? by nullableColumn(Table.nonce)

        override var scopes: Set<IScope> by column(Table.scope, ExposedColumnTransformer(
            fromColumn = { raw ->
                raw.split(" ")
                    .filter { it.isNotBlank() }
                    .mapNotNull { scopeRegistry.findById(it) ?: scopeRepository.findById(it) }
                    .toSet()
            },
            toColumn = { scopes -> scopes.map { it.id }.joinToString(" ") },
        ))

        override var status: HandoffStatus by column(Table.status, ExposedColumnTransformer(
            fromColumn = { HandoffStatus.valueOf(it) },
            toColumn = { it.name },
        ))

        override var scanToken: String
            get() = _scanToken
                ?: throw UnsupportedOperationException("IHandoff#scanToken is transient and no longer accessible")
            set(value) = hashFunction.hash(value.toByteArray()).let {
                row?.set(Table.scanTokenHash, it)
                insert?.set(Table.scanTokenHash, it)
                update?.set(Table.scanTokenHash, it)
                _scanToken = value
            }

        override var scannedAt: Instant? by nullableColumn(Table.scannedAt, NullableInstantTransformer)
        override var scanUserAgent: String? by nullableColumn(Table.scanUserAgent)
        override var scanIpAddress: String? by nullableColumn(Table.scanIpAddress)

        override var confirmedAt: Instant? by nullableColumn(Table.confirmedAt, NullableInstantTransformer)
        override var claimedAt: Instant? by nullableColumn(Table.claimedAt, NullableInstantTransformer)

        override var newSessionId: UUID? by nullableColumn(Table.newSessionId)
        override var previousSessionId: UUID? by nullableColumn(Table.previousSessionId)

        override fun verifyScanToken(token: String): Boolean {
            val hash = row?.get(Table.scanTokenHash) ?: return false
            return hashFunction.verify(token.toByteArray(), hash)
        }
    }

    object Table : UUIDTable("handoffs") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expires_at")

        val originalSession = reference("original_session", ExposedSessionRepository.Table, onDelete = ReferenceOption.CASCADE)
        val client = reference("client", ExposedClientRepository.Table, onDelete = ReferenceOption.CASCADE)

        val redirectUri = varchar("redirect_uri", 2048)
        val state = varchar("state", 2048).nullable()
        val scope = varchar("scope", 256)
        val nonce = varchar("nonce", 256).nullable()

        val status = varchar("status", 32)
        val scanTokenHash = text("scan_token_hash").nullable()
        val scannedAt = long("scanned_at").nullable()
        val scanUserAgent = varchar("scan_user_agent", 256).nullable()
        val scanIpAddress = varchar("scan_ip_address", 39).nullable()
        val confirmedAt = long("confirmed_at").nullable()
        val claimedAt = long("claimed_at").nullable()
        val newSessionId = uuid("new_session_id").nullable()
        val previousSessionId = uuid("previous_session_id").nullable()

        init {
            index(false, originalSession)
        }
    }

    private companion object {
        val ACTIVE_STATUSES = listOf(
            HandoffStatus.AWAITING_SCAN.name,
            HandoffStatus.PENDING_CONFIRMATION.name,
        )

        val COMPLETED_STATUSES = listOf(
            HandoffStatus.CONFIRMED.name,
            HandoffStatus.CLAIMED.name,
        )
    }
}
