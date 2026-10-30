package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.tenant.IHashedMembershipInvitation
import java.util.*

interface IMembershipInvitationRepository : IIdentifiedRepository<IHashedMembershipInvitation> {
    fun findByEmail(email: String): List<IHashedMembershipInvitation>
    fun findByToken(token: String): IHashedMembershipInvitation?
    fun findPendingByEmailAndTenant(email: String, tenantId: UUID): List<IHashedMembershipInvitation>
}
