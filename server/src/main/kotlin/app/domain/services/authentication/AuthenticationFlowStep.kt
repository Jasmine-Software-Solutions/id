package app.domain.services.authentication

import app.domain.models.authentication.IAuthenticationFlow
import kotlin.reflect.KClass

open class AuthenticationFlowStep(
    val fqdn: String,
    val alternatives: Set<AuthenticationFlowStep> = emptySet(),
    val level: Int = 0
)

abstract class AuthenticationFlowStepHandler<TRequest : Any, TResponse : Any>(
    val requestClass: KClass<TRequest>,
    val responseClass: KClass<TResponse>
) {
    abstract fun create(flow: IAuthenticationFlow): TRequest
    abstract fun accept(flow: IAuthenticationFlow, request: TRequest, response: TResponse): AuthenticationFlowStepResult
}