package com.jasminesoftwaresolutions.idinterfaces.application.handoff

import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.handoff.HandoffStatus
import com.jasminesoftwaresolutions.id.domain.repositories.IHandoffRepository
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext

/**
 * The second `AcceptHandoff` check, applied when a handoff-issued code is
 * exchanged for tokens.
 *
 * The check at claim time proves the user approved the scan while the client
 * held the permission; this one proves nothing changed between claim and
 * exchange, and binds the code to the client that created the handoff. Any
 * uncertainty — a missing handoff row, a status that is not `claimed` — is
 * [Decision.INVALID]: a handoff code must never fall through to the normal
 * authorization-code path.
 */
class HandoffExchangeGuard(
    private val handoffRepository: IHandoffRepository,
    private val evaluationService: IAuthorizationService,
) {
    enum class Decision {
        ALLOW,
        INVALID,
        FORBIDDEN,
    }

    fun evaluate(
        delegatedSession: IDelegatedSession,
        requestingClient: IClient,
        auth: PrincipalAuthorizationContext?,
    ): Decision {
        // A normal (non-handoff) code has no handoff to re-check.
        val handoffId = delegatedSession.handoffId ?: return Decision.ALLOW

        val handoff = handoffRepository.findById(handoffId) ?: return Decision.INVALID
        if (handoff.status != HandoffStatus.CLAIMED) return Decision.INVALID
        if (handoff.client.id != requestingClient.id) return Decision.INVALID

        // A code exchanged with client credentials may only be exchanged by the
        // client that created the handoff.
        if (auth is PrincipalAuthorizationContext.TokenAuth && auth.principalId != requestingClient.id)
            return Decision.INVALID

        val resource = IdResources.session(
            handoff.originalSession.account.id,
            handoff.originalSession.id,
        )

        // The permission is held by the client, on the original session.
        val allowed = evaluationService.evaluate(
            handoff.client.id,
            IdActions.ACCEPT_HANDOFF,
            resource,
        ).get()

        return if (allowed) Decision.ALLOW else Decision.FORBIDDEN
    }
}
