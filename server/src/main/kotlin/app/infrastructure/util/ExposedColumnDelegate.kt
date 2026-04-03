package app.infrastructure.util

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private fun <T> writeValue(
    row: ResultRow?,
    insert: InsertStatement<Number>?,
    update: UpdateStatement?,
    column: Column<T>,
    value: T
) {
    row?.set(column, value)
    insert?.set(column, value)
    update?.set(column, value)
}

open class ExposedColumnTransformer<TColumn, TValue>(
    val fromColumn: (TColumn) -> TValue,
    val toColumn: (TValue) -> TColumn
)

class ExposedIdentityTransformer<T> : ExposedColumnTransformer<T, T>({ it }, { it })

private class MappedExposedColumnDelegate<TColumn, TValue>(
    private val column: Column<TColumn>,
    private val row: ResultRow?,
    private val insert: InsertStatement<Number>?,
    private val update: UpdateStatement?,
    private val transformer: ExposedColumnTransformer<TColumn, TValue>
) : ReadWriteProperty<Any?, TValue> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): TValue {
        return transformer.fromColumn(row!![column])
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: TValue) {
        writeValue(row, insert, update, column, transformer.toColumn(value))
    }
}

private fun <TColumn, TValue> mappedDelegate(
    column: Column<TColumn>,
    row: ResultRow?,
    insert: InsertStatement<Number>?,
    update: UpdateStatement?,
    transformer: ExposedColumnTransformer<TColumn, TValue>
): ReadWriteProperty<Any?, TValue> = MappedExposedColumnDelegate(
    column = column,
    row = row,
    insert = insert,
    update = update,
    transformer = transformer
)

fun <T> ExposedColumnDelegate(
    column: Column<T>,
    row: ResultRow?,
    insert: InsertStatement<Number>?,
    update: UpdateStatement?
): ReadWriteProperty<Any?, T> = mappedDelegate(
    column = column,
    row = row,
    insert = insert,
    update = update,
    transformer = ExposedIdentityTransformer()
)

fun <TColumn, TValue> ExposedColumnDelegate(
    column: Column<TColumn>,
    row: ResultRow?,
    insert: InsertStatement<Number>?,
    update: UpdateStatement?,
    transformer: ExposedColumnTransformer<TColumn, TValue>
): ReadWriteProperty<Any?, TValue> = mappedDelegate(
    column = column,
    row = row,
    insert = insert,
    update = update,
    transformer = transformer
)