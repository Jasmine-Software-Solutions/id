package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Events related to membership invitations and acceptance.
 */
sealed interface InvitationEvent : Event

data class InvitationCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val invitationId: UUID,
    val email: String,
    val tenantId: UUID,
    val tenantName: String,
) : InvitationEvent

data class InvitationAcceptedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val invitationId: UUID,
    val email: String,
    val accountId: UUID,
    val tenantId: UUID,
    val tenantName: String,
) : InvitationEvent
