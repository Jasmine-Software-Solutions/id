package app.workers.witchwood

import com.jasminesoftwaresolutions.witchwood.client.WitchwoodClient
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Idempotently ensures Witchwood principals exist for accounts and clients.
 *
 * Uses a get-or-create pattern: tries [WitchwoodClient.getPrincipal] first,
 * falls back to [WitchwoodClient.createPrincipal] if the principal doesn't exist yet.
 */
class PrincipalSyncer(
    private val client: WitchwoodClient,
) {
    private val log = LoggerFactory.getLogger(PrincipalSyncer::class.java)

    fun ensureAccountPrincipal(accountId: UUID) {
        try {
            client.getPrincipal(accountId).get()
        } catch (_: Exception) {
            client.createPrincipal(accountId, emptyList(), emptyList()).get()
        }
        log.debug("Ensured Witchwood principal for account {}", accountId)
    }

    fun ensureClientPrincipal(clientId: UUID) {
        try {
            client.getPrincipal(clientId).get()
        } catch (_: Exception) {
            client.createPrincipal(clientId, emptyList(), emptyList()).get()
        }
        log.debug("Ensured Witchwood principal for client {}", clientId)
    }
}
