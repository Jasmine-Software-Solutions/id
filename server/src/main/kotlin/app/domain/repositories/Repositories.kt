package app.domain.repositories

import app.domain.models.IIdentified
import java.util.*

interface IRepository<T> {
    fun create(function: (T).() -> Unit): T

    fun update(entity: T, function: T.() -> Unit)
    fun delete(entity: T)

    fun install()
}

interface IIdentifiedRepository<T : IIdentified> : IRepository<T> {
    fun findById(id: UUID): T?
}