package app.domain.services.accounts

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Instant

class JWTDecodeException(message: String) : Exception(message)

interface IJWTService {
    fun encode(payload: JsonObject): String
    fun decode(token: String): IJWT
}

interface IJWT {
    fun payload(): JsonObject

    fun validAt(instant: Instant): Boolean {
        val now = Instant.now()
        return now < expiresAt() && now > notBefore() && now > issuedAt()
    }

    fun audience(): String? = payload().get("aud").asString
    fun issuer(): String? = payload().get("iss").asString
    fun subject(): String? = payload().get("sub").asString
    fun issuedAt(): Instant? = payload().get("iat")?.asLong?.let { Instant.ofEpochSecond(it) }
    fun expiresAt(): Instant? = payload().get("exp")?.asLong?.let { Instant.ofEpochSecond(it) }
    fun notBefore(): Instant? = payload().get("nbf")?.asLong?.let { Instant.ofEpochSecond(it) }
    fun id(): String? = payload().get("jti").asString

    fun claim(name: String): JsonElement? = payload().get(name)
}