package app.infrastructure.services

import com.jasminesoftwaresolutions.id.domain.services.ISigningFunction
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HmacSHA256SigningFunction(
    private val key: ByteArray
) : ISigningFunction {
    override fun sign(value: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)

        val rawHmac = mac.doFinal(value)
        return rawHmac
    }
}