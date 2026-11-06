package app.infrastructure.repositories.handoffs

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoff
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoffQrToken
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoffQrTokenRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IHandoffRepository
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedHandoffQrTokenRepository(
    val hashFunction: IHashFunction,
    val handoffRepository: IHandoffRepository,
) : ExposedIdentifiedEntityRepository<IHandoffQrToken, ExposedHandoffQrTokenRepository.HandoffQrToken>(Table),
    IHandoffQrTokenRepository {

    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?) =
        HandoffQrToken(row, insert, update)

    override fun transform(entity: IHandoffQrToken): HandoffQrToken? = entity as? HandoffQrToken

    override fun clone(src: HandoffQrToken, dest: HandoffQrToken) {
        runCatching { dest.token = src.token }
    }

    override fun create(
        handoff: IHandoff,
        rawToken: String,
        expiresAt: Instant,
    ): IHandoffQrToken = create {
        this.handoff = handoff
        this.token = rawToken
        this.expiresAt = expiresAt
    }

    override fun findValid(handoffId: UUID, rawToken: String, now: Instant): IHandoffQrToken? = transaction {
        val digest = hashFunction.hash(rawToken.toByteArray())

        Table.select {
            (Table.handoff eq handoffId) and
                (Table.tokenHash eq digest) and
                (Table.expiresAt greater now.toEpochMilli())
        }
            .firstOrNull()
            ?.let { read(it, null, null) }
    }

    override fun deleteForHandoff(handoffId: UUID) {
        transaction {
            Table.deleteWhere { Table.handoff eq handoffId }
        }
    }

    override fun deleteExpired(now: Instant) {
        transaction {
            Table.deleteWhere { Table.expiresAt lessEq now.toEpochMilli() }
        }
    }

    inner class HandoffQrToken(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null,
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHandoffQrToken {
        private var _token: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)

        override var handoff: IHandoff by column(
            Table.handoff,
            EntityTransformer(ExposedHandoffRepository.Table, handoffRepository),
        )

        override var token: String
            get() = _token
                ?: throw UnsupportedOperationException("IHandoffQrToken#token is transient and no longer accessible")
            set(value) = hashFunction.hash(value.toByteArray()).let {
                row?.set(Table.tokenHash, it)
                insert?.set(Table.tokenHash, it)
                update?.set(Table.tokenHash, it)
                _token = value
            }

        override fun verify(token: String): Boolean {
            val hash = row?.get(Table.tokenHash) ?: return false
            return hashFunction.verify(token.toByteArray(), hash)
        }
    }

    object Table : UUIDTable("handoff_qr_tokens") {
        val handoff = reference("handoff", ExposedHandoffRepository.Table, onDelete = ReferenceOption.CASCADE)
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expires_at")
        val tokenHash = text("token_hash")

        init {
            index(false, handoff, tokenHash)
        }
    }
}
