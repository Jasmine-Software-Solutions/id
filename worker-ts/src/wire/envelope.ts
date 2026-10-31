import { isObject, MalformedEventError, type Wire } from "./codec";

/** Mirrors a Lambda SQS record's message attributes. */
export type MessageAttributes = Record<string, { stringValue?: string } | undefined>;

export interface ParsedMessage {
  /** Raw body after SNS-envelope unwrapping (JSON text). */
  json: string;
  /** Parsed JSON object. */
  body: Wire;
  /** Discriminator resolved from body or SQS message attributes. */
  eventType: string;
}

/**
 * Parses one SQS record body.
 *
 * Production subscriptions use raw message delivery (no SNS envelope), but
 * un-enveloping whenever a `Message` member exists is harmless and keeps
 * fixtures captured from either wiring usable.
 *
 * The discriminator lookup order mirrors what the old listener could observe:
 * body `event_type` (server Gson and the TS publisher), body `eventType`
 * (events captured before the snake_case policy), and finally the SQS message
 * attribute — the only place the JVM worker ever wrote it. Messages published
 * before the SnsEventDispatcher fix are still in flight, so the fallback must
 * stay.
 */
export function parseMessage(rawBody: string, attributes: MessageAttributes = {}): ParsedMessage {
  let root: unknown;
  try {
    root = JSON.parse(rawBody);
  } catch {
    throw new MalformedEventError("body is not valid JSON");
  }
  if (!isObject(root)) {
    throw new MalformedEventError("body is not a JSON object");
  }

  let json = rawBody;
  let body = root;
  if (typeof root.Message === "string") {
    json = root.Message;
    let inner: unknown;
    try {
      inner = JSON.parse(json);
    } catch {
      throw new MalformedEventError("SNS envelope Message is not valid JSON");
    }
    if (!isObject(inner)) {
      throw new MalformedEventError("SNS envelope Message is not a JSON object");
    }
    body = inner;
  }

  const eventType =
    asNonEmptyString(body.event_type) ??
    asNonEmptyString(body.eventType) ??
    asNonEmptyString(attributes.event_type?.stringValue);

  if (eventType === undefined) {
    throw new MalformedEventError("missing event_type");
  }

  return { json, body, eventType };
}

function asNonEmptyString(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}
