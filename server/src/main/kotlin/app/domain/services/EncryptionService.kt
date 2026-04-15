package app.domain.services

interface IEncryptionFunction {
    fun encrypt(value: ByteArray): ByteArray
    fun decrypt(value: ByteArray): ByteArray
}