package app.infrastructure.models.account

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object ForgotPasswordCodesTable : IntIdTable("forgot_password_codes") {
    val account = reference("account_id", AccountsTable, onDelete = ReferenceOption.CASCADE)

    val issuedAt = long("issued_at")
    val expiresAt = long("expires_at")

    val code = varchar("code", 6).uniqueIndex()
}