package app.infrastructure.repositories.security

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.ExposedColumnTransformer
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableInstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.restriction.IRestriction
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionTargetType
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionType
import com.jasminesoftwaresolutions.id.domain.repositories.IRestrictionRepository
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ExposedRestrictionRepository : ExposedIdentifiedEntityRepository<IRestriction, ExposedRestrictionRepository.Restriction>(
    Table
), IRestrictionRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = Restriction(row, insert, update)

    override fun transform(entity: IRestriction): Restriction? = entity as? Restriction

    override fun findById(id: UUID): IRestriction? = transaction {
        Table.select { Table.id eq id }
            .firstOrNull()
            ?.let { Restriction(it, null, null) }
    }

    override fun findActive(targetType: RestrictionTargetType, targetId: String): List<IRestriction> = transaction {
        val now = System.currentTimeMillis()

        Table.select {
            (Table.targetType eq targetType.name) and
            (Table.targetId eq targetId) and
            (Table.expiresAt.isNull() or (Table.expiresAt greater now))
        }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { Restriction(it, null, null) }
            .toList()
    }

    override fun findAllActive(): List<IRestriction> = transaction {
        val now = System.currentTimeMillis()

        Table.select {
            Table.expiresAt.isNull() or (Table.expiresAt greater now)
        }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { Restriction(it, null, null) }
            .toList()
    }

    inner class Restriction(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IRestriction {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var type: RestrictionType by column(Table.type,
            ExposedColumnTransformer(
                fromColumn = { RestrictionType.valueOf(it) },
                toColumn = { it.name }
            )
        )

        override var targetType: RestrictionTargetType by column(Table.targetType,
            ExposedColumnTransformer(
                fromColumn = { RestrictionTargetType.valueOf(it) },
                toColumn = { it.name }
            )
        )

        override var targetId: String by column(Table.targetId)
        override var eventId: UUID? by nullableColumn(Table.eventId)
        override var durationMinutes: Int? by nullableColumn(Table.durationMinutes)
        override var expiresAt: Instant? by nullableColumn(Table.expiresAt, NullableInstantTransformer)
    }

    object Table : UUIDTable("restrictions") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val type = varchar("type", 32)
        val targetType = varchar("target_type", 32)
        val targetId = varchar("target_id", 255)
        val eventId = uuid("event_id").nullable()
        val durationMinutes = integer("duration_minutes").nullable()
        val expiresAt = long("expires_at").nullable()
    }
}
