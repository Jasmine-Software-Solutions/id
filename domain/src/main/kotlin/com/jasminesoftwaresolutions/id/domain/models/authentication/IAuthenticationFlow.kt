package com.jasminesoftwaresolutions.id.domain.models.authentication

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStep

interface IAuthenticationFlow : IIdentified,
    ICreated, IExpires {
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