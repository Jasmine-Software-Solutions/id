import { SNSClient, PublishCommand, type MessageAttributeValue } from "@aws-sdk/client-sns";
import { toWire, type Wire } from "./codec";
import { createLogger, type Logger } from "../runtime/logging";

/**
 * Principal recorded on events the worker itself emits (security detections,
 * restrictions, session revocations). The plan fixes this at the nil UUID so
 * downstream consumers can recognize system-originated events.
 */
export const SYSTEM_PRINCIPAL_ID = "00000000-0000-0000-0000-000000000000";

/** The action prefix / platform resource shared by every ID event. */
export const ID_SERVICE = "id.jasmine.software";
export const ID_PLATFORM_RESOURCE = `${ID_SERVICE}://`;

export interface OutboundEvent {
  eventType: string;
  eventId: string;
  principalId: string;
  action: string;
  resource: string;
  timestamp: Date;
  [field: string]: unknown;
}

export interface EventDispatcher {
  dispatch(event: OutboundEvent): Promise<void>;
}

export function systemEvent(eventType: string, fields: Record<string, unknown>): OutboundEvent {
  return {
    eventType,
    eventId: crypto.randomUUID(),
    principalId: SYSTEM_PRINCIPAL_ID,
    action: "",
    resource: ID_PLATFORM_RESOURCE,
    timestamp: new Date(),
    ...fields,
  };
}

/**
 * Serializes an event exactly as `workerGson` did (snake_case, epoch seconds)
 * and stamps `event_type` into the body. The JVM worker's Gson did not stamp
 * the discriminator, so its events rode on the SNS message attribute alone;
 * stamping here matches the server's dispatcher and the Phase 6 fix.
 */
export function serializeEvent(event: OutboundEvent): string {
  const wire = toWire(event) as Wire;
  if (wire.event_type === undefined) {
    wire.event_type = event.eventType;
  }
  return JSON.stringify(wire);
}

export interface SnsPublisherOptions {
  topicArn: string;
  region: string;
  endpoint?: string;
  logger?: Logger;
  client?: SNSClient;
}

export class SnsPublisher implements EventDispatcher {
  private readonly client: SNSClient;
  private readonly topicArn: string;
  private readonly logger: Logger;

  constructor(options: SnsPublisherOptions) {
    this.topicArn = options.topicArn;
    this.logger = options.logger ?? createLogger("publisher");
    this.client =
      options.client ??
      new SNSClient({
        region: options.region,
        ...(options.endpoint ? { endpoint: options.endpoint } : {}),
      });
  }

  async dispatch(event: OutboundEvent): Promise<void> {
    if (!this.topicArn) {
      this.logger.warn("SNS_TOPIC_ARN not configured — event logged but not published", {
        event_type: event.eventType,
        principal_id: event.principalId,
        action: event.action,
      });
      return;
    }

    let body: string;
    try {
      body = serializeEvent(event);
    } catch (error) {
      this.logger.error("Failed to serialize event", { event_type: event.eventType, error: errorMessage(error) });
      return;
    }

    try {
      const response = await this.client.send(
        new PublishCommand({
          TopicArn: this.topicArn,
          Message: body,
          MessageAttributes: {
            event_type: stringAttribute(event.eventType),
            action: stringAttribute(event.action),
            principal_id: stringAttribute(event.principalId),
          },
        }),
      );
      this.logger.info("Published event to SNS", {
        event_type: event.eventType,
        message_id: response.MessageId,
      });
    } catch (error) {
      // IEventDispatcher.dispatch is fire-and-forget: failures are logged, never
      // thrown into the detector that emitted the event.
      this.logger.error("Failed to publish event to SNS", {
        event_type: event.eventType,
        error: errorMessage(error),
      });
    }
  }
}

/** Publisher used in local runs with no topic configured. */
export class LoggingPublisher implements EventDispatcher {
  private readonly logger: Logger;

  constructor(logger: Logger = createLogger("publisher")) {
    this.logger = logger;
  }

  async dispatch(event: OutboundEvent): Promise<void> {
    this.logger.info("Event not published (no topic configured)", {
      event_type: event.eventType,
      principal_id: event.principalId,
      action: event.action,
      body: serializeEvent(event),
    });
  }
}

export function createPublisher(options: {
  topicArn: string;
  region: string;
  endpoint?: string;
  logger?: Logger;
}): EventDispatcher {
  if (!options.topicArn) return new LoggingPublisher(options.logger);
  return new SnsPublisher(options);
}

function stringAttribute(value: string): MessageAttributeValue {
  return { DataType: "String", StringValue: value };
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
