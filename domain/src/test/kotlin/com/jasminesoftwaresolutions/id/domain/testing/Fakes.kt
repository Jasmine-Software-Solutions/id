package com.jasminesoftwaresolutions.id.domain.testing

import com.jasminesoftwaresolutions.id.domain.events.Event
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedMagicLink
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlowSteps
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IAuthenticationFlowRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IMagicLinkRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStep
import java.time.Instant
import java.util.UUID

/**
 * In-memory stand-ins for the repositories the authentication steps write to.
 *
 * Writable entities, not recording stubs: every model in this domain is a
 * mutable interface, so a fake that hands the *same* instance back from
 * `create`/`update` — as the Exposed repositories do by attaching the row to
 * the entity — reproduces the only behaviour the steps depend on. Assertions
 * then read the state the way the next reader of the database would.
 *
 * Only what the classes under test call is implemented; anything else throws,
 * so a test that reaches for a collaborator its subject does not use fails
 * rather than silently passing.
 */

class FakeAccount(
    override val id: UUID = UUID.randomUUID(),
    override val createdAt: Instant = Instant.now(),
    override var email: String = "",
    override var firstName: String = "",
    override var lastName: String = "",
    override var emailVerified: Boolean = false,
) : IAccount {
    override var ssoFavoriteClientIds: List<String> = emptyList()
    override var ssoClientOrder: List<String> = emptyList()

    override fun toString(): String = "FakeAccount($email)"
}

/**
 * The `accounts` table, with its **case-sensitive** unique index on `email`.
 *
 * [findByEmail] matches exactly, as the database index does — a case-insensitive
 * fake would pass a test that the real deployment fails, which is the whole
 * reason the lookup in the email step tries the typed form and then the
 * lowercased one.
 */
class FakeAccountRepository : IAccountRepository {
    val rows = mutableListOf<IAccount>()

    /** Every account handed to [update], so "wrote nothing" can be asserted. */
    val updates = mutableListOf<IAccount>()

    override fun findByEmail(email: String): IAccount? =
        rows.firstOrNull { it.email == email }

    override fun entries(): List<IAccount> = rows

    override fun findById(id: UUID): IAccount? = rows.firstOrNull { it.id == id }

    override fun create(function: IAccount.() -> Unit): IAccount =
        FakeAccount().also {
            it.function()
            rows += it
        }

    override fun create(id: UUID, function: IAccount.() -> Unit): IAccount =
        FakeAccount(id).also {
            it.function()
            rows += it
        }

    override fun update(entity: IAccount, function: IAccount.() -> Unit) {
        if (entity !in rows) throw IllegalStateException("update() on an account that was never created")
        entity.function()
        updates += entity
    }

    override fun delete(entity: IAccount) {
        rows -= entity
    }

    override fun install() = Unit
}

class FakeTenant(override val id: UUID) : ITenant {
    override var suspended: Boolean = false
    override var name: String = "fake-tenant"
    override val createdAt: Instant = Instant.EPOCH
}

class FakeTenantMembership(
    override val id: UUID = UUID.randomUUID(),
    override var tenant: ITenant,
    override var account: IAccount,
) : ITenantMembership {
    override val createdAt: Instant = Instant.EPOCH
}

class FakeTenantMembershipRepository : ITenantMembershipRepository {
    val entries = mutableListOf<FakeTenantMembership>()

    override fun findByAccount(id: UUID): List<ITenantMembership> =
        entries.filter { it.account.id == id }

    override fun findByTenant(id: UUID): List<ITenantMembership> =
        entries.filter { it.tenant.id == id }

    override fun findById(id: UUID): ITenantMembership? = entries.firstOrNull { it.id == id }

    override fun create(function: ITenantMembership.() -> Unit): ITenantMembership =
        throw UnsupportedOperationException("the membership repository is read-only in these tests")

    override fun create(id: UUID, function: ITenantMembership.() -> Unit): ITenantMembership =
        throw UnsupportedOperationException("the membership repository is read-only in these tests")

    override fun update(entity: ITenantMembership, function: ITenantMembership.() -> Unit) =
        throw UnsupportedOperationException("the membership repository is read-only in these tests")

    override fun delete(entity: ITenantMembership) =
        throw UnsupportedOperationException("the membership repository is read-only in these tests")

    override fun install() = Unit
}

class FakeFlow(
    override val id: UUID = UUID.randomUUID(),
    override var tenant: ITenant? = null,
    override var account: IAccount? = null,
    override var ipAddress: String = "127.0.0.1",
) : IAuthenticationFlow {
    override val createdAt: Instant = Instant.EPOCH
    override var expiresAt: Instant = Instant.EPOCH.plusSeconds(3600)
    override val steps: IAuthenticationFlowSteps = FakeFlowSteps()
    override var userAgent: String? = null
    override var state: String? = null
}

class FakeFlowSteps : IAuthenticationFlowSteps {
    private val mutable = mutableListOf<AuthenticationFlowStep>()
    override val values: List<AuthenticationFlowStep> get() = mutable
    override fun add(step: AuthenticationFlowStep) { mutable += step }
    override fun replace(step: AuthenticationFlowStep) { mutable[mutable.lastIndex] = step }
}

class FakeFlowRepository : IAuthenticationFlowRepository {
    val updates = mutableListOf<FakeFlow>()

    override fun findById(id: UUID): IAuthenticationFlow? =
        updates.firstOrNull { it.id == id }

    override fun create(function: IAuthenticationFlow.() -> Unit): IAuthenticationFlow =
        throw UnsupportedOperationException("flows are created by the flow service, not by these tests")

    override fun create(id: UUID, function: IAuthenticationFlow.() -> Unit): IAuthenticationFlow =
        throw UnsupportedOperationException("flows are created by the flow service, not by these tests")

    override fun update(entity: IAuthenticationFlow, function: IAuthenticationFlow.() -> Unit) {
        (entity as FakeFlow).function()
        updates += entity
    }

    override fun delete(entity: IAuthenticationFlow) = Unit

    override fun install() = Unit
}

class FakeMagicLink(
    override val id: UUID = UUID.randomUUID(),
    override var account: IAccount,
) : IHashedMagicLink {
    override val createdAt: Instant = Instant.EPOCH
    override var expiresAt: Instant = Instant.EPOCH.plusSeconds(3600)
    override var decidedAt: Instant? = null
    override var approved: Boolean = false
    override var consumed: Boolean = false
    override var decisionToken: String = "decision-token"
    override var acceptanceToken: String = "acceptance-token"

    override fun verifyDecisionToken(token: String): Boolean = token == decisionToken
    override fun verifyAcceptanceToken(token: String): Boolean = token == acceptanceToken
}

class FakeMagicLinkRepository : IMagicLinkRepository {
    val entries = mutableListOf<FakeMagicLink>()
    val updates = mutableListOf<FakeMagicLink>()

    override fun findByAccount(id: UUID): List<IHashedMagicLink> =
        entries.filter { it.account.id == id }

    override fun findById(id: UUID): IHashedMagicLink? = entries.firstOrNull { it.id == id }

    override fun create(function: IHashedMagicLink.() -> Unit): IHashedMagicLink =
        create(UUID.randomUUID(), function)

    override fun create(id: UUID, function: IHashedMagicLink.() -> Unit): IHashedMagicLink =
        FakeMagicLink(id, FakeAccount()).also {
            it.function()
            entries += it
        }

    override fun update(entity: IHashedMagicLink, function: IHashedMagicLink.() -> Unit) {
        entity.function()
        updates += entity as FakeMagicLink
    }

    override fun delete(entity: IHashedMagicLink) {
        entries -= entity as FakeMagicLink
    }

    override fun install() = Unit
}

/** Every event the subject dispatched, in order. */
class RecordingEventDispatcher : IEventDispatcher {
    val events = mutableListOf<Event>()
    override fun dispatch(event: Event) {
        events += event
    }
}
