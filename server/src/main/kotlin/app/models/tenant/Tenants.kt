package app.models.tenant

import app.etc.transformInstant
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object TenantsTable : UUIDTable("tenants") {
    val createdAt = long("created_at")
    val name = varchar("name", 64)

    val suspended = bool("suspended").default(false)
    val openbox = bool("openbox").default(false) // Allow support agents to access the tenant
}

@Suppress("unused")
class Tenant(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Tenant>(TenantsTable)

    var createdAt by TenantsTable.createdAt.transformInstant()
    var name by TenantsTable.name

    var suspended by TenantsTable.suspended
    var openbox by TenantsTable.openbox

    override fun equals(other: Any?): Boolean {
        if (other !is Tenant)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}