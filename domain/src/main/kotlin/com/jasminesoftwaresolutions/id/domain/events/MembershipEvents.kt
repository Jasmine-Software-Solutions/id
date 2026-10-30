package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Events related to Tenant Membership lifecycle and administration.
 */
sealed interface MembershipEvent : Event

data class MembershipCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val membershipId: UUID,
    val accountId: UUID,
    val accountEmail: String,
    val tenantId: UUID,
    val tenantName: String,
) : MembershipEvent

data class MembershipDeletedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val membershipId: UUID,
    val accountId: UUID,
    val accountEmail: String,
    val tenantId: UUID,
    val tenantName: String,
) : MembershipEvent

data class MemberProfileUpdatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val membershipId: UUID,
    val accountId: UUID,
    val previousFirstName: String?,
    val newFirstName: String?,
    val previousLastName: String?,
    val newLastName: String?,
    val previousEmail: String?,
    val newEmail: String?,
) : MembershipEvent

data class MemberTOTPDisabledEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val membershipId: UUID,
    val accountId: UUID,
    val accountEmail: String,
) : MembershipEvent
