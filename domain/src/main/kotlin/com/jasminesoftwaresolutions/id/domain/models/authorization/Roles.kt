package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant

interface IRole {
    var id: String
    var name: String
    var description: String

    var privileges: List<IPrivilege>
}

interface IPlatformRole : IRole

object PlatformAdministrator : IPlatformRole {
    override var id: String = "platform_administrator"
    override var name: String = "Platform Administrator"
    override var description: String = "Full access to all resources"
    override var privileges: List<IPrivilege> = listOf(
        *PlatformPrivilege.all()
    )
}

object PlatformTrustedClient : IPlatformRole {
    override var id: String = "platform_trusted_client"
    override var name: String = "Platform Trusted Client"
    override var description: String = "Full access for applications"
    override var privileges: List<IPrivilege> = listOf(
        *PlatformPrivilege.all()
    )
}

object PlatformClient : IPlatformRole {
    override var id: String = "platform_client"
    override var name: String = "Platform Client"
    override var description: String = "Limited access for applications"
    override var privileges: List<IPrivilege> = listOf(
        ClientScopesWritePrivilege
    )
}

object PlatformMember : IPlatformRole {
    override var id: String = "platform_member"
    override var name: String = "Platform Member"
    override var description: String = "Limited access"
    override var privileges: List<IPrivilege> = emptyList()
}

interface ITenantRole : IRole {
    var tenant: ITenant
}

class TenantAdministrator(override var tenant: ITenant) : ITenantRole {
    companion object {
        val id = "administrator"
    }

    override var id: String = Companion.id
    override var name: String = "Administrator"
    override var description: String = "Full access to organization resources"
    override var privileges: List<IPrivilege> = listOf(
        *TenantPrivilege.all(tenant)
    )
}

class TenantMember(override var tenant: ITenant) : ITenantRole {
    companion object {
        val id = "member"
    }

    override var id: String = Companion.id
    override var name: String = "Member"
    override var description: String = "Limited access to organization resources"
    override var privileges: List<IPrivilege> = listOf(
        TenantReadPrivilege(tenant),
        TenantMembersListPrivilege(tenant),
        TenantMembersReadPrivilege(tenant)
    )
}
