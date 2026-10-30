package app.workers.witchwood

import com.google.gson.JsonArray
import com.jasminesoftwaresolutions.witchwood.client.WitchwoodClient
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Syncs Witchwood policies in response to domain events.
 *
 * Handles policy CRUD (upsert via update-then-create) and assignment
 * to both roles and principals.
 */
class PolicySyncer(
    private val client: WitchwoodClient,
    private val compiler: PolicyCompiler,
) {
    private val log = LoggerFactory.getLogger(PolicySyncer::class.java)

    fun upsert(policyId: UUID, document: JsonArray) {
        val statements = compiler.compile(document)
        log.info("Upserting policy {} with {} statements", policyId, statements.size)
        try {
            client.updatePolicy(policyId, statements).get()
            log.info("Updated policy {}", policyId)
        } catch (e: Exception) {
            log.warn("updatePolicy failed for {}: {}, trying createPolicy", policyId, e.message)
            try {
                client.createPolicy(policyId, statements).get()
                log.info("Created policy {}", policyId)
            } catch (e2: Exception) {
                log.error("createPolicy failed for {}: {}", policyId, e2.message, e2)
                throw e2
            }
        }
    }

    fun delete(policyId: UUID) {
        client.deletePolicy(policyId).get()
        log.debug("Deleted Witchwood policy {}", policyId)
    }

    fun assignToRole(roleId: UUID, policyId: UUID) {
        client.assignPolicyToRole(roleId, policyId).get()
        log.debug("Assigned policy {} to role {}", policyId, roleId)
    }

    fun unassignFromRole(roleId: UUID, policyId: UUID) {
        client.unassignPolicyFromRole(roleId, policyId).get()
        log.debug("Unassigned policy {} from role {}", policyId, roleId)
    }

    fun assignToPrincipal(principalId: UUID, policyId: UUID) {
        client.assignPolicyToPrincipal(principalId, policyId).get()
        log.debug("Assigned policy {} to principal {}", policyId, principalId)
    }

    fun unassignFromPrincipal(principalId: UUID, policyId: UUID) {
        client.unassignPolicyFromPrincipal(principalId, policyId).get()
        log.debug("Unassigned policy {} from principal {}", policyId, principalId)
    }
}
