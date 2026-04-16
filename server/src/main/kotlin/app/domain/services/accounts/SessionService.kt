package app.domain.services.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IHashedSession
import app.domain.models.account.ISession
import app.domain.repositories.ISessionRepository
import app.infrastructure.etc.SecureToken
import java.time.Instant
import java.util.*
import kotlin.time.Duration

interface ISessionService<T : ISession> {
    fun create(account: IAccount, function: (ISession).() -> (Unit) = {}): T
}

class HashedSessionService(
    private val repository: ISessionRepository,
    private val lifetime: Duration
) : ISessionService<IHashedSession> {
    override fun create(account: IAccount, function: (ISession).() -> Unit ): IHashedSession {
        val token = SecureToken()
        val session = repository.create {
            this.expiresAt = Instant.now().plusMillis(lifetime.inWholeMilliseconds)
            this.account = account

            (this as IHashedSession).token = token
            function(this)
        } as IHashedSession

        return Session(session, token)
    }

    class Session(private val session: IHashedSession, private var _token: String) : IHashedSession {
        override val id: UUID
            get() = session.id

        override val createdAt: Instant
            get() = session.createdAt

        override var expiresAt: Instant
        get() = session.expiresAt
        set(value) { session.expiresAt = value }

        override var userAgent: String
        get() = session.userAgent
        set(value) { session.userAgent = value }

        override var ipAddress: String
        get() = session.ipAddress
        set(value) { session.ipAddress = value }

        override var account: IAccount
        get() = session.account
        set(value) { session.account = value }

        override var token: String
            get() = _token
            set(value) {
                session.token = value
                _token = value
            }

        override fun verify(token: String): Boolean {
            return session.verify(token)
        }
    }
}