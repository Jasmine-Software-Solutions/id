package app.workers.infrastructure

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.Instant

/**
 * The Gson configuration shared by every worker.
 *
 * Field names follow the server's snake_case wire format, and [Instant] travels
 * as epoch seconds in both directions — the serializer matters when a worker
 * publishes to SNS, the deserializer when it reads an event off SQS.
 */
internal val workerGson: Gson = GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .registerTypeAdapter(Instant::class.java, object : JsonSerializer<Instant> {
        override fun serialize(src: Instant, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
            JsonPrimitive(src.epochSecond)
    })
    .registerTypeAdapter(Instant::class.java, object : JsonDeserializer<Instant> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Instant =
            Instant.ofEpochSecond(json.asLong)
    })
    .create()
