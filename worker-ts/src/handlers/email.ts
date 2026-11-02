import type { Context, SQSBatchResponse, SQSEvent } from "aws-lambda";
import type { EmailConfig } from "../config/email";
import { loadEmailConfig } from "../config/email";
import { env } from "../config/env";
import { createSsmClient } from "../runtime/aws";
import { flushTelemetry, initTelemetry } from "../runtime/bootstrap";
import { runBatch } from "../runtime/batch-processor";
import { createLogger } from "../runtime/logging";
import { Secrets } from "../runtime/secrets";
import { EMAIL_EVENT_TYPES } from "../wire/event-types";
import { bodyOf } from "../wire/parse";
import { EmailWorker } from "../modules/email";
import { ResendEmailSender } from "../modules/email/resend-sender";

const logger = createLogger("handler.email");

export interface EmailServices {
  worker: EmailWorker;
}

let servicesPromise: Promise<EmailServices> | undefined;

export function getEmailServices(): Promise<EmailServices> {
  // A transient SSM/credential failure must not poison a warm environment for
  // its whole lifetime: drop the rejected promise so the next invocation
  // retries initialization.
  servicesPromise ??= createEmailServices().catch((error) => {
    servicesPromise = undefined;
    throw error;
  });
  return servicesPromise;
}

export async function createEmailServices(config: EmailConfig = loadEmailConfig()): Promise<EmailServices> {
  const apiKey = await resolveResendApiKey(config);
  const sender = new ResendEmailSender({
    apiKey,
    senderName: config.senderName,
    senderEmail: config.senderEmail,
    baseUrl: config.resendBaseUrl,
    logger: createLogger("email.resend"),
  });
  return { worker: new EmailWorker(sender, config.idServerBaseUrl) };
}

/**
 * The Resend key comes from SSM in production. A local run may leave both the
 * env var and the prefix unset, in which case the sender's blank-key
 * `[dev-mail]` path is intentional.
 */
async function resolveResendApiKey(config: EmailConfig): Promise<string> {
  if (config.resendApiKey !== undefined) return config.resendApiKey;
  if (!config.ssmPrefix) return "";

  const secrets = await Secrets.load({
    names: ["id/resend-api-key"],
    prefix: config.ssmPrefix,
    client: createSsmClient({
      region: process.env.AWS_REGION || "us-east-1",
      endpoint: env("SSM_ENDPOINT_OVERRIDE"),
    }),
    logger,
  });
  return secrets.get("id/resend-api-key");
}

export const handler = async (event: SQSEvent, context: Context): Promise<SQSBatchResponse> => {
  await initTelemetry("email");
  try {
    const services = await getEmailServices();
    return await runBatch(event, context, {
      moduleName: "email",
      knownTypes: EMAIL_EVENT_TYPES,
      createContext: () => services,
      process: async (record, svc) => {
        await svc.worker.handle(record.message.eventType, bodyOf(record));
      },
    });
  } finally {
    await flushTelemetry();
  }
};

/** Test hook: drop the cold-start singleton. */
export function resetEmailServicesForTests(): void {
  servicesPromise = undefined;
}
