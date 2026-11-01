package com.jasminesoftwaresolutions.id.domain.services

interface IHashFunction {
    fun hash(value: ByteArray): String
    fun verify(value: ByteArray, hash: String): Boolean
}