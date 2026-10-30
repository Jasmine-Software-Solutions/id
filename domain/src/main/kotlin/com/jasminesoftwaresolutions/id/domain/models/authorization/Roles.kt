package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.*
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.time.Instant
import java.util.*

interface IRole: IIdentified, ICreated {
    var name: String
    var description: String?

    /** The scope this role belongs to. `null` means platform scope. */
    var tenant: ITenant?
}

interface IRoleAssignment : IIdentified, ICreated {
    var expiresAt: Instant?

    var account: IAccount?
    var client: IClient?
    var role: IRole
}

open class Role(
    override var tenant: ITenant?,
    override var id: UUID,
    override var name: String,
    override var description: String?,
    override val createdAt: Instant
) : IRole
