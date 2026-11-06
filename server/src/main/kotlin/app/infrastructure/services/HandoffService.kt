package app.infrastructure.services

import app.infrastructure.repositories.accounts.ExposedSessionRepository
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.SessionCreatedEvent
import com.jasminesoftwaresolutions.id.domain.events.SessionRevokedEvent
import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.handoff.HandoffStatus
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoff
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoffQrTokenRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IDelegatedSessionRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IHandoffRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.ISessionService
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffClaim
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffConflictException
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffCreation
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffExpiredException
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffNotFoundException
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffQr
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffScan
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffScanStatus
import com.jasminesoftwaresolutions.id.domain.services.handoff.IHandoffService
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.time.Instant
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Coordinates the handoff state machine.
 *
 * Deliberately knows nothing about HTTP or Witchwood: permission evaluation
 * lives in the controllers, and this class only moves a handoff through its
 * states and, at claim time, mints the new session and code.
 */
class StandardHandoffService(
    private val handoffRepository: IHandoffRepository,
    private val qrTokenRepository: IHandoffQrTokenRepository,
    private val sessionRepository: ISessionRepository,
    private val sessionService: ISessionService,
    private val delegatedSessionRepository: IDelegatedSessionRepository,
    private val eventDispatcher: IEventDispatcher,
) : IHandoffService {

    /**
     * Serialises create-or-attach inside this process. The database row lock
     * below is the real guard across instances; this one exists because the
     * in-process SQLite used by tests ignores `SELECT … FOR UPDATE`, and it
     * costs nothing for the rare embed load.
     */
    private val creationLock = Any()

    override fun createOrAttach(
        originalSession: IHashedSession,
        client: IClient,
        redirectUri: URI,
        state: String?,
        scopes: Set<IScope>,
        nonce: String?,
    ): HandoffCreation = synchronized(creationLock) {
        transaction {
            if (originalSession.expiresAt <= Instant.now())
                throw HandoffExpiredException()

            lockOriginalSession(originalSession.id)

            val active = handoffRepository.findActiveForSession(originalSession.id)
            if (active != null) {
                val qr = if (active.status == HandoffStatus.AWAITING_SCAN) issueQrFor(active) else null
                return@transaction HandoffCreation(active, qr, created = false)
            }

            // A completed handoff is replaced: its +1 session becomes this
            // handoff's revocation target and stays active until confirmation.
            val completed = handoffRepository.findLatestCompletedForSession(originalSession.id)
            val previousSessionId = completed
                ?.let { handoffRepository.supersede(it.id) }
                ?.newSessionId

            val handoff = handoffRepository.createHandoff(
                originalSession = originalSession,
                client = client,
                redirectUri = redirectUri,
                state = state,
                scopes = scopes,
                nonce = nonce,
                expiresAt = originalSession.expiresAt,
                previousSessionId = previousSessionId,
            )

            return@transaction HandoffCreation(handoff, issueQrFor(handoff), created = true)
        }
    }

    override fun issueQr(handoff: IHandoff): HandoffQr = transaction {
        val current = handoffRepository.findById(handoff.id) ?: throw HandoffNotFoundException()
        ensureNotExpired(current)
        if (current.status != HandoffStatus.AWAITING_SCAN)
            throw HandoffConflictException("Handoff is no longer awaiting a scan")

        return@transaction issueQrFor(current)
    }

    override fun reject(handoff: IHandoff): HandoffQr = transaction {
        val current = handoffRepository.findById(handoff.id) ?: throw HandoffNotFoundException()
        ensureNotExpired(current)

        val updated = handoffRepository.tryReject(current.id)
            ?: throw HandoffConflictException("No scan is pending confirmation")

        qrTokenRepository.deleteForHandoff(updated.id)
        return@transaction issueQrFor(updated)
    }

    override fun confirm(handoff: IHandoff): IHandoff = transaction {
        val current = handoffRepository.findById(handoff.id) ?: throw HandoffNotFoundException()

        // Idempotent: a second click in another tab is a no-op.
        if (current.status == HandoffStatus.CONFIRMED || current.status == HandoffStatus.CLAIMED)
            return@transaction current

        ensureNotExpired(current)

        val updated = handoffRepository.tryConfirm(current.id, Instant.now())
            ?: throw HandoffConflictException("No scan is pending confirmation")

        revokePreviousSession(updated)
        return@transaction updated
    }

    override fun changeDevices(handoff: IHandoff): HandoffQr = transaction {
        val current = handoffRepository.findById(handoff.id) ?: throw HandoffNotFoundException()
        ensureNotExpired(current)

        // Read before the guarded update clears the reference.
        val newSessionId = current.newSessionId

        val updated = handoffRepository.tryChangeDevices(current.id)
            ?: throw HandoffConflictException("The device has not been signed in")

        if (newSessionId != null) revokeSession(current, newSessionId)

        qrTokenRepository.deleteForHandoff(updated.id)
        return@transaction issueQrFor(updated)
    }

    override fun scan(
        handoffId: UUID,
        qrToken: String,
        userAgent: String?,
        ipAddress: String?,
    ): HandoffScan = transaction {
        val handoff = handoffRepository.findById(handoffId) ?: throw HandoffNotFoundException()
        ensureNotExpired(handoff)

        if (handoff.status != HandoffStatus.AWAITING_SCAN)
            throw HandoffConflictException("Handoff is no longer awaiting a scan")

        qrTokenRepository.findValid(handoffId, qrToken)
            ?: throw HandoffNotFoundException("Invalid or expired QR code")

        val rawScanToken = SecureToken()
        val updated = handoffRepository.tryBeginScan(
            handoffId = handoffId,
            scanToken = rawScanToken,
            scannedAt = Instant.now(),
            userAgent = userAgent?.take(MAX_USER_AGENT_LENGTH),
            ipAddress = ipAddress?.take(MAX_IP_ADDRESS_LENGTH),
        ) ?: throw HandoffConflictException("Another device already scanned")

        return@transaction HandoffScan(updated, rawScanToken)
    }

    override fun scanStatus(handoff: IHandoff, scanToken: String?): HandoffScanStatus? {
        if (scanToken == null) return null
        if (!handoff.verifyScanToken(scanToken)) return null

        if (handoff.expiresAt <= Instant.now()) return HandoffScanStatus.EXPIRED

        return when (handoff.status) {
            HandoffStatus.AWAITING_SCAN -> HandoffScanStatus.REJECTED
            HandoffStatus.PENDING_CONFIRMATION -> HandoffScanStatus.PENDING
            HandoffStatus.CONFIRMED -> HandoffScanStatus.CONFIRMED
            HandoffStatus.CLAIMED -> HandoffScanStatus.CLAIMED
            HandoffStatus.SUPERSEDED -> HandoffScanStatus.SUPERSEDED
        }
    }

    override fun claim(handoff: IHandoff, userAgent: String?, ipAddress: String?): HandoffClaim = transaction {
        val current = handoffRepository.findById(handoff.id) ?: throw HandoffNotFoundException()

        if (current.status == HandoffStatus.CLAIMED || current.status == HandoffStatus.SUPERSEDED)
            throw HandoffConflictException("Handoff was already claimed or replaced")
        ensureNotExpired(current)
        if (current.status != HandoffStatus.CONFIRMED)
            throw HandoffConflictException("Handoff has not been confirmed")

        val account = current.originalSession.account

        val created = sessionService.create(account) {
            this.userAgent = userAgent?.take(MAX_USER_AGENT_LENGTH)
            this.ipAddress = ipAddress?.take(MAX_IP_ADDRESS_LENGTH)
        }
        val session = created as? IHashedSession
            ?: throw IllegalStateException("The session service must return a hashed session")
        val newSession: ISession = created

        // The guarded update is the single winner across concurrent claims; a
        // loser throws and rolls this transaction (including the session) back.
        val claimed = handoffRepository.tryClaim(current.id, session.id, Instant.now())
            ?: throw HandoffConflictException("Handoff was already claimed or replaced")

        eventDispatcher.dispatch(
            SessionCreatedEvent(
                principalId = account.id,
                action = "id.jasmine.software:SessionCreated",
                resource = IdResources.session(account.id, session.id).toString(),
                timestamp = Instant.now(),
                sessionId = session.id,
                accountId = account.id,
                ipAddress = ipAddress,
                userAgent = userAgent,
                expiresAt = session.expiresAt,
            )
        )

        val code = SecureToken()
        val delegatedSession = delegatedSessionRepository.create {
            this.session = session
            this.client = claimed.client
            this.redirectUri = claimed.redirectUri
            this.scopes = claimed.scopes
            this.nonce = claimed.nonce
            this.handoffId = claimed.id
            this.code.code = code
            this.code.expiresAt = Instant.now().plusMillis(AUTHORIZATION_CODE_LIFETIME.inWholeMilliseconds)
            this.expiresAt = newSession.expiresAt
        }

        return@transaction HandoffClaim(newSession, delegatedSession, code)
    }

    /**
     * A fresh QR row valid for the smaller of the QR lifetime and the
     * handoff's own expiry. Rotation does not extend the handoff.
     */
    private fun issueQrFor(handoff: IHandoff): HandoffQr {
        val now = Instant.now()
        ensureNotExpired(handoff)

        if (handoff.status != HandoffStatus.AWAITING_SCAN)
            throw HandoffConflictException("Handoff is no longer awaiting a scan")

        qrTokenRepository.deleteExpired(now)

        val expiresAt = minOf(
            now.plusMillis(QR_LIFETIME.inWholeMilliseconds),
            handoff.expiresAt,
        )

        val rawToken = SecureToken()
        qrTokenRepository.create(handoff, rawToken, expiresAt)

        return HandoffQr(rawToken, expiresAt)
    }

    /**
     * Delete the +1 session belonging to the handoff this one replaced. This is
     * the point where "sign a new device in" also retires the previous device.
     */
    private fun revokePreviousSession(handoff: IHandoff) {
        val previousSessionId = handoff.previousSessionId ?: return
        revokeSession(handoff, previousSessionId)
    }

    private fun revokeSession(handoff: IHandoff, sessionId: UUID) {
        val session = sessionRepository.findById(sessionId) ?: return
        val accountId = handoff.originalSession.account.id

        // Never delete a session that does not belong to this account, even if
        // a bad row were to point at one.
        if (session.account.id != accountId) return

        sessionRepository.delete(session)

        eventDispatcher.dispatch(
            SessionRevokedEvent(
                principalId = accountId,
                action = IdActions.ACCEPT_HANDOFF,
                resource = IdResources.account(accountId).toString(),
                timestamp = Instant.now(),
                sessionId = sessionId,
                accountId = accountId,
                revokedByPrincipalId = accountId,
                wasSelfRevocation = false,
            )
        )
    }

    private fun ensureNotExpired(handoff: IHandoff) {
        if (handoff.expiresAt <= Instant.now()) throw HandoffExpiredException()
    }

    /**
     * `SELECT … FOR UPDATE` on the original session row. On SQLite — tests, and
     * any embedded deployment — Exposed omits the clause and the in-process
     * creation lock above provides the serialisation instead.
     */
    private fun lockOriginalSession(sessionId: UUID) {
        ExposedSessionRepository.Table
            .select { ExposedSessionRepository.Table.id eq sessionId }
            .forUpdate()
            .firstOrNull()
    }

    companion object {
        /** How long one displayed QR code stays valid. */
        val QR_LIFETIME: Duration = 2.minutes

        /** How long before expiry the client is expected to rotate. */
        val QR_ROTATION_LEAD: Duration = 30.seconds

        /** Lifetime of the authorization code a claim issues. */
        val AUTHORIZATION_CODE_LIFETIME: Duration = 10.minutes

        /** Column widths: `handoffs.scan_user_agent` / `handoffs.scan_ip_address`. */
        const val MAX_USER_AGENT_LENGTH = 256
        const val MAX_IP_ADDRESS_LENGTH = 39
    }
}
