package app.sql.audit

import app.routes.oauth2.OAuth2AuthorizationRoute.requireAuthorization
import app.sql.account.SessionsTable
import app.sql.oauth2.SessionAccessTokens
import app.sql.oauth2.SessionAccessTokensTable
import io.javalin.http.Context
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.insert

object SessionAuditTable : IntIdTable("session_audit") {
    val eventAt = long("event_at")
    val sessionToken = optReference("session_token", SessionsTable.token, onDelete = ReferenceOption.CASCADE)
    val ipAddress = varchar("ip_address", 39)

    val action = text("action", eagerLoading = true)

    var sessionAccessToken = optReference("session_access_token", SessionAccessTokensTable, onDelete = ReferenceOption.SET_NULL)

    fun write(ctx: Context, action: String) {
        this.insert {
            it[this.eventAt] = System.currentTimeMillis()
            it[this.sessionToken] = ctx.cookie("session")
            it[this.ipAddress] = ctx.ip()
            it[this.action] = action

            val tokens = try { ctx.requireAuthorization() } catch (e: Exception) { null }
            if (tokens is SessionAccessTokens)
                it[this.sessionAccessToken] = tokens.id
        }
    }
}