package com.jasminesoftwaresolutions.idinterfaces.application.handoff

import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.IToken
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IClientRedirectUris
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.client.IHashedDelegatedSessionCode
import com.jasminesoftwaresolutions.id.domain.models.handoff.HandoffStatus
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoff
import com.jasminesoftwaresolutions.id.domain.repositories.IHandoffRepository
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.CompiledResourcePath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture

class HandoffExchangeGuardTest {
    private val originalSession = FakeSession()
    private val account = FakeAccount()
    private val handoffClient = FakeClient()
    private val otherClient = FakeClient()

    private fun guard(
        handoff: IHandoff? = FakeHandoff(originalSession = originalSession, client = handoffClient),
        allowed: Boolean = true,
    ): HandoffExchangeGuard = HandoffExchangeGuard(
        FakeHandoffRepository(handoff),
        FakeAuthorizationService(allowed),
    )

    private fun delegatedSession(handoffId: UUID?) = FakeDelegatedSession(handoffId)

    @Test
    fun `a normal code is allowed without a handoff lookup`() {
        assertEquals(
            HandoffExchangeGuard.Decision.ALLOW,
            guard(handoff = null).evaluate(delegatedSession(null), handoffClient, null),
        )
    }

    @Test
    fun `a missing handoff fails closed`() {
        assertEquals(
            HandoffExchangeGuard.Decision.INVALID,
            guard(handoff = null).evaluate(delegatedSession(UUID.randomUUID()), handoffClient, null),
        )
    }

    @Test
    fun `a superseded handoff is invalid`() {
        val handoff = FakeHandoff(
            originalSession = originalSession,
            client = handoffClient,
            status = HandoffStatus.SUPERSEDED,
        )

        assertEquals(
            HandoffExchangeGuard.Decision.INVALID,
            guard(handoff).evaluate(delegatedSession(handoff.id), handoffClient, null),
        )
    }

    @Test
    fun `a client that did not create the handoff cannot exchange its code`() {
        val handoff = FakeHandoff(originalSession = originalSession, client = handoffClient)

        assertEquals(
            HandoffExchangeGuard.Decision.INVALID,
            guard(handoff).evaluate(delegatedSession(handoff.id), otherClient, null),
        )
    }

    @Test
    fun `a token-authenticated principal must be the exchanging client`() {
        val handoff = FakeHandoff(originalSession = originalSession, client = handoffClient)
        val auth = PrincipalAuthorizationContext.TokenAuth(FakeToken(subject = otherClient))

        assertEquals(
            HandoffExchangeGuard.Decision.INVALID,
            guard(handoff).evaluate(delegatedSession(handoff.id), handoffClient, auth),
        )
    }

    @Test
    fun `revoked AcceptHandoff permission forbids the exchange`() {
        val handoff = FakeHandoff(originalSession = originalSession, client = handoffClient)

        assertEquals(
            HandoffExchangeGuard.Decision.FORBIDDEN,
            guard(handoff, allowed = false).evaluate(delegatedSession(handoff.id), handoffClient, null),
        )
    }

    @Test
    fun `a claimed handoff with permission is allowed`() {
        val handoff = FakeHandoff(originalSession = originalSession, client = handoffClient)
        val auth = PrincipalAuthorizationContext.TokenAuth(FakeToken(subject = handoffClient))

        assertEquals(
            HandoffExchangeGuard.Decision.ALLOW,
            guard(handoff).evaluate(delegatedSession(handoff.id), handoffClient, auth),
        )
    }

    // ----- Fakes -----

    private class FakeAccount(override val id: UUID = UUID.randomUUID()) : IAccount {
        override val createdAt: Instant = Instant.EPOCH
        override var email: String = "user@example.com"
        override var firstName: String = "Ada"
        override var lastName: String = "Lovelace"
        override var emailVerified: Boolean = true
        override var ssoFavoriteClientIds: List<String> = emptyList()
        override var ssoClientOrder: List<String> = emptyList()
    }

    private class FakeSession(
        override val id: UUID = UUID.randomUUID(),
        override var account: IAccount = FakeAccount(),
        override var expiresAt: Instant = Instant.now().plusSeconds(3600),
    ) : IHashedSession {
        override val createdAt: Instant = Instant.EPOCH
        override var userAgent: String? = null
        override var ipAddress: String? = null
        override var token: String = "session-token"
        override fun verify(token: String): Boolean = token == this.token
    }

    private class FakeClient(override val id: UUID = UUID.randomUUID()) : IClient {
        override val createdAt: Instant = Instant.EPOCH
        override var suspended: Boolean = false
        override var fqdn: String = "app.example.com"
        override var confidential: Boolean = true
        override var name: String? = null
        override var logoUri: URI? = null
        override var initiateLoginUri: URI? = null
        override val redirectUris: IClientRedirectUris = object : IClientRedirectUris {
            override val values: List<URI> = listOf(URI.create("http://localhost/callback"))
            override fun add(uri: URI) = Unit
            override fun remove(uri: URI) = Unit
        }
        override var secret: String = "secret"
        override fun verify(secret: String): Boolean = secret == this.secret
    }

    private class FakeHandoff(
        override val id: UUID = UUID.randomUUID(),
        override var originalSession: IHashedSession,
        override var client: IClient,
        override var status: HandoffStatus = HandoffStatus.CLAIMED,
    ) : IHandoff {
        override val createdAt: Instant = Instant.EPOCH
        override var expiresAt: Instant = Instant.now().plusSeconds(3600)
        override var redirectUri: URI = URI.create("http://localhost/callback")
        override var state: String? = null
        override var scopes: Set<IScope> = emptySet()
        override var nonce: String? = null
        override var scanToken: String = ""
        override var scannedAt: Instant? = null
        override var scanUserAgent: String? = null
        override var scanIpAddress: String? = null
        override var confirmedAt: Instant? = null
        override var claimedAt: Instant? = null
        override var newSessionId: UUID? = null
        override var previousSessionId: UUID? = null
        override fun verifyScanToken(token: String): Boolean = false
    }

    private class FakeDelegatedSession(override var handoffId: UUID?) : IDelegatedSession {
        override val id: UUID = UUID.randomUUID()
        override val createdAt: Instant = Instant.EPOCH
        override var expiresAt: Instant = Instant.now().plusSeconds(3600)
        override var refreshedAt: Instant? = null
        override var session: IHashedSession = FakeSession()
        override var client: IClient = FakeClient()
        override var redirectUri: URI = URI.create("http://localhost/callback")
        override var scopes: Set<IScope> = emptySet()
        override var nonce: String? = null
        override val code: IHashedDelegatedSessionCode = object : IHashedDelegatedSessionCode {
            override val createdAt: Instant = Instant.EPOCH
            override var expiresAt: Instant = Instant.now().plusSeconds(600)
            override var code: String = "code"
            override fun verify(code: String): Boolean = code == this.code
        }
    }

    private class FakeToken(
        override val subject: IIdentified,
        override val id: UUID = UUID.randomUUID(),
    ) : IToken {
        override val token: String = ""
        override val expiresAt: Instant = Instant.now().plusSeconds(60)
        override val issuedAt: Instant = Instant.now()
        override val notBefore: Instant = issuedAt
        override val audience: List<String> = emptyList()
        override val scopes: Set<String> = emptySet()
    }

    private class FakeHandoffRepository(private val handoff: IHandoff?) : IHandoffRepository {
        override fun findById(id: UUID): IHandoff? = handoff?.takeIf { it.id == id }
        override fun findOwned(handoffId: UUID, originalSessionId: UUID): IHandoff? = null
        override fun findActiveForSession(originalSessionId: UUID): IHandoff? = null
        override fun findLatestCompletedForSession(originalSessionId: UUID): IHandoff? = null
        override fun createHandoff(
            originalSession: IHashedSession,
            client: IClient,
            redirectUri: URI,
            state: String?,
            scopes: Set<IScope>,
            nonce: String?,
            expiresAt: Instant,
            previousSessionId: UUID?,
        ): IHandoff = throw UnsupportedOperationException()

        override fun supersede(handoffId: UUID): IHandoff? = throw UnsupportedOperationException()
        override fun tryBeginScan(
            handoffId: UUID,
            scanToken: String,
            scannedAt: Instant,
            userAgent: String?,
            ipAddress: String?,
        ): IHandoff? = throw UnsupportedOperationException()
        override fun tryConfirm(handoffId: UUID, confirmedAt: Instant): IHandoff? = throw UnsupportedOperationException()
        override fun tryReject(handoffId: UUID): IHandoff? = throw UnsupportedOperationException()
        override fun tryClaim(handoffId: UUID, newSessionId: UUID, claimedAt: Instant): IHandoff? =
            throw UnsupportedOperationException()
        override fun tryChangeDevices(handoffId: UUID): IHandoff? = throw UnsupportedOperationException()
        override fun create(function: IHandoff.() -> Unit): IHandoff = throw UnsupportedOperationException()
        override fun update(entity: IHandoff, function: IHandoff.() -> Unit) = throw UnsupportedOperationException()
        override fun delete(entity: IHandoff) = throw UnsupportedOperationException()
        override fun install() = Unit
        override fun create(id: UUID, function: IHandoff.() -> Unit): IHandoff = throw UnsupportedOperationException()
    }

    private class FakeAuthorizationService(private val allowed: Boolean) : IAuthorizationService {
        override fun evaluate(
            principalId: UUID,
            action: String,
            resource: CompiledResourcePath,
            context: Map<String, String>,
        ): CompletableFuture<Boolean> = CompletableFuture.completedFuture(allowed)

        override fun queryResources(
            principalId: UUID,
            action: String,
        ): CompletableFuture<Set<CompiledResourcePath>> = CompletableFuture.completedFuture(emptySet())
    }
}
