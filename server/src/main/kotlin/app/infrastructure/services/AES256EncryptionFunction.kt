package app.infrastructure.services

import app.domain.services.IEncryptionFunction
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

open class AES256EncryptionFunction(
    private val key: CharArray,
    private val salt: ByteArray,
    private val iterations: Int = 1,
    private val keyLength: Int = 256
) : IEncryptionFunction {
    companion object {
        const val FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val KEY_ALGORITHM = "AES"
        const val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"
    }

    private fun initCipher(mode: Int, ivspec: IvParameterSpec): Cipher {
        val factory = SecretKeyFactory.getInstance(FACTORY_ALGORITHM)
        val spec: KeySpec = PBEKeySpec(key, salt, iterations, keyLength)
        val tmp = factory.generateSecret(spec)
        val secretKeySpec = SecretKeySpec(tmp.encoded, KEY_ALGORITHM)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(mode, secretKeySpec, ivspec)
        return cipher
    }

    override fun encrypt(value: ByteArray): ByteArray {
        val secureRandom = SecureRandom()
        val iv = ByteArray(16)
        secureRandom.nextBytes(iv)

        val ivspec = IvParameterSpec(iv)
        val cipher = initCipher(Cipher.ENCRYPT_MODE, ivspec)

        val cipherText = cipher.doFinal(value)
        val encryptedData = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, encryptedData, 0, iv.size)
        System.arraycopy(cipherText, 0, encryptedData, iv.size, cipherText.size)

        return encryptedData
    }

    override fun decrypt(value: ByteArray): ByteArray {
        val iv = value.copyOfRange(0, 16)
        val cipherText = value.copyOfRange(16, value.size)

        val cipher = initCipher(Cipher.DECRYPT_MODE, IvParameterSpec(iv))
        return cipher.doFinal(cipherText)
    }
}