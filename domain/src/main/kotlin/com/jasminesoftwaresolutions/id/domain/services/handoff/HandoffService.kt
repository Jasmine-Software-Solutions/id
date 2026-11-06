package com.jasminesoftwaresolutions.id.domain.services.handoff

import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.handoff.HandoffStatus
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoff
import java.net.URI
import java.time.Instant
import java.util.*

/** A freshly issued per-instance QR code. The raw token is never persisted. */
data class HandoffQr(
    val token: String,
    val expiresAt: Instant,
)

/**
 * The outcome of a create-or-attach call.
 *
 * [qr] is present only while the shared handoff is [HandoffStatus.AWAITING_SCAN]:
 * attach to a handoff that is already awaiting a confirmation and it has no QR
 * to issue; attach to one that is awaiting a scan and this instance gets its own.
 */
data class HandoffCreation(
    val handoff: IHandoff,
    val qr: HandoffQr?,
    val created: Boolean,
)

/** A scan that moved the handoff to `pending_confirmation`. */
data class HandoffScan(
    val handoff: IHandoff,
    val token: String,
)

/** A completed claim: the new session, its delegated session, and its raw code. */
data class HandoffClaim(
    val session: ISession,
    val delegatedSession: IDelegatedSession,
    val code: String,
)

/** What the scanning device is told about its own scan. */
enum class HandoffScanStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CLAIMED,
    SUPERSEDED,
    EXPIRED,
}

interface IHandoffService {
    /**
     * Create a handoff for [originalSession], or attach to the live one.
     *
     * The first instance's parameters are authoritative; later instances are
     * validated by the caller but do not alter the handoff.
     */
    fun createOrAttach(
        originalSession: IHashedSession,
        client: IClient,
        redirectUri: URI,
        state: String?,
        scopes: Set<IScope>,
        nonce: String?,
    ): HandoffCreation

    /** Issue a fresh QR code for the calling instance. Only while awaiting a scan. */
    fun issueQr(handoff: IHandoff): HandoffQr

    /** Decline the pending scan: return to awaiting a scan with fresh QR codes. */
    fun reject(handoff: IHandoff): HandoffQr

    /** Approve the pending scan. Idempotent once confirmed/claimed. */
    fun confirm(handoff: IHandoff): IHandoff

    /** Drop this handoff's +1 device and return to awaiting a scan. */
    fun changeDevices(handoff: IHandoff): HandoffQr

    /** Mark a QR scan pending confirmation, recording who scanned it. */
    fun scan(handoffId: UUID, qrToken: String, userAgent: String?, ipAddress: String?): HandoffScan

    /**
     * What the scanning device should show, or null when [scanToken] no longer
     * matches this handoff's pending scan.
     */
    fun scanStatus(handoff: IHandoff, scanToken: String?): HandoffScanStatus?

    /** Complete a confirmed handoff: create the +1 session and its code. */
    fun claim(handoff: IHandoff, userAgent: String?, ipAddress: String?): HandoffClaim
}

/** The handoff does not exist (or does not belong to the caller's session). */
class HandoffNotFoundException(message: String = "Handoff not found") : RuntimeException(message)

/** The handoff or the original ID session has expired. */
class HandoffExpiredException(message: String = "Handoff expired") : RuntimeException(message)

/** The handoff moved on: another instance won the scan or the transition. */
class HandoffConflictException(message: String = "Handoff state conflict") : RuntimeException(message)
