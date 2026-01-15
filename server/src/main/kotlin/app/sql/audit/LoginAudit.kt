package app.sql.audit

import io.javalin.http.Context
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insert

object LoginAuditTable : IntIdTable("login_audit") {
    val eventAt = long("event_at")
    val ipAddress = varchar("ip_address", 39)

    val action = text("action", eagerLoading = true)

    fun write(ctx: Context, action: String) {
        this.insert {
            it[this.eventAt] = System.currentTimeMillis()
            it[this.ipAddress] = ctx.ip()
            it[this.action] = action
        }
    }
}