package app.workers.security.detectors

import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.InvalidPasswordAtAuthenticationEvent
import com.jasminesoftwaresolutions.id.domain.events.InvalidTOTPAtAuthenticationEvent
import com.jasminesoftwaresolutions.id.domain.events.PasswordBruteForceAttemptedSecurityEvent
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import app.workers.security.RestrictionEnforcer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects brute force attacks on password and TOTP authentication.
 *
 * Tracks failures per (email, ip) in sliding windows. When a threshold is
 * exceeded, emits a security event and places account/IP restrictions.
 */
class BruteForceDetector(
    private val eventDispatcher: IEventDispatcher,
    private val enforcer: RestrictionEnforcer,
    private val clientId: UUID,
) {
    private val log = LoggerFactory.getLogger(BruteForceDetector::class.java)

    companion object {
        const val PASSWORD_THRESHOLD = 5
        const val PASSWORD_WINDOW_MINUTES = 10
        const val TOTP_THRESHOLD = 5
        const val TOTP_WINDOW_MINUTES = 10
        const val LOCK_DURATION_MINUTES = 30
    }

    private val passwordFailures = ConcurrentHashMap<String, MutableList<Instant>>()
    private val totpFailures = ConcurrentHashMap<String, MutableList<Instant>>()

    fun handleInvalidPassword(event: InvalidPasswordAtAuthenticationEvent) {
        val key = buildFailureKey(event.email, event.ipAddress)
        val failures = passwordFailures.getOrPut(key) { mutableListOf() }

        synchronized(failures) {
            val cutoff = Instant.now().minus(Duration.ofMinutes(PASSWORD_WINDOW_MINUTES.toLong()))
            failures.removeAll { it.isBefore(cutoff) }
            failures.add(Instant.now())

            if (failures.size >= PASSWORD_THRESHOLD) {
                log.warn("Brute force detected: email=${event.email} ip=${event.ipAddress} failures=${failures.size}")

                val securityEventId = UUID.randomUUID()
                val securityEvent = PasswordBruteForceAttemptedSecurityEvent(
                    principalId = clientId,
                    action = "${IdActions.SERVICE}:BruteForceDetected",
                    resource = IdResources.platform().toString(),
                    timestamp = Instant.now(),
                    eventId = securityEventId,
                    accountId = event.accountId,
                    email = event.email,
                    ipAddress = event.ipAddress ?: "unknown",
                    failureCount = failures.size,
                    windowMinutes = PASSWORD_WINDOW_MINUTES,
                )
                eventDispatcher.dispatch(securityEvent)

                if (event.accountId != null) {
                    enforcer.placeAccountLock(
                        event.accountId.toString(), securityEventId, LOCK_DURATION_MINUTES
                    )
                }
                event.ipAddress?.let { ip ->
                    enforcer.placeIpBlock(ip, securityEventId, LOCK_DURATION_MINUTES)
                }

                failures.clear()
            }
        }
    }

    fun handleInvalidTOTP(event: InvalidTOTPAtAuthenticationEvent) {
        val key = buildFailureKey(event.accountId.toString(), event.ipAddress)
        val failures = totpFailures.getOrPut(key) { mutableListOf() }

        synchronized(failures) {
            val cutoff = Instant.now().minus(Duration.ofMinutes(TOTP_WINDOW_MINUTES.toLong()))
            failures.removeAll { it.isBefore(cutoff) }
            failures.add(Instant.now())

            if (failures.size >= TOTP_THRESHOLD) {
                log.warn("TOTP brute force detected: account=${event.accountId} ip=${event.ipAddress} failures=${failures.size}")

                val securityEventId = UUID.randomUUID()
                val securityEvent = PasswordBruteForceAttemptedSecurityEvent(
                    principalId = clientId,
                    action = "${IdActions.SERVICE}:TOTPBruteForceDetected",
                    resource = IdResources.platform().toString(),
                    timestamp = Instant.now(),
                    eventId = securityEventId,
                    accountId = event.accountId,
                    email = null,
                    ipAddress = event.ipAddress ?: "unknown",
                    failureCount = failures.size,
                    windowMinutes = TOTP_WINDOW_MINUTES,
                )
                eventDispatcher.dispatch(securityEvent)

                enforcer.placeAccountLock(
                    event.accountId.toString(), securityEventId, LOCK_DURATION_MINUTES
                )
                failures.clear()
            }
        }
    }

    /** Reset counters after a successful login for this email/ip. */
    fun handleAuthSucceeded(email: String, ip: String, accountId: UUID) {
        passwordFailures.remove(buildFailureKey(email, ip))
        totpFailures.remove(buildFailureKey(accountId.toString(), ip))
    }

    /** Clear counters tied to an account (password change, email change). */
    fun clearForAccount(accountId: UUID) {
        val prefix = "$accountId:"
        totpFailures.keys.removeIf { it.startsWith(prefix) }
        passwordFailures.keys.removeIf { it.startsWith(prefix) }
    }

    private fun buildFailureKey(email: String?, ip: String?): String {
        return "${email ?: "unknown"}:${ip ?: "unknown"}"
    }
}
