package app.domain.repositories

import app.domain.models.tenant.ITenant
import app.domain.models.tenant.ITenantMembership

interface ITenantRepository : IIdentifiedRepository<ITenant>
interface ITenantMembershipRepository : IIdentifiedRepository<ITenantMembership>, IRepositoryRelatedToAccount<List<ITenantMembership>>