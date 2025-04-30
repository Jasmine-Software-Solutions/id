package app.sql.audit

import app.routes.oauth2.OAuth2AuthorizationRoute.requireAuthorization
import app.sql.client.ClientsTable
import app.sql.oauth2.MachineAccessTokens
import app.sql.oauth2.MachineAccessTokensTable
import io.javalin.http.Context
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.insert

object ClientAuditTable : IntIdTable("client_audit") {
    val eventAt = long("event_at")
    val action = text("action", eagerLoading = true)

    var client = reference("client", ClientsTable, onDelete = ReferenceOption.CASCADE)
    var accessToken = reference("access_token", MachineAccessTokensTable, onDelete = ReferenceOption.CASCADE)

    fun write(ctx: Context, action: String) {
        val tokens = ctx.requireAuthorization()
        if (tokens !is MachineAccessTokens)
            throw IllegalStateException("ClientAuditTable can only be written by MachineAccessTokens")

        this.insert {
            it[this.eventAt] = System.currentTimeMillis()
            it[this.action] = action

            it[this.client] = tokens.client.id
            it[this.accessToken] = tokens.id
        }
    }
}