package com.jasminesoftwaresolutions.id.domain.services

interface ISigningFunction {
    fun sign(value: ByteArray): ByteArray
}