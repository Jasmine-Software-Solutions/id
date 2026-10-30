package app.infrastructure.services

import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.witchwood.client.WitchwoodClient
import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.CompiledResourcePath
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Witchwood-backed implementation of [IAuthorizationService].
 *
 * Delegates policy evaluation to the Witchwood engine via gRPC.
 * The [client] is exposed so bootstrapping and principal management can share
 * the same connection.
 */
class WitchwoodAuthorizationService(
    val client: WitchwoodClient,
) : IAuthorizationService, AutoCloseable by client {

    @WithSpan
    override fun evaluate(
        principalId: UUID,
        action: String,
        resource: CompiledResourcePath,
        context: Map<String, String>,
    ): CompletableFuture<Boolean> {
        return client.evaluate(principalId, action, resource, context)
    }

    @WithSpan
    override fun queryResources(
        principalId: UUID,
        action: String,
    ): CompletableFuture<Set<CompiledResourcePath>> {
        return client.queryResources(principalId, action)
    }
}
