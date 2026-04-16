package app.domain.services.authentication

import app.domain.models.account.ISession

sealed class AuthenticationFlowResult

object AccountMissingAuthenticationFlowResult : AuthenticationFlowResult()
object LevelMissingAuthenticationFlowResult : AuthenticationFlowResult()

class AuthenticatedAuthenticationFlowResult(
    val session: ISession
) : AuthenticationFlowResult()
