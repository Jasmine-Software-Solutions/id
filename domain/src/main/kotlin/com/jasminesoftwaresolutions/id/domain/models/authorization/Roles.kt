package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant

interface IRole {
    var id: String
    var description: String

    var privileges: List<IPrivilege>
}

interface IPlatformRole : IRole

object PlatformAdministrator : IPlatformRole {
    override var id: String = "platform_administrator"
    override var description: String = "Platform Administrator"
    override var privileges: List<IPrivilege> = listOf(
        *PlatformPrivilege.all()
    )
}

object PlatformClient : IPlatformRole {
    override var id: String = "platform_client"
    override var description: String = "Platform Client"
    override var privileges: List<IPrivilege> = listOf(
        ClientScopesWritePrivilege
    )
}

object PlatformTrustedClient : IPlatformRole {
    override var id: String = "platform_trusted_client"
    override var description: String = "Platform Trusted Client"
    override var privileges: List<IPrivilege> = listOf(
        *PlatformPrivilege.all()
    )
}

object PlatformMember : IPlatformRole {
    override var id: String = "platform_member"
    override var description: String = "Platform Member"
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
    override var description: String = "Administrator"
    override var privileges: List<IPrivilege> = listOf(
        *TenantPrivilege.all(tenant)
    )
}

class TenantMember(override var tenant: ITenant) : ITenantRole {
    companion object {
        val id = "member"
    }

    override var id: String = Companion.id
    override var description: String = "Member"
    override var privileges: List<IPrivilege> = listOf(
        TenantReadPrivilege(tenant),
        TenantMembersListPrivilege(tenant),
        TenantMembersReadPrivilege(tenant)
    )
}
