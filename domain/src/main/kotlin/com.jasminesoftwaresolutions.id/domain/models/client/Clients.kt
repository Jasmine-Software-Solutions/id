package com.jasminesoftwaresolutions.id.domain.models.client

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import java.net.URI

interface IClient : IIdentified,
    ICreated {
    var suspended: Boolean

    var name: String
    var confidential: Boolean

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