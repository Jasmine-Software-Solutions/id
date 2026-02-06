package app.infrastructure.models.audit

import app.infrastructure.models.account.SessionsTable
import app.infrastructure.models.oauth2.SessionAccessTokensTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object SessionAuditTable : IntIdTable("session_audit") {
    val eventAt = long("event_at")
    val sessionToken = optReference("session_token", SessionsTable.token, onDelete = ReferenceOption.CASCADE)
    val ipAddress = varchar("ip_address", 39)

    val action = text("action", eagerLoading = true)

    var sessionAccessToken = optReference("session_access_token", SessionAccessTokensTable, onDelete = ReferenceOption.SET_NULL)
}