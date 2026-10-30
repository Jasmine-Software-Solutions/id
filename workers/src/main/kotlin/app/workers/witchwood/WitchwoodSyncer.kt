package app.workers.witchwood

import com.jasminesoftwaresolutions.id.domain.events.*
import org.slf4j.LoggerFactory

/**
 * Consumes role and policy events from SQS and syncs them to Witchwood
 * using incremental gRPC calls.
 *
 * This class is a thin orchestrator: it routes each event to the
 * appropriate syncer component. All gRPC logic and state lives
 * in [PrincipalSyncer], [RoleSyncer], and [PolicySyncer].
 */
class WitchwoodSyncer(
    private val principals: PrincipalSyncer,
    private val roles: RoleSyncer,
    private val policies: PolicySyncer,
) {
    private val log = LoggerFactory.getLogger(WitchwoodSyncer::class.java)

    val eventTypes: Map<String, kotlin.reflect.KClass<out Event>> = mapOf(
        AccountCreatedEvent::class.simpleName!! to AccountCreatedEvent::class,
        ClientCreatedEvent::class.simpleName!! to ClientCreatedEvent::class,
        RoleCreatedEvent::class.simpleName!! to RoleCreatedEvent::class,
        RoleUpdatedEvent::class.simpleName!! to RoleUpdatedEvent::class,
        RoleDeletedEvent::class.simpleName!! to RoleDeletedEvent::class,
        RoleAssignedEvent::class.simpleName!! to RoleAssignedEvent::class,
        RoleUnassignedEvent::class.simpleName!! to RoleUnassignedEvent::class,
        PolicyCreatedEvent::class.simpleName!! to PolicyCreatedEvent::class,
        PolicyUpdatedEvent::class.simpleName!! to PolicyUpdatedEvent::class,
        PolicyDeletedEvent::class.simpleName!! to PolicyDeletedEvent::class,
        PolicyAssignedEvent::class.simpleName!! to PolicyAssignedEvent::class,
        PolicyUnassignedEvent::class.simpleName!! to PolicyUnassignedEvent::class,
        AuthenticationSucceededEvent::class.simpleName!! to AuthenticationSucceededEvent::class,
    )

    fun handle(event: Event) {
        log.info("Handling {}", event.eventType)
        try {
            when (event) {
                is AccountCreatedEvent -> principals.ensureAccountPrincipal(event.accountId)
                is ClientCreatedEvent -> principals.ensureClientPrincipal(event.clientId)
                is AuthenticationSucceededEvent -> principals.ensureAccountPrincipal(event.accountId)

                is RoleCreatedEvent -> roles.ensureRole(event.roleId)
                is RoleUpdatedEvent -> roles.ensureRole(event.roleId)
                is RoleDeletedEvent -> roles.deleteRole(event.roleId)

                is RoleAssignedEvent -> {
                    roles.principalId(event.accountId, event.clientId)?.let { pid ->
                        roles.assignToPrincipal(pid, event.roleId)
                    }
                }

                is RoleUnassignedEvent -> {
                    roles.principalId(event.accountId, event.clientId)?.let { pid ->
                        roles.unassignFromPrincipal(pid, event.roleId)
                    }
                }

                is PolicyCreatedEvent -> policies.upsert(event.policyId, event.document)
                is PolicyUpdatedEvent -> event.newDocument?.let { policies.upsert(event.policyId, it) }
                is PolicyDeletedEvent -> policies.delete(event.policyId)

                is PolicyAssignedEvent -> {
                    event.roleId?.let { policies.assignToRole(it, event.policyId) }
                    (event.accountId ?: event.clientId)?.let { policies.assignToPrincipal(it, event.policyId) }
                }

                is PolicyUnassignedEvent -> {
                    event.roleId?.let { policies.unassignFromRole(it, event.policyId) }
                    (event.accountId ?: event.clientId)?.let { policies.unassignFromPrincipal(it, event.policyId) }
                }

                else -> log.debug("WitchwoodSyncer ignoring: {}", event.eventType)
            }
        } catch (e: Exception) {
            log.error("Failed to handle {}: {}", event.eventType, e.message, e)
        }
    }
}
