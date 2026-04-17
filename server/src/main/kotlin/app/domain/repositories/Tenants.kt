package app.domain.repositories

import app.domain.models.tenant.ITenant
import app.domain.models.tenant.ITenantMembership

interface ITenantRepository<T : ITenant> : IIdentifiedRepository<T>
interface ITenantMembershipRepository<T : ITenantMembership> : IIdentifiedRepository<T>, IRepositoryRelatedToAccount<List<T>>