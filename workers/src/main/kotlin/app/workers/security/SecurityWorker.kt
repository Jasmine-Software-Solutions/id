package app.workers.security

import com.jasminesoftwaresolutions.id.domain.events.*
import app.workers.security.detectors.BruteForceDetector
import app.workers.security.detectors.CredentialStuffingDetector
import app.workers.security.detectors.EnumerationDetector
import app.workers.security.detectors.ImpossibleTravelDetector
import app.workers.security.detectors.RapidCreationDetector
import app.workers.security.detectors.TotpDisableDetector
import org.slf4j.LoggerFactory

/**
 * Security worker that coordinates threat detectors to analyze domain events
 * and enforce restrictions.
 *
 * This class is a thin orchestrator: it holds references to individual
 * detectors/trackers and delegates each incoming event to the appropriate
 * component. All detection logic, state, and thresholds live in the
 * individual detector classes.
 */
class SecurityWorker(
    private val bruteForce: BruteForceDetector,
    private val enumeration: EnumerationDetector,
    private val rapidCreation: RapidCreationDetector,
    private val impossibleTravel: ImpossibleTravelDetector,
    private val totpDisable: TotpDisableDetector,
    private val sessionTracker: SessionTracker,
    private val credentialStuffing: CredentialStuffingDetector,
) {
    private val log = LoggerFactory.getLogger(SecurityWorker::class.java)

    val eventTypes: Map<String, kotlin.reflect.KClass<out Event>> = mapOf(
        ValidPasswordAtAuthenticationEvent::class.simpleName!! to ValidPasswordAtAuthenticationEvent::class,
        InvalidPasswordAtAuthenticationEvent::class.simpleName!! to InvalidPasswordAtAuthenticationEvent::class,
        InvalidTOTPAtAuthenticationEvent::class.simpleName!! to InvalidTOTPAtAuthenticationEvent::class,
        AuthenticationSucceededEvent::class.simpleName!! to AuthenticationSucceededEvent::class,
        AuthenticationFlowStartedEvent::class.simpleName!! to AuthenticationFlowStartedEvent::class,
        SessionCreatedEvent::class.simpleName!! to SessionCreatedEvent::class,
        SessionRevokedEvent::class.simpleName!! to SessionRevokedEvent::class,
        AllSessionsRevokedEvent::class.simpleName!! to AllSessionsRevokedEvent::class,
        AccountCreatedEvent::class.simpleName!! to AccountCreatedEvent::class,
        AccountDeletedEvent::class.simpleName!! to AccountDeletedEvent::class,
        AccountPasswordSetEvent::class.simpleName!! to AccountPasswordSetEvent::class,
        AccountTOTPEnabledEvent::class.simpleName!! to AccountTOTPEnabledEvent::class,
        AccountTOTPDisabledEvent::class.simpleName!! to AccountTOTPDisabledEvent::class,
        AccountEmailChangeVerifiedEvent::class.simpleName!! to AccountEmailChangeVerifiedEvent::class,
    )

    fun handle(event: Event) {
        when (event) {
            is ValidPasswordAtAuthenticationEvent -> credentialStuffing.handleValidPassword(event)
            is InvalidPasswordAtAuthenticationEvent -> bruteForce.handleInvalidPassword(event)
            is InvalidTOTPAtAuthenticationEvent -> bruteForce.handleInvalidTOTP(event)
            is AuthenticationSucceededEvent -> {
                bruteForce.handleAuthSucceeded(event.email, event.ipAddress ?: "unknown", event.accountId)
                impossibleTravel.handleAuthSucceeded(event)
            }

            is AuthenticationFlowStartedEvent -> enumeration.handleFlowStarted(event)
            is SessionCreatedEvent -> sessionTracker.handleSessionCreated(event)
            is SessionRevokedEvent -> sessionTracker.handleSessionRevoked(event)
            is AllSessionsRevokedEvent -> sessionTracker.handleAllSessionsRevoked(event)
            is AccountCreatedEvent -> rapidCreation.handleAccountCreated(event)
            is AccountDeletedEvent -> {
                impossibleTravel.removeLoginRecord(event.accountId)
                sessionTracker.removeAccount(event.accountId)
                totpDisable.removeRecord(event.accountId)
            }

            is AccountPasswordSetEvent -> bruteForce.clearForAccount(event.accountId)
            is AccountTOTPEnabledEvent -> totpDisable.handleTOTPEnabled(event)
            is AccountTOTPDisabledEvent -> totpDisable.handleTOTPDisabled(event)
            is AccountEmailChangeVerifiedEvent -> bruteForce.clearForAccount(event.accountId)
            else -> log.debug("SecurityWorker ignoring event: ${event.eventType}")
        }
    }
}
