package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.client.IClient

interface IScope {
    val client: IClient?

    val id: String
    val description: String

    val isByTenant: Boolean
}

interface IRegisteredScope : IScope {
    override var client: IClient?

    override var id: String
    override var description: String

    override var isByTenant: Boolean
}

abstract class StaticScope(override final val id: String, override final val description: String) : IScope {
    override final var client: IClient? = null
    override var isByTenant: Boolean = false

    companion object {
        fun all() = arrayOf(
            *PlatformScope.all(), *TenantScope.all()
        )
    }
}

sealed class PlatformScope(id: String, description: String) : StaticScope(id, description) {
    companion object {
        fun all() = arrayOf(
            EmailScope, ProfileScope, TenantsScope
        )
    }
}

object EmailScope : PlatformScope("email", "Know your email address")
object ProfileScope : PlatformScope("profile", "Know your first and last name")
object TenantsScope : PlatformScope("tenants", "Know what organizations you are a member of")

sealed class TenantScope(id: String, description: String) : StaticScope("tenant:$id", description) {
    override var isByTenant: Boolean = true

    companion object {
        fun all() = arrayOf(
            TenantMembersScope, TenantMembersWriteScope
        )
    }
}

object TenantMembersScope : TenantScope("members", "Know the members of your organization")
object TenantMembersWriteScope : TenantScope("members:write", "Manage member membership in your organization")
