package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoff
import java.net.URI
import java.time.Instant
import java.util.*

/**
 * Handoff persistence.
 *
 * The transitions are custom methods rather than the generic [update] because
 * each one is a conditional update whose `WHERE` clause carries the state it is
 * moving out of. Exactly one row changing is the concurrency guard: a lost race
 * returns null instead of overwriting another instance's decision.
 */
interface IHandoffRepository : IIdentifiedRepository<IHandoff> {
    fun findOwned(handoffId: UUID, originalSessionId: UUID): IHandoff?

    /** The live (awaiting/pending) handoff for an original session, newest first. */
    fun findActiveForSession(originalSessionId: UUID): IHandoff?

    /** The newest completed (confirmed/claimed) handoff for a session, if any. */
    fun findLatestCompletedForSession(originalSessionId: UUID): IHandoff?

    fun createHandoff(
        originalSession: IHashedSession,
        client: IClient,
        redirectUri: URI,
        state: String?,
        scopes: Set<IScope>,
        nonce: String?,
        expiresAt: Instant,
        previousSessionId: UUID?,
    ): IHandoff

    /** `confirmed|claimed → superseded`. Returns the updated row, or null if it lost the race. */
    fun supersede(handoffId: UUID): IHandoff?

    /** `awaiting_scan → pending_confirmation`, recording the scan and its raw token. */
    fun tryBeginScan(
        handoffId: UUID,
        scanToken: String,
        scannedAt: Instant,
        userAgent: String?,
        ipAddress: String?,
    ): IHandoff?

    /** `pending_confirmation → confirmed`. */
    fun tryConfirm(handoffId: UUID, confirmedAt: Instant): IHandoff?

    /**
     * `pending_confirmation → awaiting_scan`. The scan digest is deliberately
     * kept so the scanning device can be told its request was declined.
     */
    fun tryReject(handoffId: UUID): IHandoff?

    /** `confirmed → claimed`, recording the session the claim created. */
    fun tryClaim(handoffId: UUID, newSessionId: UUID, claimedAt: Instant): IHandoff?

    /** `confirmed|claimed → awaiting_scan`, dropping the +1 session reference. */
    fun tryChangeDevices(handoffId: UUID): IHandoff?
}
