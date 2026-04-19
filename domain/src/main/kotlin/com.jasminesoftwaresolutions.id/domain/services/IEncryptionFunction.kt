package com.jasminesoftwaresolutions.id.domain.services

interface IEncryptionFunction {
    fun encrypt(value: ByteArray): ByteArray
    fun decrypt(value: ByteArray): ByteArray
}