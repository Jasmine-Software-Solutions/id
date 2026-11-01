package app.workers.infrastructure

import app.infrastructure.services.SnsEventDispatcher
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.jasminesoftwaresolutions.id.domain.events.AccountCreatedEvent
import com.jasminesoftwaresolutions.id.domain.events.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.model.PublishResponse
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * The SNS body must carry its discriminator regardless of which Gson instance
 * the dispatcher was handed.
 *
 * id-server's Gson stamps `event_type` through a TypeAdapterFactory, but the
 * workers' [workerGson] did not, so worker-published events reached SQS with
 * the type only on the SNS message attribute. The email worker dropped
 * credential-stuffing events and SessionTracker never saw an
 * AllSessionsRevokedEvent as a result; the fix belongs at the dispatcher so
 * every publisher agrees.
 */
class SnsEventDispatcherTest {

    private val captured = AtomicReference<PublishRequest>()

    private val fakeClient: SnsClient = Proxy.newProxyInstance(
        SnsClient::class.java.classLoader,
        arrayOf(SnsClient::class.java),
    ) { _, method, args ->
        when {
            method.name == "publish" && args?.size == 1 && args[0] is PublishRequest -> {
                captured.set(args[0] as PublishRequest)
                PublishResponse.builder().build()
            }
            method.name == "toString" -> "FakeSnsClient"
            method.name == "hashCode" -> System.identityHashCode(this)
            method.name == "equals" -> false
            else -> null
        }
    } as SnsClient

    private fun dispatcher(gson: Gson) = SnsEventDispatcher(
        gson = gson,
        topicArn = "arn:aws:sns:us-east-1:123456789012:id-events",
        // Passed explicitly so the constructor never touches app.application.Env,
        // which requires a full server environment (DATABASE_URL et al.).
        region = "us-east-1",
        accessKey = "",
        secretKey = "",
        awsProfile = "",
        endpointOverride = null,
        client = fakeClient,
    )

    private fun event() = AccountCreatedEvent(
        principalId = UUID.randomUUID(),
        action = "id.jasmine.software:CreateAccount",
        resource = "id.jasmine.software://",
        timestamp = Instant.now(),
        accountId = UUID.randomUUID(),
        email = "person@example.com",
        firstName = "Person",
        lastName = "Example",
        emailVerified = false,
        ipAddress = "203.0.113.7",
    )

    @Test
    fun `workerGson bodies carry event_type`() {
        dispatcher(workerGson).dispatch(event())

        val body = JsonParser.parseString(captured.get().message()).asJsonObject
        assertEquals("AccountCreatedEvent", body.get("event_type").asString)
        // The rest of the wire format is untouched.
        assertTrue(body.has("account_id"))
        assertTrue(body.has("principal_id"))
    }

    @Test
    fun `an event that already carries event_type is not altered`() {
        dispatcher(stampingGson()).dispatch(event())

        val body = JsonParser.parseString(captured.get().message()).asJsonObject
        val keys = body.entrySet().count { it.key == "event_type" }
        assertEquals(1, keys)
        assertEquals("AccountCreatedEvent", body.get("event_type").asString)
    }

    /** Stand-in for id-server's Gson, which stamps the discriminator itself. */
    private fun stampingGson(): Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(Instant::class.java, JsonSerializer<Instant> { src, _, _ ->
            JsonPrimitive(src.epochSecond)
        })
        .registerTypeAdapterFactory(object : TypeAdapterFactory {
            override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
                if (!Event::class.java.isAssignableFrom(type.rawType)) return null
                val delegate = gson.getDelegateAdapter(this, type)
                return object : TypeAdapter<T>() {
                    override fun write(out: JsonWriter, value: T) {
                        val tree = delegate.toJsonTree(value).asJsonObject
                        tree.addProperty("event_type", (value as Event).eventType)
                        gson.toJson(tree, out)
                    }

                    override fun read(reader: JsonReader): T = delegate.read(reader)
                }
            }
        })
        .create()
}
