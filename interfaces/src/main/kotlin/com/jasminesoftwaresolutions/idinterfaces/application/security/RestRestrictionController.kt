package com.jasminesoftwaresolutions.idinterfaces.application.security

import com.jasminesoftwaresolutions.id.domain.models.restriction.IRestriction
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionTargetType
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionType
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.security.IRestrictionService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import io.javalin.community.routing.annotations.Delete
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.http.*
import java.util.*

class RestRestrictionController(
    private val restrictionService: IRestrictionService,
    private val evaluationService: IAuthorizationService,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val accountRepository: IAccountRepository,
) {
    data class PlaceRestrictionPayload(
        val type: String?,
        val target_type: String?,
        val target_id: String?,
        val event_id: UUID?,
        val duration_minutes: Int?,
    )

    data class RestrictionResponse(
        val id: UUID,
        val type: String,
        val target_type: String,
        val target_id: String,
        val account_email: String?,
        val event_id: UUID?,
        val duration_minutes: Int?,
        val created_at: Long,
        val expires_at: Long?,
    )

    private fun IRestriction.toResponse() = RestrictionResponse(
        id = id,
        type = type.name.lowercase(),
        target_type = targetType.name.lowercase(),
        target_id = targetId,
        account_email = if (targetType == RestrictionTargetType.ACCOUNT) {
            runCatching { UUID.fromString(targetId) }.getOrNull()?.let { accountRepository.findById(it)?.email }
        } else null,
        event_id = eventId,
        duration_minutes = durationMinutes,
        created_at = createdAt.epochSecond,
        expires_at = expiresAt?.epochSecond,
    )

    private fun Context.authorizeRestriction() {
        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
        val allowed = evaluationService.evaluate(auth.principalId, IdActions.PLACE_RESTRICTION, IdResources.platform()).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges")
    }

    @Post("/api/v1/restrictions")
    fun place(ctx: Context) {
        ctx.authorizeRestriction()

        val payload = runCatching { ctx.bodyAsClass(PlaceRestrictionPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val type = when (payload.type?.lowercase()) {
            "account_lock" -> RestrictionType.ACCOUNT_LOCK
            "ip_block" -> RestrictionType.IP_BLOCK
            else -> throw BadRequestResponse("Invalid type: must be account_lock or ip_block")
        }

        val targetType = when (payload.target_type?.lowercase()) {
            "account" -> RestrictionTargetType.ACCOUNT
            "ip" -> RestrictionTargetType.IP
            else -> throw BadRequestResponse("Invalid target_type: must be account or ip")
        }

        val targetId = payload.target_id?.takeUnless { it.isBlank() }
            ?: throw BadRequestResponse("Missing target_id")

        val restriction = restrictionService.place(type, targetType, targetId, payload.event_id, payload.duration_minutes)

        ctx.status(201)
        ctx.json(restriction.toResponse())
    }

    @Get("/api/v1/restrictions")
    fun list(ctx: Context) {
        ctx.authorizeRestriction()

        val targetType = ctx.queryParam("target_type")?.let {
            when (it.lowercase()) {
                "account" -> RestrictionTargetType.ACCOUNT
                "ip" -> RestrictionTargetType.IP
                else -> throw BadRequestResponse("Invalid target_type")
            }
        }
        val targetId = ctx.queryParam("target_id")

        val restrictions = if (targetType != null && targetId != null) {
            restrictionService.findActive(targetType, targetId)
        } else {
            restrictionService.findAllActive()
        }

        ctx.json(restrictions.map { it.toResponse() })
    }

    @Delete("/api/v1/restrictions/{id}")
    fun lift(ctx: Context, @Param id: UUID) {
        ctx.authorizeRestriction()

        restrictionService.lift(id)
        ctx.status(200)
    }
}
