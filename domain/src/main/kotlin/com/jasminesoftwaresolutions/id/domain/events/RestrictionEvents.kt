package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Events emitted by the server when a restriction is placed or lifted.
 *
 * [RestrictionPlacedEvent.triggerEventId] optionally references the [SecurityEvent]
 * that triggered the restriction.
 */
sealed interface RestrictionEvent : Event

data class RestrictionPlacedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val restrictionId: UUID,
    /** The [Event.eventId] of the [SecurityEvent] that triggered this restriction. Null when created manually. */
    val triggerEventId: UUID?,
    val type: String,
    val targetType: String,
    val targetId: String,
    val durationMinutes: Int?,
) : RestrictionEvent

data class RestrictionLiftedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val principalId: UUID,
    override val action: String,
    override val resource: String,
    override val timestamp: Instant,
    val restrictionId: UUID,
    val type: String,
    val targetType: String,
    val targetId: String,
) : RestrictionEvent
