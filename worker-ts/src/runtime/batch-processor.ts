import type { Context, SQSBatchItemFailure, SQSBatchResponse, SQSEvent } from "aws-lambda";
import { SpanStatusCode, trace } from "@opentelemetry/api";
import { parseBatch, type ParsedRecord } from "../wire/parse";
import { hasTimeForNext } from "./deadline";
import { createLogger, type Logger } from "./logging";

const tracer = trace.getTracer("id-worker");

export interface BatchDefinition<C> {
  moduleName: string;
  knownTypes: ReadonlySet<string>;
  /** Builds per-environment services and per-invocation state. */
  createContext: () => C | Promise<C>;
  /**
   * Runs before any record is processed. The security module uses it to
   * resolve every IP the batch could need in one ip-api batch request, the
   * replacement for the JVM's background geolocation thread.
   */
  prepare?: (records: ParsedRecord[], context: C, invocation: Context) => Promise<void>;
  process: (record: ParsedRecord, context: C, invocation: Context) => Promise<void>;
  logger?: Logger;
}

/**
 * Runs one SQS batch with partial-batch failure reporting.
 *
 * - malformed / unknown-type records are poison pills: logged and acked
 * - a record whose handler throws is reported in `batchItemFailures`
 * - when less than the deadline margin remains, the unprocessed tail is
 *   reported as failed so SQS redelivers it instead of the invocation being
 *   killed mid-record
 */
export async function runBatch<C>(
  event: SQSEvent,
  invocation: Context,
  definition: BatchDefinition<C>,
): Promise<SQSBatchResponse> {
  const logger = definition.logger ?? createLogger(`batch.${definition.moduleName}`);
  const records = event.Records ?? [];

  return tracer.startActiveSpan(
    `id-worker.${definition.moduleName}.batch`,
    async (span) => {
      span.setAttribute("id_worker.module", definition.moduleName);
      span.setAttribute("messaging.batch.message_count", records.length);

      const failures: SQSBatchItemFailure[] = [];
      const { parsed, skipped } = parseBatch({ ...event, Records: records }, definition.knownTypes);

      for (const skip of skipped) {
        // SqsEventListener.processMessage deleted deserialization failures
        // immediately rather than retrying them forever.
        logger.warn("Acking poison-pill message", {
          message_id: skip.messageId,
          reason: skip.reason,
        });
      }

      if (parsed.length === 0) {
        span.end();
        return { batchItemFailures: failures };
      }

      const context = await definition.createContext();

      if (definition.prepare) {
        try {
          await definition.prepare(parsed, context, invocation);
        } catch (error) {
          logger.error("Batch preparation failed; failing every record", { error });
          for (const record of parsed) failures.push({ itemIdentifier: record.record.messageId });
          span.setStatus({ code: SpanStatusCode.ERROR });
          span.end();
          return { batchItemFailures: failures };
        }
      }

      for (let index = 0; index < parsed.length; index += 1) {
        const record = parsed[index]!;

        if (!hasTimeForNext(invocation)) {
          logger.warn("Deadline guard reached; failing remaining records", {
            remaining: parsed.length - index,
          });
          for (let tail = index; tail < parsed.length; tail += 1) {
            failures.push({ itemIdentifier: parsed[tail]!.record.messageId });
          }
          break;
        }

        await tracer.startActiveSpan(
          `id-worker.${definition.moduleName}.record`,
          async (recordSpan) => {
            recordSpan.setAttribute("messaging.message.id", record.record.messageId);
            recordSpan.setAttribute("id_worker.event_type", record.message.eventType);
            try {
              await definition.process(record, context, invocation);
            } catch (error) {
              recordSpan.recordException(error as Error);
              recordSpan.setStatus({ code: SpanStatusCode.ERROR });
              failures.push({ itemIdentifier: record.record.messageId });
              logger.error("Handler failed for event; leaving message for SQS retry", {
                message_id: record.record.messageId,
                event_type: record.message.eventType,
                error,
              });
            } finally {
              recordSpan.end();
            }
          },
        );
      }

      span.setAttribute("id_worker.failures", failures.length);
      span.end();
      return { batchItemFailures: failures };
    },
  );
}
