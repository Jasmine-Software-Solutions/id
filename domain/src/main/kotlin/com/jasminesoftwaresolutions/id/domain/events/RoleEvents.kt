package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Events related to Role lifecycle, updates, and assignment management.
 */
sealed interface RoleEvent : Event

data class RoleCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val roleId: UUID,
    val roleName: String,
    val description: String?,
    /** The tenant this role is scoped to, or `null` for platform scope. */
    val tenantId: UUID?,
) : RoleEvent

data class RoleUpdatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val roleId: UUID,
    val previousName: String,
    val newName: String,
    val previousDescription: String?,
    val newDescription: String?,
) : RoleEvent

data class RoleDeletedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val roleId: UUID,
    val roleName: String,
) : RoleEvent

data class RoleAssignedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val assignmentId: UUID,
    val roleId: UUID,
    val roleName: String,
    val accountId: UUID?,
    val accountEmail: String?,
    val clientId: UUID?,
    val clientName: String?,
    val expiresAt: Instant?,
) : RoleEvent

data class RoleUnassignedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val assignmentId: UUID,
    val roleId: UUID,
    val roleName: String,
    val accountId: UUID?,
    val accountEmail: String?,
    val clientId: UUID?,
    val clientName: String?,
) : RoleEvent
