package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.google.gson.JsonObject

class JWTDecodeException(message: String) : Exception(message)

interface IJWTService {
    fun encode(payload: JsonObject): String
    fun decode(jwt: String): JsonObject
}