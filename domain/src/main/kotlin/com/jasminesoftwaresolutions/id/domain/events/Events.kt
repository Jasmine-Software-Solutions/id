package com.jasminesoftwaresolutions.id.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Base type for all domain events emitted by ID.
 *
 * Every event carries the Principal-Who / Action-What / Resource-Where triple
 * that was authorized via [com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService.evaluate]
 * before the action was performed.
 *
 * Events are designed for serialization (e.g. JSON audit log, event bus) and
 * must carry enough context to be meaningfully inspected without querying the
 * live database.
 *
 * [eventType] is the simple class name (e.g. "AccountCreatedEvent") and serves
 * as the polymorphic discriminator for deserialization.
 */
sealed interface Event {
    /** Unique identifier for this event instance. Defaults to a random UUID. */
    val eventId: UUID

    /** Polymorphic discriminator — simple class name, e.g. "AccountCreatedEvent". */
    val eventType: String get() = this::class.simpleName!!

    /** The UUID of the principal (account or client) that performed the action. */
    val principalId: UUID

    /** The action constant from [com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions] (e.g. "id.jasmine.software:CreateAccount"). */
    val action: String

    /** A human-readable label for the resource being acted upon, consistent with [com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources]. */
    val resource: String

    /** UTC instant at which the event was produced. */
    val timestamp: Instant
}

/**
 * Dispatches domain events to external systems (e.g. SNS, event bus).
 *
 * Implementations are responsible for serialization and transport.
 * Dispatch is fire-and-forget — callers should not block on the result,
 * and failures must be handled internally (e.g. logged) by the implementation.
 */
interface IEventDispatcher {
    /**
     * Publish [event] to the configured event destination.
     *
     * This method should return quickly. Implementations must not throw
     * exceptions that would disrupt the caller's business transaction.
     */
    fun dispatch(event: Event)
}

/**
 * A long-lived background worker that can be started and stopped.
 *
 * Workers consume events (e.g. from SQS) or perform periodic tasks
 * (e.g. key rotation, cleanup). Implementations should block in [start]
 * until [stop] is called, or use a looping/polling pattern internally.
 */
interface IWorker {
    /** Begin processing. May block the calling thread (run in a coroutine or dedicated thread). */
    fun start()

    /** Signal the worker to shut down gracefully. */
    fun stop()
}