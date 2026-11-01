package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Events related to Tenant lifecycle.
 */
sealed interface TenantEvent : Event

data class TenantCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val tenantId: UUID,
    val name: String,
    val suspended: Boolean,
) : TenantEvent

data class TenantUpdatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val tenantId: UUID,
    val previousName: String?,
    val newName: String?,
    val previousSuspended: Boolean?,
    val newSuspended: Boolean?,
) : TenantEvent

data class TenantDeletedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val tenantId: UUID,
    val name: String,
) : TenantEvent
