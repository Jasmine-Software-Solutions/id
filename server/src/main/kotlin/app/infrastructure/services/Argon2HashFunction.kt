package app.infrastructure.services

import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import de.mkammerer.argon2.Argon2Factory

class Argon2HashFunction(
    private val hashLength: Int,
    private val saltLength: Int,
    private val iterations: Int,
    private val memory: Int,
    private val parallelism: Int
) : IHashFunction {
    private val argon2 = Argon2Factory.create(
        saltLength,
        hashLength
    )

    override fun hash(value: ByteArray): String {
        val hash = try {
            argon2.hash(
                iterations,
                memory,
                parallelism,
                value
            )
        } finally {
            argon2.wipeArray(value)
        }

        return hash
    }

    override fun verify(value: ByteArray, hash: String): Boolean {
        try {
            return argon2.verify(hash, value)
        } finally {
            argon2.wipeArray(value)
        }
    }
}