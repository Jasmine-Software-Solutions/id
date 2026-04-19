package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import java.util.*

interface IRepository<T> {
    fun create(function: (T).() -> Unit): T

    fun update(entity: T, function: T.() -> Unit)
    fun delete(entity: T)

    fun install()
}

interface IIdentifiedRepository<T : IIdentified> :
    IRepository<T> {
    fun findById(id: UUID): T?
}