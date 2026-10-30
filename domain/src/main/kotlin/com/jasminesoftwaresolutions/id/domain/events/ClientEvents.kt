package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Events related to OAuth2 Client lifecycle and secret management.
 */
sealed interface ClientEvent : Event

data class ClientCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val clientId: UUID,
    val clientName: String,
    val confidential: Boolean,
    val logoUri: String?,
    val initiateLoginUri: String?,
) : ClientEvent

data class ClientUpdatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val clientId: UUID,
    val previousName: String?,
    val newName: String?,
    val previousConfidential: Boolean?,
    val newConfidential: Boolean?,
    val previousSuspended: Boolean?,
    val newSuspended: Boolean?,
    val previousLogoUri: String?,
    val newLogoUri: String?,
    val previousInitiateLoginUri: String?,
    val newInitiateLoginUri: String?,
) : ClientEvent

data class ClientDeletedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val clientId: UUID,
    val clientName: String,
) : ClientEvent

data class ClientSecretResetEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val clientId: UUID,
    val clientName: String,
) : ClientEvent

data class ClientScopesManagedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val clientId: UUID,
    val clientName: String,
    /** Scopes that were added in this operation. */
    val addedScopes: List<String>,
    /** Scopes that were removed in this operation. */
    val removedScopes: List<String>,
) : ClientEvent
