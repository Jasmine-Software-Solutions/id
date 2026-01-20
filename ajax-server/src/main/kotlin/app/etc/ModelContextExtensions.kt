package app.etc

import app.models.audit.LoginAuditTable
import app.models.audit.SessionAuditTable
import io.javalin.http.Context
import org.jetbrains.exposed.sql.insert

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
    }
}