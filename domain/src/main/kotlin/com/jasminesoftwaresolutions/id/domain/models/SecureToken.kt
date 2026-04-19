package com.jasminesoftwaresolutions.id.domain.models

import java.security.SecureRandom

object SecureToken {
    private val charset = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '.' + '-' + '_'
    private val random = SecureRandom()

    operator fun invoke(length: Int = 32): String {
        val bytes = ByteArray(length)
        random.nextBytes(bytes)

        return bytes.map { byte -> charset[(byte.toInt() and 0xFF) % charset.size] }
            .joinToString("")
    }
}