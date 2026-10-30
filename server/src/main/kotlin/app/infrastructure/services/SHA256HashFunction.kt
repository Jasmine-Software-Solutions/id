package app.infrastructure.services

import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import java.security.MessageDigest

class SHA256HashFunction : IHashFunction {
    override fun hash(value: ByteArray): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(value)

        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Compared in constant time: `==` on the hex encodings stops at the first
     * differing character, which leaks how much of a guessed secret matched.
     * [MessageDigest.isEqual] walks both arrays in full no matter where they
     * diverge.
     */
    override fun verify(value: ByteArray, hash: String)
        = MessageDigest.isEqual(hash(value).toByteArray(), hash.toByteArray())
}