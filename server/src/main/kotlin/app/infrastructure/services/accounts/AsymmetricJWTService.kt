package app.infrastructure.services.accounts

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jasminesoftwaresolutions.id.domain.services.accounts.IJWTService
import com.jasminesoftwaresolutions.id.domain.services.accounts.JWTDecodeException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

class AsymmetricJWTService(
    private val keyAlgorithm: String,
    private val publicKeyData: ByteArray,
    private val privateKeyData: ByteArray,
    val issueAs: String
) : IJWTService {
    companion object {
        const val RSA_KEY_ALGORITHM = "RSA"
    }

    private val gson = Gson()
    private val publicKey: PublicKey
    private val privateKey: PrivateKey

    init {
        val keyFactory = KeyFactory.getInstance(keyAlgorithm)

        val specPublic = X509EncodedKeySpec(publicKeyData)
        publicKey = keyFactory.generatePublic(specPublic)

        val specPrivate = PKCS8EncodedKeySpec(privateKeyData)
        privateKey = keyFactory.generatePrivate(specPrivate)
    }

    override fun encode(payload: JsonObject): String {
        if (!payload.has("iat"))
            payload.addProperty("iat", Instant.now().epochSecond)

        if (!payload.has("iss"))
            payload.addProperty("iss", issueAs)

        val content = gson.toJson(payload)
            .toByteArray()

        val builder = Jwts.builder()
            .content(content)
            .signWith(privateKey)

        return builder.compact()
    }

    override fun decode(jwt: String): JsonObject {
        try {
            val parser = Jwts.parser()
                .verifyWith(publicKey)
                .build()

            val jws = parser.parseSignedContent(jwt)

            val ba = jws.payload
            val str = String(ba)
            val payload = gson.fromJson(str, JsonObject::class.java)

            val issuedAt = payload.getAsJsonPrimitive("iat").asLong
            val expiresAt = payload.getAsJsonPrimitive("exp").asLong
            val notBefore = payload.getAsJsonPrimitive("nbf").asLong

            if (Instant.now() > Instant.ofEpochSecond(expiresAt)
                || Instant.now() < Instant.ofEpochSecond(notBefore)
                || Instant.ofEpochSecond(issuedAt) > Instant.now())
                throw JWTDecodeException("JWT token is not valid at this time")

            if (payload.getAsJsonPrimitive("iss").asString != issueAs)
                throw JWTDecodeException("JWT token issuer is not valid")

            return payload
        } catch (ex: JwtException) {
            throw JWTDecodeException("Invalid JWT token")
        }
    }
}