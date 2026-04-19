package com.jasminesoftwaresolutions.id.domain.services.authentication

import com.jasminesoftwaresolutions.id.domain.models.account.ISession

sealed class AuthenticationFlowResult

object AccountMissingAuthenticationFlowResult : AuthenticationFlowResult()
object LevelMissingAuthenticationFlowResult : AuthenticationFlowResult()

class AuthenticatedAuthenticationFlowResult(
    val session: ISession
) : AuthenticationFlowResult()
