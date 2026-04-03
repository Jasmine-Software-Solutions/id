package app.infrastructure.util

import app.domain.models.IIdentified
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.util.*

open class ExposedIdentifiedEntityWrapper(
    table: IdTable<UUID>,
    row: ResultRow? = null,
    insert: InsertStatement<Number>? = null,
    update: UpdateStatement? = null
) : ExposedEntityWrapper(row, insert, update), IIdentified {
    override val id: UUID by column(table.id, EntityIdTransformer(table))
}