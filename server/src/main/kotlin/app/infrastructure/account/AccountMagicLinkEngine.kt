package app.infrastructure.account

import app.Env
import app.infrastructure.etc.SecureToken
import app.infrastructure.models.account.Account
import app.infrastructure.models.account.MagicLink
import app.infrastructure.models.account.MagicLinkDecision
import app.infrastructure.models.account.MagicLinksTable
import de.mkammerer.argon2.Argon2Factory
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

object AccountMagicLinkEngine {
    private val argon2 = Argon2Factory.create(
        Env.PASSWORD_SALT_LENGTH,
        1024
    )

    fun create(
        account: Account,
        expiration: Instant,
        acceptanceToken: String
    ): MagicLink = transaction {
        val tokenArray = acceptanceToken.toCharArray()
        val hashedToken = try {
            argon2.hash(
                Env.PASSWORD_HASH_ITERATIONS,
                Env.ARGON2_MEMORY,
                Env.ARGON2_PARALLELISM,
                tokenArray
            )
        } finally {
            argon2.wipeArray(tokenArray)
        }

        return@transaction MagicLink.new {
            this.createdAt = Instant.now()
            this.expiresAt = expiration

            this.account = account

            this.decisionToken = SecureToken()
            this.acceptanceTokenHash = hashedToken
        }
    }

    fun decide(decisionToken: String, approved: Boolean): Boolean = transaction {
        val link = MagicLink.find { MagicLinksTable.decisionToken eq decisionToken }.firstOrNull()
            ?.takeIf { it.expiresAt > Instant.now() }
            ?.takeIf { it.decision == null }
            ?: return@transaction false

        MagicLinkDecision.new {
            this.createdAt = Instant.now()
            this.magicLink = link

            this.approved = approved
            this.consumed = false
        }

        return@transaction true
    }

    fun approved(id: UUID, acceptanceTokenArray: CharArray): Boolean = transaction {
        val link = MagicLink.findById(id)
            ?.takeIf { it.expiresAt > Instant.now() }
            ?.takeIf { it.decision != null }
            ?.takeIf { it.decision?.approved == true }
            ?.takeIf { it.decision?.consumed == false }
            ?: return@transaction false

        val decision = link.decision!!

        val argon2 = Argon2Factory.create()
        val isToken = try {
            argon2.verify(link.acceptanceTokenHash, acceptanceTokenArray)
        } finally {
            argon2.wipeArray(acceptanceTokenArray)
        }

        if (isToken)
            decision.consumed = true

        return@transaction isToken
    }
}