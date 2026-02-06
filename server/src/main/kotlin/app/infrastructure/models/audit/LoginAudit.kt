package app.infrastructure.models.audit

import org.jetbrains.exposed.dao.id.IntIdTable

object LoginAuditTable : IntIdTable("login_audit") {
    val eventAt = long("event_at")
    val ipAddress = varchar("ip_address", 39)

    val action = text("action", eagerLoading = true)
}