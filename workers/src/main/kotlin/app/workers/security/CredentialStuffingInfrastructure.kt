package app.workers.security

import app.infrastructure.repositories.accounts.ExposedPasswordRepository
import app.infrastructure.repositories.accounts.ExposedSessionRepository
import app.workers.security.detectors.PasswordDeleter
import app.workers.security.detectors.SessionRevoker
import com.jasminesoftwaresolutions.id.domain.events.AllSessionsRevokedEvent
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Revokes all active sessions for an account by extracting the last session
 * token from the database and then deleting them. Emits [AllSessionsRevokedEvent].
 */
class ExposedSessionRevoker(
    private val sessionRepository: ExposedSessionRepository,
) : SessionRevoker {
    private val log = LoggerFactory.getLogger(ExposedSessionRevoker::class.java)

    override fun revokeAllSessions(accountId: UUID, clientId: UUID, eventDispatcher: IEventDispatcher) {
        try {
            val sessions = sessionRepository.findByAccount(accountId)
            if (sessions.isEmpty()) return

            sessions.forEach { sessionRepository.delete(it) }

            log.info("Revoked ${sessions.size} sessions for account=$accountId (credential stuffing)")

            eventDispatcher.dispatch(
                AllSessionsRevokedEvent(
                    principalId = clientId,
                    action = "${IdActions.SERVICE}:AllSessionsRevoked",
                    resource = IdResources.platform().toString(),
                    timestamp = Instant.now(),
                    accountId = accountId,
                    revokedCount = sessions.size,
                )
            )
        } catch (e: Exception) {
            log.error("Failed to revoke sessions for account=$accountId: ${e.message}", e)
        }
    }
}

/**
 * Deletes the last (current) password for an account, forcing the user to
 * set a new one. Emits no event — the CredentialStuffingSucceededSecurityEvent
 * already covers the notification.
 */
class ExposedPasswordDeleter(
    private val passwordRepository: ExposedPasswordRepository,
) : PasswordDeleter {
    private val log = LoggerFactory.getLogger(ExposedPasswordDeleter::class.java)

    override fun deletePassword(accountId: UUID) {
        try {
            val passwords = passwordRepository.findByAccount(accountId)
            if (passwords.isEmpty()) return

            // Delete all password records for this account
            passwords.forEach { passwordRepository.delete(it) }

            log.info("Deleted ${passwords.size} password(s) for account=$accountId (credential stuffing)")
        } catch (e: Exception) {
            log.error("Failed to delete passwords for account=$accountId: ${e.message}", e)
        }
    }
}
