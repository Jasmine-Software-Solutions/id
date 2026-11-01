package app.workers.infrastructure

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jasminesoftwaresolutions.id.domain.events.Event
import com.jasminesoftwaresolutions.id.domain.events.IWorker
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * Long-polling SQS listener that deserializes messages to concrete [Event] subtypes
 * and dispatches them to a handler function.
 *
 * ## SNS → SQS envelope
 *
 * The SNS → SQS subscription wraps each event inside an SNS notification envelope:
 * ```json
 * { "Type": "Notification", "Message": "{\"eventType\":\"...\", ...}", ... }
 * ```
 * This listener unwraps the envelope, deserializes [Message] using a type registry
 * keyed on the [Event.eventType] discriminator.
 *
 * ## Retry and DLQ
 *
 * AWS SQS queues support **redrive policies** that automatically route messages
 * to a dead-letter queue (DLQ) after a configured number of receives. This listener
 * leverages SQS's built-in `approximateReceiveCount` attribute:
 *
 * - Messages are deleted after successful handling.
 * - On handler failure, the exception is rethrown (message stays in queue,
 *   visibility timeout expires, SQS redelivers).
 * - After `maxReceives` (default 3), the message is moved to the DLQ by SQS
 *   itself — no manual DLQ publishing needed.
 * - Deserialization failures are logged and the message is **deleted immediately**
 *   (poison pill — can never succeed).
 *
 * **Prerequisite:** The SQS queue must be configured with a redrive policy
 * (maxReceiveCount + deadLetterTargetArn) at infrastructure level
 * (CloudFormation/Terraform/CDK).
 */
class SqsEventListener(
    private val queueUrl: String,
    private val eventTypes: Map<String, KClass<out Event>>,
    private val handler: (Event) -> Unit,
    private val client: SqsClient,
    private val maxReceives: Int = 3,
    /**
     * SQS long-poll duration. [stop] only flips a flag, so a shutdown can wait
     * up to this long for the in-flight poll to return — keep it comfortably
     * inside the orchestrator's stop grace period.
     */
    private val waitTimeSeconds: Int = 10,
    private val gson: Gson = workerGson,
) : IWorker {

    private val running = AtomicBoolean(false)

    /** Tracks per-message receive counts for visibility into retry state. */
    private val receiveCounts = ConcurrentHashMap<String, AtomicInteger>()

    override fun start() {
        running.set(true)
        log.info("SqsEventListener started, listening on $queueUrl (maxReceives=$maxReceives)")

        while (running.get()) {
            try {
                val response = client.receiveMessage(
                    ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(waitTimeSeconds) // long polling
                        .attributeNamesWithStrings("ApproximateReceiveCount")
                        .build()
                )

                for (message in response.messages()) {
                    log.info(
                        "Received SQS message {} (body preview: {})", message.messageId(),
                        message.body().take(100).replace("\n", " ")
                    )
                    val receiveCount: Int =
                        ((message.attributes() as Map<String, String>)["ApproximateReceiveCount"]?.toIntOrNull() ?: 1)
                    receiveCounts[message.messageId()] = AtomicInteger(receiveCount)

                    if (receiveCount > maxReceives) {
                        log.warn("Message ${message.messageId()} exceeded max receives ($receiveCount > $maxReceives), deleting")
                        deleteMessage(message)
                        continue
                    }

                    val success = processMessage(message)
                    if (success) {
                        deleteMessage(message)
                        receiveCounts.remove(message.messageId())
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    log.error("SQS receive loop error, retrying in 5s: ${e.message}", e)
                    Thread.sleep(5_000)
                }
            }
        }

        log.info("SqsEventListener stopped")
    }

    override fun stop() {
        running.set(false)
    }

    /**
     * Process a single message. Returns true on success (caller deletes), false on
     * handler failure (message left in queue for retry/DLQ).
     */
    private fun processMessage(message: Message): Boolean {
        val event = try {
            deserialize(message.body())
        } catch (e: Exception) {
            log.error(
                "Failed to deserialize SQS message ${message.messageId()}: ${e.message}. Deleting poison pill.",
                e
            )
            deleteMessage(message)
            return true
        }

        return try {
            handler(event)
            true
        } catch (e: Exception) {
            val count = receiveCounts[message.messageId()]?.get() ?: 1
            log.error(
                "Handler failed for event ${event.eventType} msg=${message.messageId()} attempt=$count/$maxReceives: ${e.message}",
                e
            )
            false
        }
    }

    private fun deleteMessage(message: Message) {
        try {
            client.deleteMessage(
                DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build()
            )
        } catch (e: Exception) {
            log.error("Failed to delete message ${message.messageId()}: ${e.message}", e)
        }
    }

    /** Unwrap SNS envelope → parse [Event.eventType] → deserialize to concrete class. */
    private fun deserialize(raw: String): Event {
        val root = JsonParser.parseString(raw).asJsonObject
        val messageJson = if (root.has("Message")) {
            root.get("Message").asString
        } else {
            raw
        }

        val parsed = JsonParser.parseString(messageJson).asJsonObject
        val type = parsed.get("event_type")?.asString
            ?: parsed.get("eventType")?.asString
            ?: throw IllegalStateException("Missing event_type in message payload")

        val eventClass = eventTypes[type]
            ?: throw IllegalStateException("Unknown event type: $type")

        return gson.fromJson(messageJson, eventClass.java)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SqsEventListener::class.java)
    }
}
