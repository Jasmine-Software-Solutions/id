package app.infrastructure.util

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import kotlin.properties.ReadWriteProperty

abstract class ExposedEntityWrapper(
    internal var row: ResultRow? = null,
    internal var insert: InsertStatement<Number>? = null,
    internal var update: UpdateStatement? = null
) {
    protected fun <T> column(
        column: Column<T>): ReadWriteProperty<Any?, T> =
        ExposedColumnDelegate(column, row, insert, update)

    protected fun <TColumn, TValue> column(
        column: Column<TColumn>,
        transformer: ExposedColumnTransformer<TColumn, TValue>
    ): ReadWriteProperty<Any?, TValue> = ExposedColumnDelegate(
        column = column,
        row = row,
        insert = insert,
        update = update,
        transformer = transformer
    )

    protected fun <T> nullableColumn(column: Column<T?>): ReadWriteProperty<Any?, T?> =
        ExposedColumnDelegate(column, row, insert, update)

    protected fun <TColumn, TValue> nullableColumn(
        column: Column<TColumn?>,
        transformer: ExposedColumnTransformer<TColumn?, TValue?>
    ): ReadWriteProperty<Any?, TValue?> = ExposedColumnDelegate(
        column = column,
        row = row,
        insert = insert,
        update = update,
        transformer = transformer
    )

    protected fun <T> requiredColumn(column: Column<T?>): ReadWriteProperty<Any?, T> =
        ExposedColumnDelegate(
            column = column,
            row = row,
            insert = insert,
            update = update,
            transformer = ExposedColumnTransformer(
                fromColumn = { it!! },
                toColumn = { it }
            )
        )

    protected fun <TColumn, TValue> requiredColumn(
        column: Column<TColumn?>,
        transformer: ExposedColumnTransformer<TColumn?, TValue>
    ): ReadWriteProperty<Any?, TValue> = ExposedColumnDelegate(
        column = column,
        row = row,
        insert = insert,
        update = update,
        transformer = transformer
    )
}
