package com.jasminesoftwaresolutions.idinterfaces.application.handoff

import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.EmailScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.OpenIdScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.ProfileScope
import com.jasminesoftwaresolutions.id.domain.models.handoff.HandoffStatus
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoff
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IHandoffRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IScopeRepository
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffConflictException
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffExpiredException
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffNotFoundException
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffQr
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffScanStatus
import com.jasminesoftwaresolutions.id.domain.services.handoff.IHandoffService
import com.jasminesoftwaresolutions.idinterfaces.application.authorization.OAuth2Code
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.http.*
import java.net.URI
import java.time.Instant
import java.util.*

/**
 * The cross-device handoff surface.
 *
 * Every route that requires a browser session checks `AcceptHandoff` against
 * the client on the session resource; the exchange guard re-checks it when the
 * code is redeemed. `GET /handoffs/{id}/scan` and `POST /handoffs/scans` are
 * necessarily unauthenticated — the scanning device has no ID session on that
 * device — and the raw scan token is their only credential.
 */
class RestHandoffController(
    private val handoffRepository: IHandoffRepository,
    private val handoffService: IHandoffService,
    private val clientRepository: IClientRepository,
    private val scopeRegistry: IScopeRegistry<IScope>,
    private val scopeRepository: IScopeRepository,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val issuerBaseUrl: String,
) {
    // ----- Payloads and responses -----

    data class CreateHandoffPayload(
        val client_id: UUID?,
        val redirect_uri: String?,
        val state: String?,
        val scope: String?,
        val nonce: String?,
    )

    data class HandoffResponse(
        val handoffId: UUID,
        val status: String,
        val expiresAt: Instant,
        val qrUrl: String?,
        val qrExpiresAt: Instant?,
    )

    data class HandoffStatusResponse(
        val status: String,
        val expiresAt: Instant,
    )

    data class HandoffQrResponse(
        val status: String,
        val expiresAt: Instant,
        val qrUrl: String,
        val qrExpiresAt: Instant,
    )

    data class HandoffConfirmResponse(
        val status: String,
    )

    data class ScanPayload(
        val handoff_id: UUID?,
        val token: String?,
    )

    data class ScanResponse(
        val handoffId: UUID,
        val scanToken: String,
        val expiresAt: Instant,
    )

    data class ScanStatusResponse(
        val status: String,
    )

    data class ClaimPayload(
        val scan_token: String?,
    )

    data class ClaimResponse(
        val redirect_url: String,
    )

    // ----- Create-or-attach -----

    @Post("/api/v1/handoffs")
    fun createOrAttach(ctx: Context) {
        val session = ctx.requireSession()
        val payload = ctx.bodyAsClass(CreateHandoffPayload::class.java)

        val clientId = payload.client_id ?: throw BadRequestResponse("Missing client_id")
        val redirectUriRaw = payload.redirect_uri ?: throw BadRequestResponse("Missing redirect_uri")

        requireLength(payload.state, MAX_STATE_LENGTH, "state")
        requireLength(payload.scope, MAX_SCOPE_LENGTH, "scope")
        requireLength(payload.nonce, MAX_NONCE_LENGTH, "nonce")

        val redirectUri = runCatching { URI.create(redirectUriRaw) }.getOrNull()
            ?: throw BadRequestResponse("Invalid redirect_uri")

        val client = clientRepository.findById(clientId)
            ?: throw BadRequestResponse("Invalid client_id")

        if (client.redirectUris.values.none { it.toString() == redirectUriRaw })
            throw BadRequestResponse("Invalid redirect_uri")

        if (!ctx.isAllowed(client.id, session))
            throw ForbiddenResponse("Handoff isn't enabled for this app")

        val scopes = parseScopes(payload.scope)

        val result = try {
            handoffService.createOrAttach(session, client, redirectUri, payload.state, scopes, payload.nonce)
        } catch (e: Throwable) {
            ctx.raise(e)
        }

        // The first instance's parameters stay authoritative, including its
        // client. A later instance attaching to another client's handoff is
        // validated against that client too, so it cannot be used to observe a
        // handoff its own app has no permission for.
        val handoff = result.handoff
        if (handoff.client.id != client.id && !ctx.isAllowed(handoff.client.id, session))
            throw ForbiddenResponse("Handoff isn't enabled for this app")

        val qr = result.qr
        val response = HandoffResponse(
            handoffId = handoff.id,
            status = handoff.wireStatus(),
            expiresAt = handoff.expiresAt,
            qrUrl = qr?.let { buildQrUrl(handoff.id, it.token) },
            qrExpiresAt = qr?.expiresAt,
        )

        ctx.status(if (result.created) 201 else 200)
        ctx.json(response)
    }

    // ----- Shared status -----

    @Get("/api/v1/handoffs/{handoffId}")
    fun status(ctx: Context, @Param("handoffId") handoffId: UUID) {
        val session = ctx.requireSession()
        val handoff = handoffRepository.findOwned(handoffId, session.id)
            ?: throw NotFoundResponse()

        val status = if (handoff.expiresAt <= Instant.now()) EXPIRED else handoff.wireStatus()
        ctx.json(HandoffStatusResponse(status, handoff.expiresAt))
    }

    // ----- Per-instance QR rotation -----

    @Post("/api/v1/handoffs/{handoffId}/qr")
    fun issueQr(ctx: Context, @Param("handoffId") handoffId: UUID) {
        val session = ctx.requireSession()
        val handoff = handoffRepository.findOwned(handoffId, session.id)
            ?: throw NotFoundResponse()

        val qr = try {
            handoffService.issueQr(handoff)
        } catch (e: Throwable) {
            ctx.raise(e)
        }

        ctx.json(qrResponse(handoff, qr))
    }

    // ----- User decisions -----

    @Post("/api/v1/handoffs/{handoffId}/reject")
    fun reject(ctx: Context, @Param("handoffId") handoffId: UUID) {
        val session = ctx.requireSession()
        val handoff = handoffRepository.findOwned(handoffId, session.id)
            ?: throw NotFoundResponse()

        val qr = try {
            handoffService.reject(handoff)
        } catch (e: Throwable) {
            ctx.raise(e)
        }

        ctx.json(qrResponse(handoff, qr))
    }

    @Post("/api/v1/handoffs/{handoffId}/confirm")
    fun confirm(ctx: Context, @Param("handoffId") handoffId: UUID) {
        val session = ctx.requireSession()
        val handoff = handoffRepository.findOwned(handoffId, session.id)
            ?: throw NotFoundResponse()

        if (!ctx.isAllowed(handoff.client.id, handoff.originalSession))
            throw ForbiddenResponse("Handoff isn't enabled for this app")

        val updated = try {
            handoffService.confirm(handoff)
        } catch (e: Throwable) {
            ctx.raise(e)
        }

        ctx.json(HandoffConfirmResponse(updated.wireStatus()))
    }

    @Post("/api/v1/handoffs/{handoffId}/change-devices")
    fun changeDevices(ctx: Context, @Param("handoffId") handoffId: UUID) {
        val session = ctx.requireSession()
        val handoff = handoffRepository.findOwned(handoffId, session.id)
            ?: throw NotFoundResponse()

        val qr = try {
            handoffService.changeDevices(handoff)
        } catch (e: Throwable) {
            ctx.raise(e)
        }

        ctx.json(qrResponse(handoff, qr))
    }

    // ----- Scanning device (no ID session on that device) -----

    @Post("/api/v1/handoffs/scans")
    fun scan(ctx: Context) {
        val payload = ctx.bodyAsClass(ScanPayload::class.java)
        val handoffId = payload.handoff_id ?: throw BadRequestResponse("Missing handoff_id")
        val token = payload.token ?: throw BadRequestResponse("Missing token")

        val scan = try {
            handoffService.scan(handoffId, token, ctx.userAgent(), ctx.ip())
        } catch (e: Throwable) {
            ctx.raise(e)
        }

        ctx.status(201)
        ctx.json(ScanResponse(scan.handoff.id, scan.token, scan.handoff.expiresAt))
    }

    @Get("/api/v1/handoffs/{handoffId}/scan")
    fun scanStatus(ctx: Context, @Param("handoffId") handoffId: UUID) {
        val handoff = handoffRepository.findById(handoffId) ?: throw NotFoundResponse()
        val scanToken = ctx.header(SCAN_TOKEN_HEADER) ?: throw NotFoundResponse()

        val status = handoffService.scanStatus(handoff, scanToken) ?: throw NotFoundResponse()
        ctx.json(ScanStatusResponse(status.wire()))
    }

    @Post("/api/v1/handoffs/{handoffId}/claim")
    fun claim(ctx: Context, @Param("handoffId") handoffId: UUID) {
        val payload = ctx.bodyAsClass(ClaimPayload::class.java)
        val scanToken = payload.scan_token ?: throw BadRequestResponse("Missing scan_token")

        val handoff = handoffRepository.findById(handoffId) ?: throw NotFoundResponse()

        when (handoffService.scanStatus(handoff, scanToken)) {
            null -> throw NotFoundResponse()
            HandoffScanStatus.EXPIRED -> throw GoneResponse("Handoff expired")
            HandoffScanStatus.CLAIMED -> throw ConflictResponse("Handoff was already claimed")
            HandoffScanStatus.SUPERSEDED -> throw ConflictResponse("Handoff was replaced")
            HandoffScanStatus.REJECTED -> throw ConflictResponse("The scan was declined")
            HandoffScanStatus.PENDING -> throw ConflictResponse("Handoff has not been confirmed")
            HandoffScanStatus.CONFIRMED -> Unit
        }

        if (!ctx.isAllowed(handoff.client.id, handoff.originalSession))
            throw ForbiddenResponse("Handoff isn't enabled for this app")

        val claim = try {
            handoffService.claim(handoff, ctx.userAgent(), ctx.ip())
        } catch (e: Throwable) {
            ctx.raise(e)
        }

        ctx.cookie(Cookie(SESSION_ID_COOKIE, claim.session.id.toString()).apply {
            isHttpOnly = true
            secure = true
            sameSite = SameSite.STRICT
        })
        ctx.cookie(Cookie(SESSION_TOKEN_COOKIE, claim.session.token).apply {
            isHttpOnly = true
            secure = true
            sameSite = SameSite.STRICT
        })

        val redirectParams = mutableMapOf(
            "code" to OAuth2Code.encode(claim.delegatedSession.id, claim.code),
            "handoff" to "true",
        )
        handoff.state?.let { redirectParams["state"] = it }

        ctx.json(
            ClaimResponse(
                OAuth2Code.buildRedirectUrl(handoff.redirectUri.toString(), redirectParams)
            )
        )
    }

    // ----- Helpers -----

    private fun Context.requireSession(): IHashedSession {
        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
        val sessionAuth = auth as? PrincipalAuthorizationContext.SessionAuth
            ?: throw ForbiddenResponse("Handoff requires a user session")
        return sessionAuth.session as? IHashedSession
            ?: throw ForbiddenResponse("Handoff requires a user session")
    }

    /**
     * Does [clientId] hold `AcceptHandoff` on [session]? The permission is held
     * by the client and evaluated against the original session resource.
     */
    private fun Context.isAllowed(clientId: UUID, session: ISession): Boolean =
        evaluationService.evaluate(
            clientId,
            IdActions.ACCEPT_HANDOFF,
            IdResources.session(session.account.id, session.id),
        ).get()

    private fun parseScopes(raw: String?): Set<IScope> {
        if (raw.isNullOrBlank()) return setOf(OpenIdScope, ProfileScope, EmailScope)

        return raw.split(" ")
            .filter { it.isNotBlank() }
            .mapNotNull { scopeRegistry.findById(it) ?: scopeRepository.findById(it) }
            .toSet()
    }

    private fun requireLength(value: String?, max: Int, name: String) {
        if (value != null && value.length > max)
            throw BadRequestResponse("$name is too long")
    }

    private fun HandoffStatus.wire(): String = name.lowercase()

    private fun IHandoff.wireStatus(): String = status.wire()

    private fun HandoffScanStatus.wire(): String = when (this) {
        HandoffScanStatus.PENDING -> "pending"
        HandoffScanStatus.CONFIRMED -> "confirmed"
        HandoffScanStatus.REJECTED -> "rejected"
        HandoffScanStatus.CLAIMED -> "claimed"
        HandoffScanStatus.SUPERSEDED -> "superseded"
        HandoffScanStatus.EXPIRED -> "expired"
    }

    private fun buildQrUrl(handoffId: UUID, rawToken: String): String =
        "${issuerBaseUrl.trimEnd('/')}/#/handoff/scan?h=$handoffId&t=$rawToken"

    private fun qrResponse(handoff: IHandoff, qr: HandoffQr) = HandoffQrResponse(
        status = HandoffStatus.AWAITING_SCAN.wire(),
        expiresAt = handoff.expiresAt,
        qrUrl = buildQrUrl(handoff.id, qr.token),
        qrExpiresAt = qr.expiresAt,
    )

    /**
     * Domain failures to their HTTP statuses. Everything else is rethrown so a
     * genuine bug stays a 500 rather than being disguised as a client error.
     */
    private fun Context.raise(error: Throwable): Nothing {
        val message = error.message ?: "Handoff request failed"
        when (error) {
            is HandoffNotFoundException -> throw NotFoundResponse(message)
            is HandoffExpiredException -> throw GoneResponse(message)
            is HandoffConflictException -> throw ConflictResponse(message)
            else -> throw error
        }
    }

    private companion object {
        const val SCAN_TOKEN_HEADER = "X-Handoff-Scan-Token"
        const val SESSION_ID_COOKIE = "session_id"
        const val SESSION_TOKEN_COOKIE = "session_token"

        const val MAX_STATE_LENGTH = 2048
        const val MAX_SCOPE_LENGTH = 256
        const val MAX_NONCE_LENGTH = 256

        const val EXPIRED = "expired"
    }
}
