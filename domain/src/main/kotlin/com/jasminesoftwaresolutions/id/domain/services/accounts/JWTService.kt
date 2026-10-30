package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.google.gson.JsonObject

class JWTDecodeException(message: String) : Exception(message)

interface IJWTService {
    fun encode(payload: JsonObject): String
    fun decode(jwt: String): JsonObject

    /** Returns the JWKS (JSON Web Key Set) document so that verifiers can discover the public key. */
    fun jwks(): JsonObject
}