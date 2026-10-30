package com.jasminesoftwaresolutions.id.domain.models.account

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified

interface IAccount : IIdentified, ICreated {
    var email: String
    var firstName: String
    var lastName: String
    var emailVerified: Boolean

    /** Ordered list of client IDs that the account has favorited on the SSO page. */
    var ssoFavoriteClientIds: List<String>

    /** Preferred display order of client IDs on the SSO page. */
    var ssoClientOrder: List<String>
}
