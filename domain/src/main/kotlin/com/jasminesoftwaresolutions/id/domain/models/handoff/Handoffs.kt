package com.jasminesoftwaresolutions.id.domain.models.handoff

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import java.net.URI
import java.time.Instant
import java.util.*

/**
 * The lifecycle of a cross-device sign-in handoff.
 *
 * Exactly one non-superseded handoff may exist for an original ID session:
 * [AWAITING_SCAN] and [PENDING_CONFIRMATION] describe the live one, and
 * [CONFIRMED]/[CLAIMED] describe a completed one that a later embed load may
 * supersede. A [SUPERSEDED] handoff is inert.
 */
enum class HandoffStatus {
    AWAITING_SCAN,
    PENDING_CONFIRMATION,
    CONFIRMED,
    CLAIMED,
    SUPERSEDED,
}

/**
 * A request to sign a second device in to [originalSession]'s account.
 *
 * The handoff carries the authorization parameters the *first* embed instance
 * supplied — [client], [redirectUri], [state], [scopes] and [nonce] — so a
 * later instance cannot redirect the eventual code elsewhere. It lives until
 * [expiresAt], which is the original session's expiry.
 *
 * [scanToken] is the raw token handed to the scanning device. Like
 * [com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSessionCode],
 * only its digest is persisted: the getter throws once the entity has been
 * read back, and [verifyScanToken] compares a presented token against the
 * stored digest.
 */
interface IHandoff : IIdentified, ICreated, IExpires {
    var originalSession: IHashedSession
    var client: IClient

    var redirectUri: URI
    var state: String?
    var scopes: Set<IScope>
    var nonce: String?

    var status: HandoffStatus

    var scanToken: String
    var scannedAt: Instant?
    var scanUserAgent: String?
    var scanIpAddress: String?

    var confirmedAt: Instant?
    var claimedAt: Instant?

    /** The +1 session this handoff created, if it has been claimed. */
    var newSessionId: UUID?

    /**
     * The +1 session of the handoff this one replaced, if any. It stays
     * active until this handoff is confirmed, at which point it is revoked.
     */
    var previousSessionId: UUID?

    fun verifyScanToken(token: String): Boolean
}
