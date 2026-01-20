package app.models.audit

import app.models.client.ClientsTable
import app.models.oauth2.MachineAccessTokensTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object ClientAuditTable : IntIdTable("client_audit") {
    val eventAt = long("event_at")
    val action = text("action", eagerLoading = true)

    var client = reference("client", ClientsTable, onDelete = ReferenceOption.CASCADE)
    var accessToken = reference("access_token", MachineAccessTokensTable, onDelete = ReferenceOption.CASCADE)
}