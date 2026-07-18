package app.infrastructure.repositories.tenants

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.tenant.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITeam
import com.jasminesoftwaresolutions.id.domain.repositories.IOrganizationRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITeamRepository
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

class ExposedTeamRepository(
    val organizationRepository: IOrganizationRepository<IOrganization>
) : ExposedIdentifiedEntityRepository<ITeam, ExposedTeamRepository.Team>(Table),
    ITeamRepository<ITeam> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Team(row, insert, update)

    override fun transform(entity: ITeam): Team? = entity as? Team

    override fun findByOrganization(id: UUID): List<ITeam> = transaction {
        Table.select { Table.organization eq id }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    open inner class Team(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), ITeam {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var organization: IOrganization by column(Table.organization,
            EntityTransformer(ExposedOrganizationRepository.Table, organizationRepository))

        override var name: String by column(Table.name)
    }

    object Table : UUIDTable("teams") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val organization = reference("organization", ExposedOrganizationRepository.Table, onDelete = ReferenceOption.CASCADE)
        val name = varchar("name", 64)
    }
}
