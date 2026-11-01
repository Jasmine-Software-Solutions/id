package app.workers.witchwood

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.jasminesoftwaresolutions.witchwood.client.WitchwoodClient
import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.ResourcePathCompiler

/**
 * Compiles policy document JSON into a list of [WitchwoodClient.PolicyStatement]s
 * for upsert into Witchwood.
 *
 * Pure function — no side effects, no external dependencies beyond Gson.
 */
class PolicyCompiler(
    private val gson: Gson = Gson(),
) {
    fun compile(document: JsonArray): List<WitchwoodClient.PolicyStatement> {
        return document.mapIndexed { index, element ->
            val obj = element.asJsonObject
            val effect = obj.get("effect")?.asString?.uppercase() ?: "ALLOW"
            val actions = obj.getAsJsonArray("actions")?.map { it.asString }?.toSet() ?: emptySet()
            val resources = obj.getAsJsonArray("resources")?.map { resource ->
                val r = resource.asString
                if (r.contains('*')) ResourcePathCompiler.compilePattern(r)
                else ResourcePathCompiler.compileResource(r)
            }?.toSet() ?: emptySet()
            val condition = obj.get("condition")?.let { gson.toJson(it) } ?: ""

            WitchwoodClient.PolicyStatement(index, effect, actions, resources, condition)
        }
    }
}
