package app.workers.infrastructure

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient

object SqsClientFactory {
    fun create(env: IdAwsEnv): SqsClient {
        val builder = SqsClient.builder().region(Region.of(env.AWS_REGION))

        env.SQS_ENDPOINT_OVERRIDE?.let { builder.endpointOverride(java.net.URI.create(it)) }

        when {
            env.AWS_ACCESS_KEY_ID.isNotBlank() && env.AWS_SECRET_ACCESS_KEY.isNotBlank() ->
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(env.AWS_ACCESS_KEY_ID, env.AWS_SECRET_ACCESS_KEY)
                    )
                )

            env.AWS_PROFILE.isNotBlank() && env.AWS_PROFILE != "default" ->
                builder.credentialsProvider(ProfileCredentialsProvider.create(env.AWS_PROFILE))
        }

        return builder.build()
    }
}
