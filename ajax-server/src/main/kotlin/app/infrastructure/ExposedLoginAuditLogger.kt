package app.infrastructure

import app.application.LoginAuditLogger
import app.infrastructure.models.audit.LoginAuditTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedLoginAuditLogger : LoginAuditLogger {
    override fun log(ipAddress: String, action: String) {
        transaction {
            LoginAuditTable.insert {
                it[eventAt] = System.currentTimeMillis()
                it[LoginAuditTable.ipAddress] = ipAddress
                it[LoginAuditTable.action] = action
            }
        }
    }
}