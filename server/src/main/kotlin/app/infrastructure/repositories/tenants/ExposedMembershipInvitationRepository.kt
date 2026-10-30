package app.infrastructure.repositories.tenants

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableInstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.tenant.IHashedMembershipInvitation
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IMembershipInvitationRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedMembershipInvitationRepository(
    val hashFunction: IHashFunction,
    val tenantRepository: ITenantRepository,
) : ExposedIdentifiedEntityRepository<IHashedMembershipInvitation,
    ExposedMembershipInvitationRepository.MembershipInvitation>(Table),
    IMembershipInvitationRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = MembershipInvitation(row, insert, update)

    override fun transform(entity: IHashedMembershipInvitation): MembershipInvitation? =
        entity as? MembershipInvitation

    override fun clone(src: MembershipInvitation, dest: MembershipInvitation) {
        runCatching {
            dest.token = src.token
        }
    }

    override fun findByEmail(email: String): List<IHashedMembershipInvitation> = transaction {
        Table.select { Table.email eq email }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun findByToken(token: String): IHashedMembershipInvitation? = transaction {
        Table.selectAll()
            .map { row -> read(row, null, null) }
            .firstOrNull { invitation -> invitation.verify(token) }
    }

    override fun findPendingByEmailAndTenant(
        email: String,
        tenantId: UUID
    ): List<IHashedMembershipInvitation> = transaction {
        Table.select {
            (Table.email eq email) and
            (Table.acceptedAt.isNull()) and
            (Table.tenant eq tenantId)
        }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    open inner class MembershipInvitation(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHashedMembershipInvitation {
        private var _token: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var acceptedAt: Instant? by nullableColumn(Table.acceptedAt, NullableInstantTransformer)

        override var email: String by column(Table.email)

        override var tenant: ITenant by column(Table.tenant,
            EntityTransformer(ExposedTenantRepository.Table, tenantRepository))

        override var token: String
            get() = _token ?: throw UnsupportedOperationException(
                "IMembershipInvitation#token is transient and no longer accessible"
            )
            set(value) = hashFunction.hash(value.toByteArray()).let {
                row?.set(Table.tokenHash, it)
                insert?.set(Table.tokenHash, it)
                update?.set(Table.tokenHash, it)
                _token = value
            }

        override fun verify(token: String): Boolean {
            return hashFunction.verify(token.toByteArray(), row!![Table.tokenHash])
        }
    }

    object Table : UUIDTable("membership_invitations") {
        val email = varchar("email", 320)
        val tokenHash = text("token_hash")
        val tenant = reference("tenant_id", ExposedTenantRepository.Table, onDelete = ReferenceOption.CASCADE)
        val acceptedAt = long("accepted_at").nullable()
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        init {
            index(false, email)
            index(false, tenant)
        }
    }
}
