package app.infrastructure.repositories.authentication

import app.domain.models.account.IAccount
import app.domain.models.authentication.IAuthenticationFlow
import app.domain.models.authentication.IAuthenticationFlowSteps
import app.domain.models.tenant.ITenant
import app.domain.registries.AuthenticationStepRegistry
import app.domain.repositories.IAccountRepository
import app.domain.repositories.IAuthenticationFlowRepository
import app.domain.repositories.ITenantRepository
import app.domain.services.authentication.AuthenticationFlowStep
import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableEntityTransformer
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedAuthenticationFlowRepository(
    val stepRegistry: AuthenticationStepRegistry<IAuthenticationFlow>,
    val tenantRepository: ITenantRepository<ITenant>,
    val accountRepository: IAccountRepository<IAccount>
) : ExposedIdentifiedEntityRepository<IAuthenticationFlow, ExposedAuthenticationFlowRepository.AuthenticationFlow>(Table, ExposedAuthenticationFlowRepository.AuthenticationFlow::class), IAuthenticationFlowRepository<IAuthenticationFlow> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = AuthenticationFlow(row, insert, update)

    override fun clone(src: AuthenticationFlow, dest: AuthenticationFlow) {
        (dest.steps.values as MutableList).clear()
        (dest.steps.values as MutableList).addAll(src.steps.values)
    }

    override fun createDependents(id: UUID, entity: IAuthenticationFlow) {
        for (step in entity.steps.values)
            addStep(id, step)
    }

    private fun findSteps(id: UUID): List<AuthenticationFlowStep> = transaction {
        val steps = StepsTable.select { StepsTable.flow eq id }
            .orderBy(StepsTable.createdAt, SortOrder.ASC)
            .map { it[StepsTable.stepFqdn] }
            .mapNotNull { stepRegistry.findByFqdn(it) }
            .map { it.step }
            .toList()

        return@transaction steps
    }

    private fun addStep(id: UUID, step: AuthenticationFlowStep) {
        StepsTable.insert {
            it[StepsTable.flow] = id
            it[StepsTable.stepFqdn] = step.fqdn
        }
    }

    private fun replaceStep(id: UUID, step: AuthenticationFlowStep) {
        val lastStep = StepsTable.select { StepsTable.flow eq id }
            .orderBy(StepsTable.createdAt, SortOrder.DESC)
            .limit(1)
            .first()

        StepsTable.update({ StepsTable.id eq lastStep[StepsTable.id] }) {
            it[StepsTable.createdAt] = System.currentTimeMillis()
            it[StepsTable.stepFqdn] = step.fqdn
        }
    }

    inner class AuthenticationFlow(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IAuthenticationFlow {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)

        override var tenant: ITenant? by nullableColumn(Table.tenant,
            NullableEntityTransformer(ExposedTenantRepository.Table, tenantRepository))

        override var account: IAccount? by nullableColumn(Table.account,
            NullableEntityTransformer(ExposedAccountRepository.Table, accountRepository))

        override val steps: IAuthenticationFlowSteps = object : IAuthenticationFlowSteps {
            override val values: List<AuthenticationFlowStep> =
                if (insert == null) findSteps(id).toMutableList()
                else mutableListOf()

            override fun add(step: AuthenticationFlowStep) {
                if (insert == null) try {
                    addStep(id, step)
                } catch (e: IllegalStateException) {
                    throw IllegalStateException("Invoked IAuthenticationFlowSteps#add(AuthenticationFlowStep) outside of IRepository#update(IAuthenticationFlow, ...) callback")
                }

                (values as MutableList).add(step)
            }

            override fun replace(step: AuthenticationFlowStep) {
                if (insert == null) try {
                    replaceStep(id, step)
                } catch (e: IllegalStateException) {
                    throw IllegalStateException("Invoked IAuthenticationFlowSteps#replace(AuthenticationFlowStep) outside of IRepository#update(IAuthenticationFlow, ...) callback")
                }

                (values as MutableList).removeLast()
                (values as MutableList).add(step)
            }
        }
    }

    override fun install() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Table,
                StepsTable
            )
        }
    }

    object Table : UUIDTable("authentication_flows") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expires_at")

        val tenant = optReference("tenant", ExposedTenantRepository.Table, onDelete = ReferenceOption.CASCADE)
        val account = optReference("account", ExposedAccountRepository.Table, onDelete = ReferenceOption.CASCADE)
    }

    object StepsTable : UUIDTable("authentication_flow_steps") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val flow = reference("flow", Table, onDelete = ReferenceOption.CASCADE)
        val stepFqdn = text("step_fqdn")
    }
}