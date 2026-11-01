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
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPolicy
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPolicyAssignment
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRole
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IPolicyAssignmentRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IPolicyRepository
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

class ExposedPolicyAssignmentRepository(
    private val accountRepository: IAccountRepository,
    private val clientRepository: IClientRepository,
    private val roleRepository: IRoleRepository,
    private val policyRepository: IPolicyRepository
) : ExposedIdentifiedEntityRepository<IPolicyAssignment, ExposedPolicyAssignmentRepository.PolicyAssignmentEntity>(Table),
    IPolicyAssignmentRepository {

    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = PolicyAssignmentEntity(row, insert, update)

    override fun transform(entity: IPolicyAssignment): PolicyAssignmentEntity? = entity as? PolicyAssignmentEntity

    override fun writeDefaults(id: UUID, entity: IPolicyAssignment, it: InsertStatement<Number>) {
        super.writeDefaults(id, entity, it)
    }

    override fun findByAccount(id: UUID): List<IPolicyAssignment> = transaction {
        Table.select { Table.account eq EntityID(id, ExposedAccountRepository.Table) }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun findByClient(id: UUID): List<IPolicyAssignment> = transaction {
        Table.select { Table.client eq EntityID(id, ExposedClientRepository.Table) }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun findByRole(id: UUID): List<IPolicyAssignment> = transaction {
        Table.select { Table.role eq EntityID(id, ExposedRoleRepository.Table) }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun findByPolicy(id: UUID): List<IPolicyAssignment> = transaction {
        Table.select { Table.policy eq EntityID(id, ExposedPolicyRepository.Table) }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    open inner class PolicyAssignmentEntity(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IPolicyAssignment {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant? by nullableColumn(Table.expiresAt, NullableInstantTransformer)

        override var account by nullableColumn(Table.account,
            NullableEntityTransformer(ExposedAccountRepository.Table, accountRepository))

        override var client by nullableColumn(Table.client,
            NullableEntityTransformer(ExposedClientRepository.Table, clientRepository))

        override var role by nullableColumn(Table.role,
            NullableEntityTransformer(ExposedRoleRepository.Table, roleRepository))

        override var policy by column(Table.policy,
            EntityTransformer(ExposedPolicyRepository.Table, policyRepository))
    }

    object Table : UUIDTable("policy_assignments") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expires_at").nullable()

        val account = optReference("account_id", ExposedAccountRepository.Table, onDelete = ReferenceOption.CASCADE)
        val client = optReference("client_id", ExposedClientRepository.Table, onDelete = ReferenceOption.CASCADE)
        val role = optReference("role_id", ExposedRoleRepository.Table, onDelete = ReferenceOption.CASCADE)

        val policy = reference("policy_id", ExposedPolicyRepository.Table.id, onDelete = ReferenceOption.CASCADE)

        init {
            index(false, account, client, role)
            index(false, policy)
        }
    }
}
