package com.jasminesoftwaresolutions.id.domain.services.authorization

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant

/**
 * Sandboxes policy document resources so they are scoped within the
 * tenant they belong to at minimum.
 *
 * Each resource string in the document follows the form `service://path`.
 * If the path portion does not already begin with the tenant prefix
 * derived from [tenant], it is prepended. The service portion is
 * preserved as-is.
 *
 * Platform-scoped policies ([tenant] is `null`) produce no prefix
 * (no sandboxing needed).
 */
object PolicySandboxer {
    fun wrapResourcesTo(document: JsonArray, tenant: ITenant?): JsonArray {
        val prefix = buildPrefix(tenant)
        if (prefix == null) return document // platform — no sandboxing needed

        val result = JsonArray()
        for (element in document) {
            if (element is JsonObject) {
                val wrapped = element.deepCopy() as JsonObject
                val resources = wrapped.getAsJsonArray("resources") ?: continue
                val sandboxed = JsonArray()
                for (resource in resources) {
                    val raw = resource.asString ?: continue
                    sandboxed.add(sandboxResource(raw, prefix))
                }
                wrapped.add("resources", sandboxed)
                result.add(wrapped)
            } else {
                result.add(element)
            }
        }
        return result
    }

    // ── internals ──

    private fun sandboxResource(raw: String, prefix: String): String {
        val separator = "://"
        val idx = raw.indexOf(separator)
        val service = if (idx >= 0) raw.substring(0, idx) else return raw
        var path = if (idx >= 0) raw.substring(idx + separator.length) else return raw

        // If the path is empty (e.g. "*://"), fill it with the prefix.
        if (path.isEmpty()) return "$service$separator$prefix"

        // Already sandboxed: the path starts with the prefix.
        if (path.startsWith(prefix)) return raw

        // Path has content but not the prefix — prepend it.
        // Handle trailing wildcard: "*://**" → "*://prefix/**"
        if (path == "**") return "$service$separator$prefix/**"

        return "$service$separator$prefix/$path"
    }

    /**
     * Builds the tenant path prefix for [tenant], or `null`
     * when it is platform-scoped (no sandboxing).
     */
    private fun buildPrefix(tenant: ITenant?): String? =
        tenant?.let { "tenant:${it.id}" }
}
