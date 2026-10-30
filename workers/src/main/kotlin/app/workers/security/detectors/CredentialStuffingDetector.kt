package app.workers.security.detectors

import com.jasminesoftwaresolutions.id.domain.events.CredentialStuffingSucceededSecurityEvent
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.ValidPasswordAtAuthenticationEvent
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

/**
 * Detects credential stuffing by querying the haveibeenpwned.com (HIBP) k-anonymity API
 * with the SHA-1 hash prefix of the user's plaintext password.
 *
 * When a valid password is submitted during authentication, this detector:
 * 1. Computes the SHA-1 hex hash of the plaintext password.
 * 2. Sends the first 5 characters to HIBP's k-anon range endpoint.
 * 3. If the full hash suffix is found in the response, the password is compromised.
 * 4. Logs out all sessions, deletes the password, and emits a security event.
 */
class CredentialStuffingDetector(
    private val eventDispatcher: IEventDispatcher,
    private val sessionRevoker: SessionRevoker,
    private val passwordDeleter: PasswordDeleter,
    private val clientId: UUID,
    private val http: HttpClient = HttpClient.newHttpClient(),
) {
    private val log = LoggerFactory.getLogger(CredentialStuffingDetector::class.java)

    companion object {
        private const val HIBP_RANGE_URL = "https://api.pwnedpasswords.com/range"
    }

    /**
     * Handle a valid password event by checking the password hash against HIBP.
     * Must not block the worker thread for too long — HIBP calls are fire-and-forget
     * in a virtual thread or handled quickly within the SQS processing window.
     */
    fun handleValidPassword(event: ValidPasswordAtAuthenticationEvent) {
        val prefix = event.passwordHash.take(5).uppercase()
        val suffix = event.passwordHash.drop(5).uppercase()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$HIBP_RANGE_URL/$prefix"))
            .header("Add-Padding", "true")
            .GET()
            .build()

        try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.warn("HIBP API returned ${response.statusCode()} for prefix=$prefix")
                return
            }

            val body = response.body()
            val matched = body.lines().any { line ->
                val parts = line.split(":", limit = 2)
                parts.size == 2 && parts[0].trim() == suffix
            }

            if (!matched) return

            log.warn(
                "Credential stuffing detected: account=${event.accountId} email=${event.email} " +
                        "ip=${event.ipAddress} hash prefix=$prefix"
            )

            // Emit security event
            val securityEvent = CredentialStuffingSucceededSecurityEvent(
                principalId = clientId,
                action = "${IdActions.SERVICE}:CredentialStuffingDetected",
                resource = IdResources.platform().toString(),
                timestamp = Instant.now(),
                accountId = event.accountId,
                email = event.email,
                ipAddress = event.ipAddress ?: "unknown",
                hashPrefix = prefix,
            )
            eventDispatcher.dispatch(securityEvent)

            // Revoke all sessions for this account
            sessionRevoker.revokeAllSessions(event.accountId, clientId, eventDispatcher)

            // Delete the password — force the user to set a new one
            passwordDeleter.deletePassword(event.accountId)

        } catch (e: Exception) {
            log.error("Failed to check HIBP for account=${event.accountId}: ${e.message}", e)
        }
    }
}

/**
 * Abstracts session revocation so the detector does not depend on repository internals.
 */
interface SessionRevoker {
    fun revokeAllSessions(accountId: UUID, clientId: UUID, eventDispatcher: IEventDispatcher)
}

/**
 * Abstracts password deletion so the detector does not depend on repository internals.
 */
interface PasswordDeleter {
    fun deletePassword(accountId: UUID)
}
