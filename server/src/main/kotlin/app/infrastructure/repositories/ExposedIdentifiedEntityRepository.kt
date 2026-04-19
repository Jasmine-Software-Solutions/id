package app.infrastructure.repositories

import app.infrastructure.entities.ExposedEntity
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.repositories.IIdentifiedRepository
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
) : IIdentifiedRepository<T> where T : IIdentified, TWrapper : ExposedEntity {
    abstract fun read(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null): T

    open fun clone(src: TWrapper, dest: TWrapper) {}

    open fun writeDefaults(id: UUID, entity: T, it: InsertStatement<Number>) {
        it[table.id] = id
    }

    open fun createDependents(id: UUID, entity: T) {}

    override fun findById(id: UUID): T? = transaction {
        val row = table.select { table.id eq id }.firstOrNull()
            ?: return@transaction null

        return@transaction read(row, null, null)
    }

    override fun create(function: T.() -> Unit): T = transaction {
        val entityId = UUID.randomUUID()
        lateinit var entity: T

        table.insert {
            entity = read(null, it, null)
            function(entity)

            writeDefaults(entityId, entity, it)
        }

        createDependents(entityId, entity)

        return@transaction findById(entityId)!!
    }

    override fun update(entity: T, function: T.() -> Unit) = transaction {
        val isExposedEntity = entity::class == wrapperClazz
        val oldWrappedEntity = if (isExposedEntity) entity as ExposedEntity else null

        try {
            table.update({ table.id eq entity.id }) {
                val row = oldWrappedEntity?.row

                val newWrappedEntity = read(row, null, it)
                function(newWrappedEntity)

                if (isExposedEntity && oldWrappedEntity != null) {
                    oldWrappedEntity.row = row
                    oldWrappedEntity.insert = null
                    oldWrappedEntity.update = null

                    this@ExposedIdentifiedEntityRepository.clone(
                        src = newWrappedEntity as TWrapper,
                        dest = oldWrappedEntity as TWrapper
                    )
                }
            }
        } catch (ex: IllegalArgumentException) {}

        Unit
    }

    override fun delete(entity: T) = transaction {
        table.deleteWhere { table.id eq entity.id }
        Unit
    }

    override fun install() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                table
            )
        }
    }

    init {
        install()
    }
}