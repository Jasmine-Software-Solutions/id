package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.client.IClient

interface IScope {
    val client: IClient?

    val id: String
    val description: String
}

abstract class StaticScope(override final val id: String, override final val description: String) : IScope {
    override final var client: IClient? = null

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
    companion object {
        fun all() = arrayOf(
            TenantMembersScope, TenantMembersWriteScope
        )
    }
}

object TenantMembersScope : TenantScope("members", "Know the members of your organization")
object TenantMembersWriteScope : TenantScope("members:write", "Manage member membership in your organization")