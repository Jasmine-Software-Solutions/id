package app.etc

import app.Env
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptedParameter {
    private const val PREFIX = "\$ciphertext:"

    fun encrypt(value: String): String {
        if (Env.ENCRYPTED_PARAMETER_SECRET.isBlank())
            return value

        val secureRandom = SecureRandom()
        val iv = ByteArray(16)
        secureRandom.nextBytes(iv)
        val ivspec = IvParameterSpec(iv)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(Env.ENCRYPTED_PARAMETER_SECRET.toCharArray(), Env.ENCRYPTED_PARAMETER_SALT.toByteArray(), 1, 256)
        val tmp = factory.generateSecret(spec)
        val secretKeySpec = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivspec)

        val cipherText = cipher.doFinal(value.toByteArray())
        val encryptedData = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, encryptedData, 0, iv.size)
        System.arraycopy(cipherText, 0, encryptedData, iv.size, cipherText.size)

        return PREFIX + Base64.getEncoder().encodeToString(encryptedData)
    }

    fun decrypt(value: String): String {
        if (!value.startsWith(PREFIX))
            return value

        val encryptedData = Base64.getDecoder().decode(value.substring(PREFIX.length))
        val iv = encryptedData.copyOfRange(0, 16)
        val cipherText = encryptedData.copyOfRange(16, encryptedData.size)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(Env.ENCRYPTED_PARAMETER_SECRET.toCharArray(), Env.ENCRYPTED_PARAMETER_SALT.toByteArray(), 1, 256)
        val tmp = factory.generateSecret(spec)
        val secretKeySpec = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))

        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }
}