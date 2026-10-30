package app.infrastructure.services.accounts

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.jasminesoftwaresolutions.id.domain.services.accounts.IJWTService
import com.jasminesoftwaresolutions.id.domain.services.accounts.JWTDecodeException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

class AsymmetricJWTService(
    private val keyAlgorithm: String,
    private val publicKeyData: ByteArray,
    private val privateKeyData: ByteArray,
    val issueAs: String
) : IJWTService {
    companion object {
        const val RSA_KEY_ALGORITHM = "RSA"
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, JsonSerializer<Instant> { src, _, _ -> JsonPrimitive(src.epochSecond) })
        .create()
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

    override fun jwks(): JsonObject {
        val rsaKey = publicKey as RSAPublicKey
        val kid = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.encoded))

        val key = JsonObject().apply {
            addProperty("kty", "RSA")
            addProperty("use", "sig")
            addProperty("alg", "RS256")
            addProperty("kid", kid)
            addProperty("n", Base64.getUrlEncoder().withoutPadding().encodeToString(rsaKey.modulus.toByteArray()))
            addProperty("e", Base64.getUrlEncoder().withoutPadding().encodeToString(rsaKey.publicExponent.toByteArray()))
        }

        val keys = JsonArray().apply { add(key) }
        return JsonObject().apply { add("keys", keys) }
    }

    override fun decode(jwt: String): JsonObject {
        try {
            val parser = Jwts.parser()
                .verifyWith(publicKey)
                .build()

            val jws = parser.parseSignedClaims(jwt)

            val issuedAt = jws.payload.issuedAt.toInstant()
            val expiresAt = jws.payload.expiration.toInstant()
            val notBefore = jws.payload.notBefore.toInstant()

            if (Instant.now() > expiresAt
                || Instant.now() < notBefore
                || issuedAt > Instant.now())
                throw JWTDecodeException("JWT token is not valid at this time")

            if (jws.payload.issuer != issueAs)
                throw JWTDecodeException("JWT token issuer is not valid")

            return gson.toJsonTree(jws.payload).asJsonObject
        } catch (ex: JwtException) {
            throw JWTDecodeException("Invalid JWT token: " + ex.message)
        }
    }
}