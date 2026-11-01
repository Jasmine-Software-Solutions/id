package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.google.gson.JsonArray
import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.time.Instant

/**
 * A policy containing an opaque JSON document with an ordered list of statements.
 * The document is not evaluated by ID; it is streamed to Witchwood for evaluation.
 *
 * Policies are scoped to a platform (when [tenant] is `null`) or to a tenant,
 * but can be assigned to any account, client, or role regardless of scope.
 *
 * Expected JSON document structure (example):
 * [
 *   {
 *     "effect": "ALLOW",
 *     "actions": ["read", "write"],
 *     "resources": ["*"],
 *     "condition": { ... jsonlogic ... }
 *   },
 *   ...
 * ]
 */
interface IPolicy : IIdentified, ICreated {
    var name: String
    var description: String?

    /** The scope this policy belongs to. `null` means platform scope. */
    var tenant: ITenant?
    var document: JsonArray
}

/**
 * An assignment of a policy to an account, a client, or a role.
 * Exactly one of [account], [client], or [role] must be non-null.
 * There are no scope restrictions: a tenant-scoped policy can be assigned
 * to a superuser in a different tenant.
 */
interface IPolicyAssignment : IIdentified, ICreated {
    var expiresAt: Instant?

    var account: IAccount?
    var client: IClient?
    var role: IRole?

    var policy: IPolicy
}
