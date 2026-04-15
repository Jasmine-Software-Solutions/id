package app.domain.models.client

import app.domain.models.ICreated
import app.domain.models.IIdentified
import java.net.URI

interface IClient : IIdentified, ICreated {
    var suspended: Boolean

    var name: String
    var confidential: Boolean

    val redirectUris: IClientRedirectUris

    fun verify(secret: String): Boolean
}

interface IHashedClient : IClient {
    var secret: String
}

interface IClientRedirectUris {
    val values: List<URI>

    fun add(uri: URI)
    fun remove(uri: URI)
}