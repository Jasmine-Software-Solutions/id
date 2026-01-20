package app.etc

import app.models.audit.ClientAuditTable
import app.models.audit.GrantAuditTable
import app.models.audit.LoginAuditTable
import app.models.audit.SessionAuditTable
import app.models.oauth2.MachineAccessTokens
import app.models.oauth2.SessionAccessTokens
import app.routes.oauth2.OAuth2TokenRoute.requireAuthorization
import io.javalin.http.Context
import org.jetbrains.exposed.sql.insert

fun ClientAuditTable.write(ctx: Context, action: String) {
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

fun GrantAuditTable.write(ctx: Context, action: String) {
    this.insert {
        it[this.eventAt] = System.currentTimeMillis()
        it[this.ipAddress] = ctx.ip()
        it[this.action] = action
    }
}

fun LoginAuditTable.write(ctx: Context, action: String) {
    this.insert {
        it[this.eventAt] = System.currentTimeMillis()
        it[this.ipAddress] = ctx.ip()
        it[this.action] = action
    }
}

fun SessionAuditTable.write(ctx: Context, action: String) {
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