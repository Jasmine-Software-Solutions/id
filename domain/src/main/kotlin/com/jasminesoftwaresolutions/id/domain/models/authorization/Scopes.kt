package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.client.IClient

interface IScope {
    val client: IClient?

    val id: String
    val description: String
}

interface IRegisteredScope : IScope {
    override var client: IClient?

    override var id: String
    override var description: String
}

abstract class StaticScope(override final val id: String, override final val description: String) : IScope {
    override final var client: IClient? = null

    companion object {
        fun all() = arrayOf(
            OpenIdScope, EmailScope, ProfileScope, TenantsScope, PermissionsScope,
            AccountsListScope, AccountsReadScope, AccountsWriteScope,
            TenantMembersScope, TenantMembersWriteScope,
        )
    }
}

object OpenIdScope : StaticScope("openid", "Authenticate with OpenID Connect")
object EmailScope : StaticScope("email", "Know your email address")
object ProfileScope : StaticScope("profile", "Know your first and last name")
object TenantsScope : StaticScope("tenants", "Know what tenants you are a member of")
object PermissionsScope : StaticScope("permissions", "Know what roles and groups you are a member of")

object AccountsListScope : StaticScope("account:list", "List all accounts")
object AccountsReadScope : StaticScope("accounts:read", "Read account information")
object AccountsWriteScope : StaticScope("accounts:write", "Update accounts")

object TenantMembersScope : StaticScope("tenant:members", "Know the members of your tenant")
object TenantMembersWriteScope : StaticScope("tenant:members:write", "Manage member membership in your tenant")
