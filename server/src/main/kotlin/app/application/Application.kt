package app.application

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jasminesoftwaresolutions.id.domain.IDServer
import com.jasminesoftwaresolutions.id.domain.events.AccountCreatedEvent
import com.jasminesoftwaresolutions.id.domain.events.AccountPasswordSetEvent
import com.jasminesoftwaresolutions.id.domain.events.PolicyAssignedEvent
import com.jasminesoftwaresolutions.id.domain.events.PolicyCreatedEvent
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPolicy
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

fun main() {
    val server = IDServerImpl()
    server.start(Env.PORT)

    server.registerServiceAndActions()
    installInitialAdministratorAccount(server)
}

private fun createAllowAllOnEverythingPolicy(server: IDServer): IPolicy {
    val policyRepository = server.policyRepository
    val policy = policyRepository.create {
        this.name = "AllowAll"
        this.description = "Allow All on Everything"
        this.tenant = null
        this.document = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("effect", "ALLOW")

                add("actions", JsonArray().apply {
                    add("*")
                })

                add("resources", JsonArray().apply {
                    add("*://")
                    add("*://**")
                })
            })
        }
    }

    val eventDispatcher = server.eventDispatcher
    eventDispatcher.dispatch(PolicyCreatedEvent(
        principalId = UUID(0, 0),
        action = IdActions.CREATE_POLICY,
        resource = IdResources.policy(policy.id).toString(),
        timestamp = Instant.now(),
        policyId = policy.id,
        policyName = policy.name,
        description = policy.description,
        tenantId = policy.tenant?.id,
        document = policy.document
    ))

    return policy
}

private fun installInitialAdministratorAccount(server: IDServer) {
    if (Env.INITIAL_ADMIN_EMAIL.isNullOrBlank() || Env.INITIAL_ADMIN_PASSWORD.isNullOrBlank()) {
        Logger.getAnonymousLogger().warning("Skipping initial administrator account setup -- No defaults provided")
        return
    }

    val accountRepository = server.accountRepository
    if (accountRepository.entries().size > 0) {
        Logger.getAnonymousLogger().warning("Skipping initial administrator account setup -- An account already exists")
        return
    }

    val policy = createAllowAllOnEverythingPolicy(server)
    val account = accountRepository.create {
        this.email = Env.INITIAL_ADMIN_EMAIL
        this.emailVerified = true

        this.firstName = "Administrator"
        this.lastName = " "
    }

    val passwordRepository = server.passwordRepository
    val password = passwordRepository.create {
        this.account = account
        this.password = Env.INITIAL_ADMIN_PASSWORD
    }

    val policyAssignmentRepository = server.policyAssignmentRepository
    val assignment = policyAssignmentRepository.create {
        this.account = account
        this.policy = policy
    }

    val eventDispatcher = server.eventDispatcher

    eventDispatcher.dispatch(AccountCreatedEvent(
        principalId = UUID(0, 0),
        action = IdActions.CREATE_ACCOUNT,
        resource = IdResources.account(account.id).toString(),
        timestamp = Instant.now(),
        accountId = account.id,
        email = account.email,
        emailVerified = account.emailVerified,
        firstName = account.firstName,
        lastName = account.lastName,
        ipAddress = null
    ))

    eventDispatcher.dispatch(AccountPasswordSetEvent(
        principalId = UUID(0, 0),
        action = IdActions.SET_ACCOUNT_PASSWORD,
        resource = IdResources.account(account.id).toString(),
        timestamp = Instant.now(),
        accountId = account.id,
        overwroteExisting = false
    ))

    eventDispatcher.dispatch(PolicyAssignedEvent(
        principalId = UUID(0, 0),
        action = IdActions.ASSIGN_POLICY,
        resource = IdResources.policy(policy.id).toString(),
        timestamp = Instant.now(),
        assignmentId = assignment.id,
        policyId = policy.id,
        policyName = policy.name,
        accountId = account.id,
        accountEmail = account.email,
        clientId = null,
        clientName = null,
        roleId = null,
        roleName = null,
        expiresAt = null
    ))

    Logger.getAnonymousLogger().warning("Created an initial administrator account -- " + account.email)
}