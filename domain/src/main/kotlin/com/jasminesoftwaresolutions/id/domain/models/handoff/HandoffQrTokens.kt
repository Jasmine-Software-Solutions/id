package com.jasminesoftwaresolutions.id.domain.models.handoff

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.repositories.IIdentifiedRepository
import java.time.Instant
import java.util.*

/**
 * One displayed QR code. Every embed instance owns its own row so instances can
 * rotate independently, and an old row stays valid until its own [expiresAt].
 *
 * Only the SHA-256 digest of the raw token is stored; [token] is write-once for
 * a newly created row, and [verify] compares a presented token to the digest.
 */
interface IHandoffQrToken : IIdentified, ICreated, IExpires {
    var handoff: IHandoff
    var token: String

    fun verify(token: String): Boolean
}

interface IHandoffQrTokenRepository : IIdentifiedRepository<IHandoffQrToken> {
    /** Persist [rawToken] (hashed at rest) for [handoff], valid until [expiresAt]. */
    fun create(handoff: IHandoff, rawToken: String, expiresAt: Instant): IHandoffQrToken

    /** The unexpired row for [handoff] whose digest matches [rawToken], if any. */
    fun findValid(handoffId: UUID, rawToken: String, now: Instant = Instant.now()): IHandoffQrToken?

    fun deleteForHandoff(handoffId: UUID)

    /** Opportunistic cleanup, called when a new QR code is issued. */
    fun deleteExpired(now: Instant = Instant.now())
}
