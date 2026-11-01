package com.jasminesoftwaresolutions.id.domain.models

import com.google.gson.Gson
import com.jasminesoftwaresolutions.id.domain.services.ISigningFunction
import java.util.*

class SignedValue<T>(val signingFunction: ISigningFunction, val value: T) {
    companion object {
        private val gson = Gson()

        fun verify(signingFunction: ISigningFunction, value: String): Boolean {
            val b64EncodedPayload = value.substringBefore(".")
            val b64EncodedSignature = value.substringAfterLast(".")

            val newSignature = signingFunction.sign(b64EncodedPayload.toByteArray())
            val b64EncodedNewSignature = Base64.getEncoder().encodeToString(newSignature)

            return b64EncodedNewSignature == b64EncodedSignature
        }

        fun <T> fromString(signingFunction: ISigningFunction, value: String, valueType: Class<T>): SignedValue<T> {
            if (!verify(signingFunction, value)) throw IllegalArgumentException("Invalid signature")

            val payload = value.substringBefore(".")
            val jsonEncodedPayload = String(Base64.getDecoder().decode(payload))
            val payloadObject = gson.fromJson(jsonEncodedPayload, valueType)

            return SignedValue(signingFunction, payloadObject as T)
        }
    }

    override fun toString(): String {
        val payload = gson.toJsonTree(value).asJsonObject
        val jsonEncodedPayload = gson.toJson(payload)
        val b64EncodedPayload = Base64.getEncoder().encodeToString(jsonEncodedPayload.toByteArray())

        val signature = signingFunction.sign(b64EncodedPayload.toByteArray())
        val b64EncodedSignature = Base64.getEncoder().encodeToString(signature)

        return "$b64EncodedPayload.$b64EncodedSignature"
    }
}