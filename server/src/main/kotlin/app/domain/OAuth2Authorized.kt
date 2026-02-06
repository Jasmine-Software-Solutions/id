package app.domain

import java.util.*

interface OAuth2Authorized {
    fun isAccessTokenActive(): Boolean
    fun authorizedFor(scope: String, tenant: UUID? = null): Boolean
}