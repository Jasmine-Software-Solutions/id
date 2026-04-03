package app.domain.services

interface IHashedSecretService<T> {
    fun verify(provided: T, known: T): Boolean
}

interface IPasswordService : IHashedSecretService<String> {
    fun hash(password: String): String
}

interface IMagicLinkService : IHashedSecretService<String> {
    fun hash(token: String): String
}

interface ITOTPService {
    fun generate(secret: ByteArray, period: Long): Int
    fun verify(secret: ByteArray, code: Int, period: Long): Boolean
    fun verify(secret: ByteArray, code: Int): Boolean
}