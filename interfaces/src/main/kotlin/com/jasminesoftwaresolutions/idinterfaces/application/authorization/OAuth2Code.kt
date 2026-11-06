package com.jasminesoftwaresolutions.idinterfaces.application.authorization

import java.net.URLEncoder
import java.util.*

/**
 * The shared encoding of an authorization code and of a redirect URL.
 *
 * Both the normal OAuth2 flow and the handoff flow issue a delegated session
 * plus a raw code, and must present them to the client identically: the code
 * handed to the browser is `Base64("<delegatedSessionId>:<rawCode>")`.
 */
internal object OAuth2Code {
    fun encode(delegatedSessionId: UUID, rawCode: String): String =
        Base64.getEncoder().encodeToString(
            "$delegatedSessionId:$rawCode".toByteArray(Charsets.UTF_8)
        )

    /** Decode an opaque code into its delegated-session id and raw code, or null when malformed. */
    fun decode(encoded: String): Pair<UUID, String>? {
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
            ?: return null

        val parts = decoded.toString(Charsets.UTF_8).split(":")
        if (parts.size != 2) return null

        val id = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return null
        return id to parts[1]
    }

    fun buildRedirectUrl(baseUri: String, params: Map<String, String>): String {
        val queryString = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val separator = if (baseUri.contains('?')) "&" else "?"
        return "$baseUri$separator$queryString"
    }
}
