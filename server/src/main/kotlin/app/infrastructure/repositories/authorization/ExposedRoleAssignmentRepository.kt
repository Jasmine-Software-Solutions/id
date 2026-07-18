package app.infrastructure.repositories.authorization

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.repositories.clients.ExposedClientRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableEntityTransformer
import app.infrastructure.util.NullableInstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRole
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRoleAssignment
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IRoleAssignmentRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IRoleRepository
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedRoleAssignmentRepository(
    private val accountRepository: IAccountRepository<IAccount>,
    private val clientRepository: IClientRepository<IClient>,
    private val roleRepository: IRoleRepository<IRole>
) : ExposedIdentifiedEntityRepository<IRoleAssignment, ExposedRoleAssignmentRepository.RoleAssignment>(Table),
    IRoleAssignmentRepository<IRoleAssignment> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = RoleAssignment(row, insert, update)

    override fun transform(entity: IRoleAssignment): RoleAssignment? = entity as? RoleAssignment

    override fun writeDefaults(id: UUID, entity: IRoleAssignment, it: InsertStatement<Number>) {
        require((entity.account == null) != (entity.client == null)) {
            "Assignment must target exactly one account or client"
        }

        super.writeDefaults(id, entity, it)
    }

    override fun findByAccount(id: UUID): List<IRoleAssignment> = transaction {
        Table.select { Table.account eq EntityID(id, ExposedAccountRepository.Table) }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun findByClient(id: UUID): List<IRoleAssignment> = transaction {
        Table.select { Table.client eq EntityID(id, ExposedClientRepository.Table) }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    open inner class RoleAssignment(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IRoleAssignment {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant? by nullableColumn(Table.expiresAt, NullableInstantTransformer)

        override var account by nullableColumn(Table.account,
            NullableEntityTransformer(ExposedAccountRepository.Table, accountRepository))

        override var client by nullableColumn(Table.client,
            NullableEntityTransformer(ExposedClientRepository.Table, clientRepository))

        override var role by column(Table.role,
            EntityTransformer(ExposedRoleRepository.Table, roleRepository))
    }

    object Table : UUIDTable("role_assignments") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expires_at").nullable()

        val account = optReference("account_id", ExposedAccountRepository.Table, onDelete = ReferenceOption.CASCADE)
        val client = optReference("client_id", ExposedClientRepository.Table, onDelete = ReferenceOption.CASCADE)

        val role = reference("role_id", ExposedRoleRepository.Table.id, onDelete = ReferenceOption.CASCADE)

        init {
            index(false, account, client)
            index(false, role)
        }
    }
}
