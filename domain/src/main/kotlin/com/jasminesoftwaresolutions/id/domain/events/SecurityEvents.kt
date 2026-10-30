package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Security events emitted by the security worker when a threat is detected.
 *
 * The worker emits these to SNS, then optionally calls POST /api/v1/restrictions
 * with the [Event.eventId] to place a restriction. The restriction record references
 * this event for full context (reason, metadata).
 */
sealed interface SecurityEvent : Event

data class PasswordBruteForceAttemptedSecurityEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID?,
    val email: String?,
    val ipAddress: String,
    val failureCount: Int,
    val windowMinutes: Int,
) : SecurityEvent

data class PasswordBruteForceSucceededSecurityEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val ipAddress: String,
) : SecurityEvent

data class ImpossibleTravelDetectedSecurityEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val previousIp: String,
    val previousLocation: SecurityEventLocation?,
    val previousLoginAt: Instant,
    val currentIp: String,
    val currentLocation: SecurityEventLocation?,
    val currentLoginAt: Instant,
) : SecurityEvent

data class ConcurrentSessionLimitExceededSecurityEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val activeSessionCount: Int,
    val limit: Int,
) : SecurityEvent

data class RapidAccountCreationDetectedSecurityEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val ipAddress: String,
    val creationCount: Int,
    val windowMinutes: Int,
) : SecurityEvent

data class EnumerationDetectedSecurityEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val ipAddress: String,
    val flowCount: Int,
    val windowMinutes: Int,
) : SecurityEvent

data class TOTPDisabledFromUnusualLocationSecurityEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val enabledIp: String,
    val enabledLocation: SecurityEventLocation?,
    val disabledIp: String,
    val disabledLocation: SecurityEventLocation?,
) : SecurityEvent

data class CredentialStuffingSucceededSecurityEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val accountId: UUID,
    val email: String,
    val ipAddress: String,
    /** 5-character SHA-1 prefix sent to HIBP (first 5 hex chars of passwordHash). */
    val hashPrefix: String,
) : SecurityEvent

data class SecurityEventLocation(
    val country: String?,
    val city: String?,
    val latitude: Double?,
    val longitude: Double?,
)
