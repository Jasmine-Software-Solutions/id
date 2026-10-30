package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Events related to session lifecycle.
 *
 * Consumed by:
 * - Security worker (concurrent session limits, session tracking)
 * - Witchwood syncer (future: lazy principal loading)
 */
sealed interface SessionEvent : Event

data class SessionCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val sessionId: UUID,
    val accountId: UUID,
    val ipAddress: String?,
    val userAgent: String?,
    val expiresAt: Instant,
) : SessionEvent

data class SessionRevokedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val sessionId: UUID,
    val accountId: UUID,
    /** The principal that revoked the session (may differ from accountId for admin revocations). */
    val revokedByPrincipalId: UUID,
    /** True if the session owner revoked their own session. */
    val wasSelfRevocation: Boolean,
) : SessionEvent

data class AllSessionsRevokedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val revokedCount: Int,
) : SessionEvent
