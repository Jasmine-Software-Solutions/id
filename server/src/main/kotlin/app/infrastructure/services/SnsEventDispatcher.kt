package app.infrastructure.services

import app.application.Env
import com.google.gson.Gson
import com.jasminesoftwaresolutions.id.domain.events.Event
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import java.net.URI

class SnsEventDispatcher(
    private val gson: Gson,
    topicArn: String = Env.SNS_TOPIC_ARN,
    region: String = Env.SNS_REGION,
    accessKey: String = Env.AWS_ACCESS_KEY_ID,
    secretKey: String = Env.AWS_SECRET_ACCESS_KEY,
    awsProfile: String = Env.AWS_PROFILE,
    endpointOverride: String? = Env.SNS_ENDPOINT_OVERRIDE,
    private val client: SnsClient = createSnsClient(region, accessKey, secretKey, awsProfile, endpointOverride),
) : IEventDispatcher, AutoCloseable by client {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val topic = topicArn.ifBlank {
        log.warn("SNS_TOPIC_ARN not configured — events will be logged but not published")
        null
    }

    @WithSpan
    override fun dispatch(event: Event) {
        val target = topic ?: run {
            log.info("Event not published (no topic configured): class=${event::class.simpleName} " +
                     "principal=${event.principalId} action=${event.action}")
            return
        }

        val body = try {
            gson.toJson(event)
        } catch (e: Exception) {
            log.error("Failed to serialize event ${event::class.simpleName}: ${e.message}", e)
            return
        }

        val request = PublishRequest.builder()
            .topicArn(target)
            .message(body)
            .messageAttributes(
                mapOf(
                    "event_type" to stringAttr(event::class.simpleName ?: "Event"),
                    "action" to stringAttr(event.action),
                    "principal_id" to stringAttr(event.principalId.toString()),
                )
            )
            .build()

        try {
            val response = client.publish(request)
            log.info("Published {} to SNS: seq={}", event::class.simpleName, response.sequenceNumber())
        } catch (e: Exception) {
            log.error("Failed to publish event ${event::class.simpleName} to SNS: ${e.message}", e)
        }
    }

    private fun stringAttr(value: String) = MessageAttributeValue.builder()
        .dataType("String")
        .stringValue(value)
        .build()

    companion object {
        private fun createSnsClient(
            region: String,
            accessKey: String,
            secretKey: String,
            awsProfile: String,
            endpointOverride: String?,
        ): SnsClient {
            val builder = SnsClient.builder()
                .region(Region.of(region))

            endpointOverride?.let { builder.endpointOverride(URI.create(it)) }

            when {
                accessKey.isNotBlank() && secretKey.isNotBlank() -> {
                    builder.credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                    )
                }

                awsProfile.isNotBlank() && awsProfile != "default" -> {
                    builder.credentialsProvider(ProfileCredentialsProvider.create(awsProfile))
                }
            }

            return builder.build()
        }
    }
}
