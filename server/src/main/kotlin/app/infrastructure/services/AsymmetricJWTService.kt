package app.infrastructure.services

import app.domain.services.IJWT
import app.domain.services.IJWTService
import app.domain.services.JWTDecodeException
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.jsonwebtoken.Jws
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

class AsymmetricJWTService(
    val authority: String,
    private val keyAlgorithm: String,
    private val publicKeyData: ByteArray,
    private val privateKeyData: ByteArray
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
        val content = gson.toJson(payload)
            .toByteArray()

        val builder = Jwts.builder()
            .content(content)
            .signWith(privateKey)

        return builder.compact()
    }

    override fun decode(token: String): IJWT {
        try {
            val parser = Jwts.parser()
                .verifyWith(publicKey)
                .build()

            val jws = parser.parseSignedContent(token)
            val jwt = JWT(jws)

            if (!jwt.validAt(Instant.now()))
                throw JWTDecodeException("JWT token is not valid at this time")

            return jwt
        } catch (ex: JwtException) {
            throw JWTDecodeException("Invalid JWT token")
        }
    }

    class JWT(private val jws: Jws<ByteArray>) : IJWT {
        companion object {
            private val gson = Gson()
        }

        override fun payload(): JsonObject {
            val ba = jws.payload
            val str = String(ba)
            val jo = gson.fromJson(str, JsonObject::class.java)
            return jo
        }
    }
}