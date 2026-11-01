package com.jasminesoftwaresolutions.id.domain.events

import com.google.gson.JsonArray
import java.time.Instant
import java.util.UUID

/**
 * Events related to Policy lifecycle, updates, and assignment management.
 */
sealed interface PolicyEvent : Event

data class PolicyCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val policyId: UUID,
    val policyName: String,
    val description: String?,
    /** The tenant this policy is scoped to, or `null` for platform scope. */
    val tenantId: UUID?,
    val document: JsonArray,
) : PolicyEvent

data class PolicyUpdatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val policyId: UUID,
    val previousName: String,
    val newName: String?,
    val previousDescription: String?,
    val newDescription: String?,
    val previousDocument: JsonArray,
    val newDocument: JsonArray?
) : PolicyEvent

data class PolicyDeletedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val policyId: UUID,
    val policyName: String,
    val policyDocument: JsonArray
) : PolicyEvent

data class PolicyAssignedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val assignmentId: UUID,
    val policyId: UUID,
    val policyName: String,
    val accountId: UUID?,
    val accountEmail: String?,
    val clientId: UUID?,
    val clientName: String?,
    val roleId: UUID?,
    val roleName: String?,
    val expiresAt: Instant?,
) : PolicyEvent

data class PolicyUnassignedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val assignmentId: UUID,
    val policyId: UUID,
    val policyName: String,
    val accountId: UUID?,
    val accountEmail: String?,
    val clientId: UUID?,
    val clientName: String?,
    val roleId: UUID?,
    val roleName: String?,
) : PolicyEvent
