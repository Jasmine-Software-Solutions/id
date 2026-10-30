package app.infrastructure.util

import app.infrastructure.repositories.tenants.ExposedTenantRepository
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import java.util.*

/**
 * Matches a nullable `tenant_id` column against [tenant] — the tenant's own rows,
 * or the platform-scoped rows (no tenant) when [tenant] is `null`.
 */
fun Column<EntityID<UUID>?>.matchesTenant(tenant: ITenant?): Op<Boolean> =
    tenant?.let { this eq EntityID(it.id, ExposedTenantRepository.Table) } ?: isNull()
