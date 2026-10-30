package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.events.AccountProfileUpdatedEvent
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.services.authentication.ContinueAuthenticationFlowStepResult
import com.jasminesoftwaresolutions.id.domain.services.authentication.RetryAuthenticationFlowStepResult
import com.jasminesoftwaresolutions.id.domain.testing.FakeAccount
import com.jasminesoftwaresolutions.id.domain.testing.FakeAccountRepository
import com.jasminesoftwaresolutions.id.domain.testing.FakeFlow
import com.jasminesoftwaresolutions.id.domain.testing.RecordingEventDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The name step: the one thing standing between a magic-link sign-up and an
 * account with a usable name, and the only place names arrive for an applicant.
 *
 * A rejection here is a `Retry`, which is what makes the step re-ask rather than
 * complete with half a name — so what it refuses matters as much as what it
 * accepts.
 */
class EnterNameAuthenticationFlowStepTest {

    private val accounts = FakeAccountRepository()
    private val events = RecordingEventDispatcher()

    private val handler = EnterNameAuthenticationFlowStep.Handler(
        accountRepository = accounts,
        eventDispatcher = events,
    )

    /** The account the email step left behind: created, unverified, nameless. */
    private fun signingUp(): FakeAccount =
        accounts.create {
            this.email = "alice@example.com"
            this.firstName = ""
            this.lastName = ""
        } as FakeAccount

    private fun accept(flow: FakeFlow, firstName: String?, lastName: String?) =
        handler.accept(
            flow,
            EnterNameAuthenticationFlowStep.Request,
            EnterNameAuthenticationFlowStep.Response(firstName, lastName),
        )

    @Test
    fun `valid names are written to the account`() {
        val account = signingUp()
        val flow = FakeFlow(account = account)

        val result = accept(flow, "Alice", "O'Hara Smith")

        assertEquals(ContinueAuthenticationFlowStepResult, result)
        assertEquals("Alice", account.firstName)
        assertEquals("O'Hara Smith", account.lastName)
    }

    @Test
    fun `the update is recorded with what the names were before`() {
        val account = signingUp()
        val flow = FakeFlow(account = account)

        accept(flow, "Alice", "Smith")

        val event = events.events.single() as AccountProfileUpdatedEvent
        assertEquals(IdActions.UPDATE_ACCOUNT_PROFILE, event.action)
        assertEquals(account.id, event.principalId)
        // The previous values are the point of the audit entry: a blank-to-name
        // is the sign-up completing, and anything else is someone editing.
        assertEquals("", event.previousFirstName)
        assertEquals("Alice", event.newFirstName)
        assertEquals("", event.previousLastName)
        assertEquals("Smith", event.newLastName)
    }

    @Test
    fun `surrounding whitespace is trimmed rather than stored`() {
        val account = signingUp()

        accept(FakeFlow(account = account), "  Alice  ", "  Smith ")

        assertEquals("Alice", account.firstName)
        assertEquals("Smith", account.lastName)
    }

    @Test
    fun `a name of exactly the column width is accepted`() {
        val account = signingUp()
        val name = "a".repeat(EnterNameAuthenticationFlowStep.NAME_MAX_LENGTH)

        val result = accept(FakeFlow(account = account), name, name)

        assertEquals(ContinueAuthenticationFlowStepResult, result)
        assertEquals(name, account.firstName)
    }

    @Test
    fun `a name longer than the column is refused rather than truncated`() {
        // The column is `varchar(40)`. Truncating would silently keep a name the
        // applicant did not give, and the database would refuse it anyway.
        val account = signingUp()
        val tooLong = "a".repeat(EnterNameAuthenticationFlowStep.NAME_MAX_LENGTH + 1)

        val result = accept(FakeFlow(account = account), tooLong, "Smith")

        assertEquals(RetryAuthenticationFlowStepResult, result)
        assertEquals("", account.firstName)
        assertTrue(events.events.isEmpty(), "nothing was written, so nothing to audit: ${events.events}")
    }

    @Test
    fun `a blank name is refused`() {
        val account = signingUp()
        val flow = FakeFlow(account = account)

        assertEquals(RetryAuthenticationFlowStepResult, accept(flow, "", "Smith"))
        assertEquals(RetryAuthenticationFlowStepResult, accept(flow, "   ", "Smith"))
        assertEquals(RetryAuthenticationFlowStepResult, accept(flow, "Alice", ""))
        assertEquals(RetryAuthenticationFlowStepResult, accept(flow, "Alice", "   "))
    }

    @Test
    fun `a missing name is refused`() {
        val account = signingUp()
        val flow = FakeFlow(account = account)

        assertEquals(RetryAuthenticationFlowStepResult, accept(flow, null, "Smith"))
        assertEquals(RetryAuthenticationFlowStepResult, accept(flow, "Alice", null))
        assertEquals("", account.firstName)
        assertEquals("", account.lastName)
    }

    @Test
    fun `a refusal writes neither name`() {
        // Half a name is worse than none: the account would then pass the
        // registration condition if it were ever re-evaluated, and the applicant
        // would never be asked again.
        val account = signingUp()

        accept(FakeFlow(account = account), "Alice", "")

        assertEquals("", account.firstName)
        assertEquals("", account.lastName)
    }

    @Test
    fun `a flow with no account asks again`() {
        // `PollMagicLink` used to run with no account at all. The step cannot
        // complete for a principal that does not exist, and must not try.
        val result = accept(FakeFlow(account = null), "Alice", "Smith")

        assertEquals(RetryAuthenticationFlowStepResult, result)
        assertTrue(events.events.isEmpty())
    }

    @Test
    fun `the account is not replaced, only filled in`() {
        val account = signingUp()

        accept(FakeFlow(account = account), "Alice", "Smith")

        assertSame(account, accounts.rows.single())
        assertEquals(account.id, events.events.single().principalId)
    }
}
