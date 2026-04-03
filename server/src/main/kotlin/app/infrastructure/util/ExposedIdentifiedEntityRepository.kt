package app.infrastructure.util

import app.domain.models.IIdentified
import app.domain.repositories.IIdentifiedRepository
import app.infrastructure.models.account.AccountsTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.reflect.KClass

abstract class ExposedIdentifiedEntityRepository<T, TWrapper>(
    val table: UUIDTable,
    val wrapperClazz: KClass<TWrapper>
) : IIdentifiedRepository<T> where T : IIdentified, TWrapper : ExposedEntityWrapper {
    abstract fun read(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null): T

    open fun writeDefaults(id: UUID, it: InsertStatement<Number>) {
        it[table.id] = id
    }

    override fun findById(id: UUID): T? = transaction {
        val row = table.select { table.id eq id }.firstOrNull()
            ?: return@transaction null

        return@transaction read(row, null, null)
    }

    override fun create(function: T.() -> Unit): T = transaction {
        val entityId = UUID.randomUUID()
        table.insert {
            val entity = read(null, it, null)
            function(entity)

            writeDefaults(entityId, it)
        }

        return@transaction findById(entityId)!!
    }

    override fun update(entity: T, function: T.() -> Unit) = transaction {
        val isExposedEntity = entity::class == wrapperClazz
        val oldWrappedEntity = if (isExposedEntity) entity as ExposedEntityWrapper else null

        table.update({ AccountsTable.id eq entity.id }) {
            val row = oldWrappedEntity?.row

            val newWrappedEntity = read(row, null, it)
            function(newWrappedEntity)

            if (isExposedEntity) {
                oldWrappedEntity?.row = row
                oldWrappedEntity?.insert = null
                oldWrappedEntity?.update = null
            }
        }

        Unit
    }

    override fun delete(entity: T) = transaction {
        table.deleteWhere { table.id eq entity.id }
        Unit
    }
}