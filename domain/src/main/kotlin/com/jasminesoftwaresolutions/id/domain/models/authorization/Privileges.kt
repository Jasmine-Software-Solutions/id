package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant

interface IPrivilege {
    var id: String
    var description: String
}

sealed class PlatformPrivilege(override var id: String, override var description: String) : IPrivilege {
    companion object {
        fun all() = arrayOf(
            OAuth2IntrospectPrivilege,
            AccountsListPrivilege, AccountsReadPrivilege, AccountsWritePrivilege,
            TenantsListPrivilege, TenantsReadPrivilege, TenantsWritePrivilege,
            MembersListPrivilege, MembersReadPrivilege, MembersWritePrivilege
        )
    }
}

object OAuth2IntrospectPrivilege : PlatformPrivilege("introspect", "Introspect authentication tokens")

object AccountsListPrivilege : PlatformPrivilege("account:list", "List all accounts")
object AccountsReadPrivilege : PlatformPrivilege("accounts:read", "Read account information")
object AccountsWritePrivilege : PlatformPrivilege("accounts:write", "Update accounts")

object TenantsListPrivilege : PlatformPrivilege("tenants:list", "List all tenants")
object TenantsReadPrivilege : PlatformPrivilege("tenants:read", "Read tenant information")
object TenantsWritePrivilege : PlatformPrivilege("tenants:write", "Update tenants")

object MembersListPrivilege : PlatformPrivilege("members:list", "List all members of a tenant")
object MembersReadPrivilege : PlatformPrivilege("members:read", "Read member information")
object MembersWritePrivilege : PlatformPrivilege("members:write", "Update members")

interface ITenantPrivilege : IPrivilege {
    var tenant: ITenant
}

sealed class TenantPrivilege(override var id: String, override var description: String) : ITenantPrivilege {
    companion object {
        fun all(tenant: ITenant) = arrayOf(
            TenantMembersListPrivilege(tenant), TenantMembersReadPrivilege(tenant), TenantMembersWritePrivilege(tenant)
        )
    }
}

class TenantReadPrivilege(override var tenant: ITenant) : TenantPrivilege("tenant:read", "Read tenant information")
class TenantWritePrivilege(override var tenant: ITenant) : TenantPrivilege("tenant:write", "Update tenant information")

class TenantMembersListPrivilege(override var tenant: ITenant) : TenantPrivilege("members:list", "List members")
class TenantMembersReadPrivilege(override var tenant: ITenant) : TenantPrivilege("members:read", "Read member information")
class TenantMembersWritePrivilege(override var tenant: ITenant) : TenantPrivilege("members:write", "Update members")

class TenantRolesAssignPrivilege(override var tenant: ITenant) : TenantPrivilege("roles:assign", "Assign roles to members")