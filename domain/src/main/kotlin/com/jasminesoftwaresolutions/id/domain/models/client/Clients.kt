package com.jasminesoftwaresolutions.id.domain.models.client

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import java.net.URI

interface IClient : IIdentified, ICreated {
    var suspended: Boolean

    var fqdn: String
    var confidential: Boolean

    /** Human-readable display name for SSO tiles. Falls back to [fqdn] when empty. */
    var name: String?

    /** Optional logo URI for SSO tile branding. */
    var logoUri: URI?

    /** Optional OIDC initiate-login URI. Presence determines SSO eligibility. */
    var initiateLoginUri: URI?

    val redirectUris: IClientRedirectUris

    var secret: String

    fun verify(secret: String): Boolean
}

interface IHashedClient : IClient

interface IClientRedirectUris {
    val values: List<URI>

    fun add(uri: URI)
    fun remove(uri: URI)
}
