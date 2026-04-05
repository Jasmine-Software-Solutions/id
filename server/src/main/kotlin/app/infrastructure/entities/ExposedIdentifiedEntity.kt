package app.infrastructure.entities

import app.domain.models.IIdentified
import app.infrastructure.util.EntityIdTransformer
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.util.*

open class ExposedIdentifiedEntity(
    table: IdTable<UUID>,
    row: ResultRow? = null,
    insert: InsertStatement<Number>? = null,
    update: UpdateStatement? = null
) : ExposedEntity(row, insert, update), IIdentified {
    override val id: UUID by column(table.id, EntityIdTransformer(table))
}