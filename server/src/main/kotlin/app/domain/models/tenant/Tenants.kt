package app.domain.models.tenant

import app.domain.models.ICreated
import app.domain.models.IIdentified

interface ITenant : IIdentified, ICreated {
    var suspended: Boolean
    var name: String
}