package com.jasminesoftwaresolutions.idinterfaces.application.auth

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jasminesoftwaresolutions.id.domain.events.*
import com.jasminesoftwaresolutions.id.domain.models.SignedValue
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.repositories.IAuthenticationFlowRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IMagicLinkRepository
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepRegistry
import com.jasminesoftwaresolutions.id.domain.services.ISigningFunction
import com.jasminesoftwaresolutions.id.domain.services.accounts.IJWTService
import com.jasminesoftwaresolutions.id.domain.services.accounts.IMagicLinkService
import com.jasminesoftwaresolutions.id.domain.services.authentication.*
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.community.routing.annotations.Get
import io.javalin.http.*
import java.security.MessageDigest
import java.time.Instant
import java.util.*

class RestAuthController(
    private val authenticationService: IAuthenticationService,
    private val flowRepository: IAuthenticationFlowRepository,
    private val stepRegistry: IAuthenticationStepRegistry,
    private val magicLinkRepository: IMagicLinkRepository,
    private val magicLinkService: IMagicLinkService,
    private val signingFunction: ISigningFunction,
    private val jwtService: IJWTService,
    private val eventDispatcher: IEventDispatcher,
) {
    private val gson = Gson()

    data class StartResponse(val flowId: UUID, val step: String, val request: String)
    data class NextRequest(val request: String, val response: Map<String, String>)
    data class NextResponse(
        val done: Boolean,
        val step: String? = null,
        val request: String? = null,
        val error: Boolean = false
    )

    data class InsteadRequest(val step: String)
    data class InsteadResponse(val step: String, val request: String)

    @Get("/.well-known/jwks.json")
    fun jwks(ctx: Context) {
        ctx.json(jwtService.jwks())
    }

    @Post("/api/v1/auth/flows")
    fun start(ctx: Context) {
        val flow = authenticationService.start(
            userAgent = ctx.userAgent(),
            ipAddress = ctx.ip()
        )

        val step = flow.steps.current()
        val handler = authenticationService.run { flow.handler() }
        val signed = SignedValue(signingFunction, handler.create(flow))

        ctx.json(StartResponse(flow.id, step.fqdn, signed.toString()))
    }

    @Post("/api/v1/auth/flows/{flowId}/next")
    fun next(@Param("flowId") flowId: UUID, ctx: Context) {
        val body = ctx.bodyAsClass(NextRequest::class.java)
        val flow = flowRepository.findById(flowId) ?: run {
            ctx.json(NextResponse(done = true, error = true)); return
        }

        val currentStep = flow.steps.current()
        val handler = authenticationService.run { flow.handler() }

        val signedValue = try {
            SignedValue.fromString(signingFunction, body.request, handler.requestClass.java)
        } catch (e: Exception) {
            ctx.json(NextResponse(done = true, error = true)); return
        }

        val responseObj = JsonObject().apply {
            body.response.forEach { (k, v) -> addProperty(k, v) }
        }
        val response = gson.fromJson(responseObj, handler.responseClass.java)
        val result = authenticationService.run { flow.next(signedValue.value, response) }

        // On retry, tell the frontend to poll again with the same request — do not regenerate
        if (result.first is RetryAuthenticationFlowStepResult) {
            ctx.json(NextResponse(done = false, step = result.second?.fqdn, request = body.request))
            return
        }

        val isLastStep = result.first is BypassAuthenticationFlowStepResult ||
                (result.first is ContinueAuthenticationFlowStepResult && result.second == null)

        if (isLastStep) {
            val finish = authenticationService.run { flow.finish() }
            if (finish is AuthenticatedAuthenticationFlowResult) {
                ctx.cookie(Cookie("session_id", finish.session.id.toString()).apply {
                    isHttpOnly = true; secure = true; sameSite = SameSite.STRICT
                })
                ctx.cookie(Cookie("session_token", finish.session.token).apply {
                    isHttpOnly = true; secure = true; sameSite = SameSite.STRICT
                })
            }

            ctx.json(NextResponse(done = true, error = finish !is AuthenticatedAuthenticationFlowResult))
            return
        }

        val nextStep = result.second ?: run {
            ctx.json(NextResponse(done = true, error = true)); return
        }

        val nextHandler = authenticationService.run { flow.handler() }
        val nextSigned = SignedValue(signingFunction, nextHandler.create(flow))

        ctx.json(NextResponse(done = false, step = nextStep.fqdn, request = nextSigned.toString()))
    }

    @Post("/api/v1/auth/flows/{flowId}/instead")
    fun instead(@Param("flowId") flowId: UUID, ctx: Context) {
        val body = ctx.bodyAsClass(InsteadRequest::class.java)
        val flow = flowRepository.findById(flowId) ?: throw BadRequestResponse("Flow not found")
        val step = stepRegistry.findByFqdn(body.step)?.step ?: throw BadRequestResponse("Step not found")

        authenticationService.run { if (!flow.instead(step)) throw BadRequestResponse("Invalid step") }

        val refreshed = flowRepository.findById(flowId) ?: throw BadRequestResponse("Flow not found")
        val handler = authenticationService.run { refreshed.handler() }
        val signed = SignedValue(signingFunction, handler.create(refreshed))

        ctx.json(InsteadResponse(refreshed.steps.current().fqdn, signed.toString()))
    }

    @Post("/api/v1/auth/magic-links/{id}")
    fun resolveMagicLink(@Param("id") id: UUID, ctx: Context) {
        val token = ctx.queryParam("token") ?: throw BadRequestResponse("Missing token")
        val link = magicLinkRepository.findById(id) ?: throw NotFoundResponse()

        if (!link.verifyDecisionToken(token)) throw BadRequestResponse("Invalid token")
        if (link.expiresAt.isBefore(Instant.now())) throw BadRequestResponse("Link expired")
        if (link.decidedAt != null) throw BadRequestResponse("Already decided")

        // Also verifies the account's email: the link was delivered to the address, so
        // approving it proves control of that inbox.
        magicLinkService.approve(link)

        eventDispatcher.dispatch(
            MagicLinkVerifiedEvent(
                principalId = UUID(0, 0),
                action = "id.jasmine.software:MagicLinkVerified",
                resource = IdResources.platform().toString(),
                timestamp = Instant.now(),
                magicLinkId = id,
                flowId = UUID(0, 0),
                accountId = link.account.id,
            )
        )

        ctx.status(200)
    }
}
