package app.sql.oauth2

import java.util.*

sealed interface OAuth2Authorized {
    fun isAccessTokenActive(): Boolean
    fun authorizedFor(scope: String, tenant: UUID? = null): Boolean
}