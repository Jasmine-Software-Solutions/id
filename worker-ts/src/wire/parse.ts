import type { SQSEvent, SQSRecord } from "aws-lambda";
import { parseMessage, type ParsedMessage } from "./envelope";
import { isObject, MalformedEventError, type Wire } from "./codec";

/** One SQS record together with its resolved event type and wire body. */
export interface ParsedRecord {
  record: SQSRecord;
  message: ParsedMessage;
}

export interface SkippedRecord {
  messageId: string;
  reason: string;
}

export interface ParseBatchResult {
  parsed: ParsedRecord[];
  /** Poison pills: malformed or unknown-type records that are acked (deleted). */
  skipped: SkippedRecord[];
}

/**
 * Parses every record in an SQS batch.
 *
 * Records that cannot be parsed or whose type the module does not consume are
 * returned as skipped — the caller logs them and omits them from
 * batchItemFailures, matching `SqsEventListener.processMessage`'s poison-pill
 * handling (log and delete, never retry forever).
 */
export function parseBatch(event: SQSEvent, knownTypes: ReadonlySet<string>): ParseBatchResult {
  const parsed: ParsedRecord[] = [];
  const skipped: SkippedRecord[] = [];

  for (const record of event.Records) {
    try {
      const message = parseMessage(record.body ?? "", record.messageAttributes ?? {});
      if (!knownTypes.has(message.eventType)) {
        skipped.push({ messageId: record.messageId, reason: `unknown event type: ${message.eventType}` });
        continue;
      }
      parsed.push({ record, message });
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      skipped.push({ messageId: record.messageId, reason });
    }
  }

  return { parsed, skipped };
}

export function bodyOf(record: ParsedRecord): Wire {
  if (!isObject(record.message.body)) {
    throw new MalformedEventError("event body is not a JSON object");
  }
  return record.message.body;
}

/** Resolves the account a security event refers to, tolerating absence. */
export function findAccountId(body: Wire): string | undefined {
  const value = body.account_id;
  return typeof value === "string" && value.length > 0 ? value : undefined;
}
