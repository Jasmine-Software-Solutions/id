package app.infrastructure.models.tenant

import app.infrastructure.models.account.AccountsTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.select
import java.util.*

object TenantAccountLinksTable : Table("tenant_account_links") {
    val tenant = reference("tenant", TenantsTable, onDelete = ReferenceOption.CASCADE)
    val account = reference("account", AccountsTable, onDelete = ReferenceOption.CASCADE)
    val administrator = bool("administrator").default(false)

    override val primaryKey = PrimaryKey(tenant, account)

    @Suppress("unused")
    fun selectByTenant(tenantId: UUID) = TenantAccountLinksTable.select { tenant eq tenantId }

    @Suppress("unused")
    fun selectByAccount(accountId: UUID) = TenantAccountLinksTable.select { account eq accountId }
}

