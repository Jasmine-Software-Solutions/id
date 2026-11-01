package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Events related to Account lifecycle and profile operations.
 */
sealed interface AccountEvent : Event

data class AccountCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val emailVerified: Boolean,
    val ipAddress: String?,
) : AccountEvent

data class AccountProfileUpdatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val previousFirstName: String?,
    val newFirstName: String?,
    val previousLastName: String?,
    val newLastName: String?,
) : AccountEvent

data class AccountPasswordSetEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    /** True if this account had a prior password that was overwritten. */
    val overwroteExisting: Boolean,
) : AccountEvent

data class AccountDeletedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val email: String,
) : AccountEvent

data class AccountTOTPEnabledEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val ipAddress: String?,
) : AccountEvent

data class AccountTOTPDisabledEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val ipAddress: String?,
) : AccountEvent

data class AccountTOTPConfirmedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
) : AccountEvent

data class AccountEmailChangeStartedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val newEmail: String,
) : AccountEvent

data class AccountEmailChangeVerifiedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val previousEmail: String,
    val newEmail: String,
) : AccountEvent

data class AccountListedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    /** Account IDs visible to the principal (scoped listing result). */
    val visibleAccountIds: List<UUID>,
    val totalCount: Int,
) : AccountEvent
