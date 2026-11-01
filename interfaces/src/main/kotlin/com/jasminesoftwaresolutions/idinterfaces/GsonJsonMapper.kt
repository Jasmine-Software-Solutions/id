package com.jasminesoftwaresolutions.idinterfaces

import com.google.gson.Gson
import io.javalin.json.JsonMapper
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Type

/**
 * Javalin [JsonMapper] backed by Gson so that [com.google.gson.JsonObject] and
 * all other Gson types are serialized correctly by [io.javalin.http.Context.json].
 */
class GsonJsonMapper(private val gson: Gson) : JsonMapper {
    override fun toJsonString(obj: Any, type: Type): String = gson.toJson(obj, type)

    override fun <T : Any> fromJsonString(json: String, targetType: Type): T =
        gson.fromJson(json, targetType)

    override fun <T : Any> fromJsonStream(json: InputStream, targetType: Type): T =
        gson.fromJson(InputStreamReader(json), targetType)
}
