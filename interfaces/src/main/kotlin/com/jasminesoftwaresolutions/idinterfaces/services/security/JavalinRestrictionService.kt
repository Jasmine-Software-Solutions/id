package com.jasminesoftwaresolutions.idinterfaces.services.security

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.IAccessToken
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRefreshToken
import com.jasminesoftwaresolutions.id.domain.services.security.IRestrictionService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import io.javalin.http.Context

class JavalinRestrictionService(
    private val restrictionService: IRestrictionService,
    private val authorizationService: JavalinRequestAuthenticationService,
) : IJavalinRestrictionService {

    override fun restrictRequest(ctx: Context): Boolean {
        val auth = authorizationService.authenticate(ctx)
        val ip = ctx.ip()

        val accountRestricted = when (auth) {
            is PrincipalAuthorizationContext.SessionAuth ->
                restrictionService.isAccountRestricted(auth.session.account)

            is PrincipalAuthorizationContext.TokenAuth -> {
                val token = auth.token
                val subjectAccount = token.subject as? IAccount
                when {
                    token is IAccessToken -> restrictionService.isAccountRestricted(token.session.account)
                    token is IRefreshToken -> restrictionService.isAccountRestricted(token.session.account)
                    subjectAccount != null -> restrictionService.isAccountRestricted(subjectAccount)
                    else -> false
                }
            }

            else -> false
        }

        return accountRestricted || restrictionService.isAgentRestricted(ip)
    }
}
