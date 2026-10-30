package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Events related to the authentication flow lifecycle.
 *
 * Consumed by:
 * - Security worker (brute force detection, impossible travel, account locking)
 * - Email worker (magic link delivery)
 * - Witchwood syncer (future: lazy principal loading on login)
 */
sealed interface AuthenticationEvent : Event

data class AuthenticationFlowStartedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val flowId: UUID,
    val initialStep: String,
    val ipAddress: String?,
) : AuthenticationEvent

data class AuthenticationStepCompletedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val flowId: UUID,
    /** FQDN of the step that was just completed, e.g. "com.jasminesoftwaresolutions.id:enter_email_address". */
    val step: String,
    val accountId: UUID?,
    val email: String?,
) : AuthenticationEvent

data class AuthenticationSucceededEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val flowId: UUID,
    val accountId: UUID,
    val email: String,
    val sessionId: UUID,
    val ipAddress: String?,
    val userAgent: String?,
) : AuthenticationEvent

data class InvalidPasswordAtAuthenticationEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val flowId: UUID,
    val accountId: UUID?,
    val email: String?,
    val ipAddress: String?,
) : AuthenticationEvent

data class ValidPasswordAtAuthenticationEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val flowId: UUID,
    val accountId: UUID,
    val email: String,
    val ipAddress: String?,
    /** SHA-1 hex hash of the plaintext password for HIBP k-anonymity lookup. */
    val passwordHash: String,
) : AuthenticationEvent

data class InvalidTOTPAtAuthenticationEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val flowId: UUID,
    val accountId: UUID,
    val ipAddress: String?,
) : AuthenticationEvent

data class MagicLinkRequestedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val flowId: UUID,
    val email: String,
    val magicLinkId: UUID,
    val token: String,
) : AuthenticationEvent

data class MagicLinkVerifiedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val magicLinkId: UUID,
    val flowId: UUID,
    val accountId: UUID,
) : AuthenticationEvent
