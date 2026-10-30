package app.workers.witchwood

import com.jasminesoftwaresolutions.witchwood.client.WitchwoodClient
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Syncs Witchwood roles in response to domain events.
 */
class RoleSyncer(
    private val client: WitchwoodClient,
) {
    private val log = LoggerFactory.getLogger(RoleSyncer::class.java)

    fun ensureRole(roleId: UUID) {
        try {
            client.getRole(roleId).get()
        } catch (_: Exception) {
            client.createRole(roleId, emptySet()).get()
        }
        log.debug("Ensured Witchwood role {}", roleId)
    }

    fun deleteRole(roleId: UUID) {
        client.deleteRole(roleId).get()
        log.debug("Deleted Witchwood role {}", roleId)
    }

    fun assignToPrincipal(principalId: UUID, roleId: UUID) {
        client.assignRoleToPrincipal(principalId, roleId).get()
        log.debug("Assigned role {} to principal {}", roleId, principalId)
    }

    fun unassignFromPrincipal(principalId: UUID, roleId: UUID) {
        client.unassignRoleFromPrincipal(principalId, roleId).get()
        log.debug("Unassigned role {} from principal {}", roleId, principalId)
    }

    /** Resolves the principal from an assignment event (account or client). */
    fun principalId(accountId: UUID?, clientId: UUID?): UUID? = accountId ?: clientId
}
