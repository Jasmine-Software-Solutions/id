package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.events.AccountCreatedEvent
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.services.authentication.ContinueAuthenticationFlowStepResult
import com.jasminesoftwaresolutions.id.domain.services.authentication.RetryAuthenticationFlowStepResult
import com.jasminesoftwaresolutions.id.domain.testing.FakeAccount
import com.jasminesoftwaresolutions.id.domain.testing.FakeAccountRepository
import com.jasminesoftwaresolutions.id.domain.testing.FakeFlow
import com.jasminesoftwaresolutions.id.domain.testing.FakeFlowRepository
import com.jasminesoftwaresolutions.id.domain.testing.FakeTenant
import com.jasminesoftwaresolutions.id.domain.testing.FakeTenantMembership
import com.jasminesoftwaresolutions.id.domain.testing.FakeTenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.testing.RecordingEventDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The email step, which is also where an account comes into existence.
 *
 * Signing in by magic link is the only sign-up route an applicant has: they
 * type their address at the login page and the account exists by the time they
 * approve the link. Two things about that are easy to get wrong and are pinned
 * here — a case variant of a known address must resolve to the existing row
 * rather than try to insert a second one past a case-sensitive unique index, and
 * a tenant-scoped flow (an invitation) must keep its old semantics of "unknown
 * email resolves to nothing".
 */
class EnterEmailAddressAuthenticationFlowStepTest {

    private val accounts = FakeAccountRepository()
    private val memberships = FakeTenantMembershipRepository()
    private val flows = FakeFlowRepository()
    private val events = RecordingEventDispatcher()

    private val handler = EnterEmailAddressAuthenticationFlowStep.Handler(
        accountRepository = accounts,
        tenantMembershipRepository = memberships,
        flowRepository = flows,
        eventDispatcher = events,
    )

    private fun seed(email: String, firstName: String = "Alice", lastName: String = "Smith"): FakeAccount =
        accounts.create {
            this.email = email
            this.firstName = firstName
            this.lastName = lastName
            this.emailVerified = true
        } as FakeAccount

    private fun accept(flow: FakeFlow, email: String?) =
        handler.accept(flow, EnterEmailAddressAuthenticationFlowStep.Request, EnterEmailAddressAuthenticationFlowStep.Response(email))

    // -- The plain sign-in path ------------------------------------------------

    @Test
    fun `an unknown email signs up`() {
        val flow = FakeFlow(ipAddress = "203.0.113.7")

        val result = accept(flow, "alice@example.com")

        assertEquals(ContinueAuthenticationFlowStepResult, result)

        val created = accounts.rows.single()
        assertEquals("alice@example.com", created.email)
        // Blank names are what the name step keys on, so this is the marker that
        // makes an account eligible for it.
        assertEquals("", created.firstName)
        assertEquals("", created.lastName)
        assertSame(created, flow.account)
    }

    @Test
    fun `signing up dispatches the creation the Witchwood syncer waits for`() {
        val flow = FakeFlow(ipAddress = "203.0.113.7")

        accept(flow, "alice@example.com")

        val created = accounts.rows.single()
        val event = events.events.single() as AccountCreatedEvent
        assertEquals(IdActions.CREATE_ACCOUNT, event.action)
        // The account is its own principal: nobody acted on its behalf. This is
        // the shape every creation on this path has, and it is worth pinning
        // down because it is also true of the invitation path — while the
        // rapid-creation detector, which once read the equality to exempt
        // sign-ups, deliberately does not distinguish them any more.
        assertEquals(created.id, event.principalId)
        assertEquals(created.id, event.accountId)
        // Unverified until the link is approved — clicking it is what proves the
        // inbox.
        assertEquals(false, event.emailVerified)
        assertEquals("203.0.113.7", event.ipAddress)
    }

    @Test
    fun `a known email resolves without creating anything`() {
        val existing = seed("alice@example.com")
        val flow = FakeFlow()

        val result = accept(flow, "alice@example.com")

        assertEquals(ContinueAuthenticationFlowStepResult, result)
        assertSame(existing, flow.account)
        assertEquals(1, accounts.rows.size)
        assertTrue(events.events.isEmpty(), "no sign-up, so no creation event: ${events.events}")
    }

    @Test
    fun `a case variant resolves to the existing row instead of duplicating it`() {
        // The unique index on `accounts.email` is case-sensitive, so inserting
        // "Alice@Example.COM" would succeed and leave two rows for one person —
        // hiring matches candidates to accounts by email equality.
        val existing = seed("alice@example.com")
        val flow = FakeFlow()

        val result = accept(flow, "Alice@Example.COM")

        assertEquals(ContinueAuthenticationFlowStepResult, result)
        assertSame(existing, flow.account)
        assertEquals(1, accounts.rows.size, "a case variant must not create a second account")
        assertTrue(events.events.isEmpty())
    }

    @Test
    fun `a sign-up stores the lowercased address, so a later case variant matches it`() {
        accept(FakeFlow(), "Alice@Example.COM")

        assertEquals("alice@example.com", accounts.rows.single().email)

        // The other half of the same guarantee: the row just created is found by
        // the typed form, so typing the address again in any case resolves to it.
        val flow = FakeFlow()
        accept(flow, "ALICE@example.com")

        assertEquals(1, accounts.rows.size)
        assertSame(accounts.rows.single(), flow.account)
    }

    @Test
    fun `a missing email asks again`() {
        val flow = FakeFlow()

        val result = accept(flow, null)

        assertEquals(RetryAuthenticationFlowStepResult, result)
        assertTrue(accounts.rows.isEmpty())
        assertNull(flow.account)
    }

    // -- The tenant-scoped path (invitations) ----------------------------------

    @Test
    fun `a tenant flow does not sign anyone up`() {
        // An invitation flow names the tenant it will join; an unknown address
        // there is a stranger on a tenant's login page, not a new account.
        val flow = FakeFlow(tenant = FakeTenant(UUID.randomUUID()))

        val result = accept(flow, "stranger@example.com")

        assertEquals(ContinueAuthenticationFlowStepResult, result)
        assertTrue(accounts.rows.isEmpty(), "a tenant flow must not create accounts")
        assertTrue(events.events.isEmpty())
        assertNull(flow.account)
    }

    @Test
    fun `a tenant flow resolves an existing account that is not a member to nothing`() {
        seed("alice@example.com")
        val flow = FakeFlow(tenant = FakeTenant(UUID.randomUUID()))

        val result = accept(flow, "alice@example.com")

        assertEquals(ContinueAuthenticationFlowStepResult, result)
        // The address is known, but this tenant is not one of its members — so
        // the flow carries on with no principal, which the flow engine tolerates.
        assertNull(flow.account)
    }

    @Test
    fun `a tenant flow resolves a member`() {
        val account = seed("alice@example.com")
        val tenant = FakeTenant(UUID.randomUUID())
        memberships.entries += FakeTenantMembership(tenant = tenant, account = account)
        val flow = FakeFlow(tenant = tenant)

        val result = accept(flow, "alice@example.com")

        assertEquals(ContinueAuthenticationFlowStepResult, result)
        assertSame(account, flow.account)
    }
}
