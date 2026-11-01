package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IMagicLink
import com.jasminesoftwaresolutions.id.domain.testing.FakeAccount
import com.jasminesoftwaresolutions.id.domain.testing.FakeAccountRepository
import com.jasminesoftwaresolutions.id.domain.testing.FakeMagicLink
import com.jasminesoftwaresolutions.id.domain.testing.FakeMagicLinkRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Approval, which is where an emailed link becomes a verified address.
 *
 * Clicking a link that was delivered to an address proves control of that
 * inbox, and that is the whole basis for `emailVerified` on the sign-up path —
 * so the flip belongs here, at the single point every approval goes through,
 * rather than at the caller that happens to know which flow produced the link.
 */
class StandardMagicLinkServiceTest {

    private val links = FakeMagicLinkRepository()
    private val accounts = FakeAccountRepository()

    private val service = StandardMagicLinkService(
        repository = links,
        accountRepository = accounts,
        lifetime = 15.minutes,
    )

    private fun account(verified: Boolean = false): FakeAccount =
        accounts.create {
            this.email = "alice@example.com"
            this.firstName = ""
            this.lastName = ""
            this.emailVerified = verified
        } as FakeAccount

    private fun link(account: FakeAccount): FakeMagicLink =
        FakeMagicLink(account = account).also { links.entries += it }

    @Test
    fun `approving records the decision and verifies the address`() {
        val account = account()
        val link = link(account)

        service.approve(link)

        assertTrue(link.approved)
        assertNotNull(link.decidedAt)
        assertTrue(account.emailVerified)
        assertEquals(listOf(link), links.updates)
        assertEquals(listOf(account), accounts.updates)
    }

    @Test
    fun `approving again verifies once`() {
        // The link page can be reloaded, and the browser is not the only thing
        // that retries. Re-approving still records the link's decision — the
        // latest approval is the one that stands — but the *account* write is
        // once-only: an update per reload for a state that is already true is
        // pure churn, and it would move the account's audit trail with it.
        val account = account()
        val link = link(account)

        service.approve(link)
        val updatesAfterFirst = accounts.updates.size

        service.approve(link)

        assertTrue(link.approved)
        assertTrue(account.emailVerified)
        assertEquals(updatesAfterFirst, accounts.updates.size, "an already-verified account is not written again")
    }

    @Test
    fun `an account that is already verified is left alone`() {
        // A password sign-in or an earlier link may have verified it; approving
        // again is not a reason to touch the row.
        val account = account(verified = true)
        val link = link(account)

        service.approve(link)

        assertTrue(link.approved)
        assertTrue(accounts.updates.isEmpty(), "no write should have been made: ${accounts.updates}")
    }

    @Test
    fun `a link that no repository holds is refused`() {
        // `approve` persists through the repository, which needs the row. A
        // detached link reaching here is a caller passing the wrong object —
        // and silently verifying the address without recording the approval is
        // the failure that would hide.
        val detachedAccount = account()
        val detached = object : IMagicLink {
            override val id: UUID = UUID.randomUUID()
            override val createdAt: Instant = Instant.EPOCH
            override var expiresAt: Instant = Instant.EPOCH.plusSeconds(3600)
            override var account: IAccount = detachedAccount
            override var decidedAt: Instant? = null
            override var approved: Boolean = false
            override var consumed: Boolean = false
            override var decisionToken: String = "t"
            override var acceptanceToken: String = "t"
            override fun verifyDecisionToken(token: String) = false
            override fun verifyAcceptanceToken(token: String) = false
        }

        assertThrows(IllegalArgumentException::class.java) { service.approve(detached) }

        assertEquals(false, detached.account.emailVerified)
        assertTrue(accounts.updates.isEmpty())
    }

    @Test
    fun `a created link carries both tokens and expires in the configured window`() {
        val account = account()

        val created = service.create(
            account,
            function = {
                decisionToken = "decision"
                acceptanceToken = "acceptance"
            },
        )

        assertTrue(created.verifyDecisionToken("decision"))
        assertTrue(created.verifyAcceptanceToken("acceptance"))
        assertTrue(created.expiresAt.isAfter(Instant.now().plusSeconds(14 * 60)))
    }
}
