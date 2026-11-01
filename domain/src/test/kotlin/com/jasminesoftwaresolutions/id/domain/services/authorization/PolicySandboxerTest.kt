package com.jasminesoftwaresolutions.id.domain.services.authorization

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Pins the two-case scope model: a platform-scoped policy ([ITenant] is `null`)
 * is left untouched, and a tenant-scoped one has every resource path prefixed
 * with `tenant:{id}` so a tenant policy cannot escape its own subtree.
 */
class PolicySandboxerTest {

    private class FakeTenant(override val id: UUID) : ITenant {
        override var suspended: Boolean = false
        override var name: String = "fake"
        override val createdAt: Instant = Instant.EPOCH
    }

    private val tenantId = "00000000-0000-0000-0000-000000000000"
    private val tenant = FakeTenant(UUID.fromString(tenantId))

    /** One ALLOW statement carrying every [resources] entry, as the console authors them. */
    private fun document(vararg resources: String): JsonArray =
        JsonParser.parseString(
            """[{"effect":"ALLOW","actions":["*"],"resources":[${
                resources.joinToString(",") { "\"$it\"" }
            }]}]"""
        ).asJsonArray

    private fun resourcesOf(document: JsonArray): List<String> =
        document[0].asJsonObject.getAsJsonArray("resources").map { it.asString }

    @Test
    fun `platform scope returns the document unchanged`() {
        val original = document("*://", "*://**")

        val result = PolicySandboxer.wrapResourcesTo(original, null)

        assertSame(original, result, "platform scope must not copy or rewrite the document")
    }

    @Test
    fun `tenant scope prefixes an empty and a wildcard path`() {
        val result = PolicySandboxer.wrapResourcesTo(document("*://", "*://**"), tenant)

        assertEquals(
            listOf("*://tenant:$tenantId", "*://tenant:$tenantId/**"),
            resourcesOf(result),
        )
    }

    @Test
    fun `tenant scope leaves an already-prefixed path alone`() {
        val already = "*://tenant:$tenantId/**"

        val result = PolicySandboxer.wrapResourcesTo(document(already), tenant)

        assertEquals(listOf(already), resourcesOf(result))
    }

    @Test
    fun `tenant scope prepends the prefix to a relative path`() {
        val result = PolicySandboxer.wrapResourcesTo(document("id.jasmine.software://forms/**"), tenant)

        assertEquals(
            listOf("id.jasmine.software://tenant:$tenantId/forms/**"),
            resourcesOf(result),
        )
    }

    @Test
    fun `tenant scope does not touch the service portion`() {
        val result = PolicySandboxer.wrapResourcesTo(document("id.jasmine.software://roles"), tenant)

        assertEquals(
            listOf("id.jasmine.software://tenant:$tenantId/roles"),
            resourcesOf(result),
        )
    }
}
