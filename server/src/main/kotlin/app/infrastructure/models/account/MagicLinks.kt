package app.infrastructure.models.account

import app.infrastructure.etc.transformInstant
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*

object MagicLinksTable : UUIDTable("magic_links") {
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")

    val account = reference("account_id", AccountsTable, onDelete = ReferenceOption.CASCADE)

    val decisionToken = varchar("decision_token", 32).uniqueIndex()
    val acceptanceTokenHash = text("argon2_acceptance_token_hash")
}

class MagicLink(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MagicLink>(MagicLinksTable)

    var createdAt by MagicLinksTable.createdAt.transformInstant()
    var expiresAt by MagicLinksTable.expiresAt.transformInstant()

    var account by Account referencedOn MagicLinksTable.account

    var decisionToken by MagicLinksTable.decisionToken
    var acceptanceTokenHash by MagicLinksTable.acceptanceTokenHash

    val decision by MagicLinkDecision optionalBackReferencedOn MagicLinkDecisionsTable.magicLink
}

object MagicLinkDecisionsTable : UUIDTable("magic_link_decisions") {
    val createdAt = long("created_at")
    val magicLink = reference("magic_link", MagicLinksTable, onDelete = ReferenceOption.CASCADE)

    val approved = bool("approved")
    val consumed = bool("consumed")
}

class MagicLinkDecision(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MagicLinkDecision>(MagicLinkDecisionsTable)

    var createdAt by MagicLinkDecisionsTable.createdAt.transformInstant()
    var magicLink by MagicLink referencedOn MagicLinkDecisionsTable.magicLink

    var approved by MagicLinkDecisionsTable.approved
    var consumed by MagicLinkDecisionsTable.consumed
}