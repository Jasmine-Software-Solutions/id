package app.infrastructure.util

import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.repositories.IIdentifiedRepository
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.time.Instant
import java.util.*

object InstantTransformer : ExposedColumnTransformer<Long, Instant>(
    fromColumn = Instant::ofEpochMilli,
    toColumn = Instant::toEpochMilli
)

object NullableInstantTransformer : ExposedColumnTransformer<Long?, Instant?>(
    fromColumn = { it?.let { Instant.ofEpochMilli(it) } },
    toColumn = { it?.toEpochMilli() }
)

class EntityTransformer<T : IIdentified>(val table: IdTable<UUID>, val repository: IIdentifiedRepository<out T>) : ExposedColumnTransformer<EntityID<UUID>, T>(
    fromColumn = { repository.findById(it.value)!! },
    toColumn = { EntityID(it.id, table) }
)

class NullableEntityTransformer<T : IIdentified>(val table: IdTable<UUID>, val repository: IIdentifiedRepository<out T>) : ExposedColumnTransformer<EntityID<UUID>?, T?>(
    fromColumn = { it?.let { repository.findById(it.value) } },
    toColumn = { it?.id?.let { EntityID(it, table) } }
)

class EntityIdTransformer(val table: IdTable<UUID>) : ExposedColumnTransformer<EntityID<UUID>, UUID>(
    fromColumn = { it.value },
    toColumn = { EntityID(it, table) }
)

class NullableEntityIdTransformer(val table: IdTable<UUID>) : ExposedColumnTransformer<EntityID<UUID>?, UUID?>(
    fromColumn = { it?.value },
    toColumn = { it?.let { EntityID(it, table) } }
)