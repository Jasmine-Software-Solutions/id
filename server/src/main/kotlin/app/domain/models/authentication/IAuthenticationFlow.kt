package app.domain.models.authentication

import app.domain.models.ICreated
import app.domain.models.IExpires
import app.domain.models.IIdentified
import app.domain.models.account.IAccount
import app.domain.models.tenant.ITenant
import app.domain.services.authentication.AuthenticationFlowStep

interface IAuthenticationFlow : IIdentified, ICreated, IExpires {
    var tenant: ITenant?
    var account: IAccount?

    val steps: IAuthenticationFlowSteps
}

interface IAuthenticationFlowSteps {
    val values: List<AuthenticationFlowStep>

    fun add(step: AuthenticationFlowStep)
    fun replace(step: AuthenticationFlowStep)

    fun currentOrNull(): AuthenticationFlowStep?
            = values.lastOrNull()

    fun current(): AuthenticationFlowStep
            = values.last()
}