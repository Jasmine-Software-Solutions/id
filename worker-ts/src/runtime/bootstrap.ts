import { logs, SeverityNumber, trace } from "@opentelemetry/api";
import { NodeSDK } from "@opentelemetry/sdk-node";
import { BatchSpanProcessor } from "@opentelemetry/sdk-trace";
import { BatchLogRecordProcessor } from "@opentelemetry/sdk-logs";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-grpc";
import { OTLPLogExporter } from "@opentelemetry/exporter-logs-otlp-http";
import { createLogger, setLogSink, type LogLevel, type LogRecord } from "./logging";

const log = createLogger("bootstrap");

let sdk: NodeSDK | undefined;
let spanProcessor: BatchSpanProcessor | undefined;
let logProcessor: BatchLogRecordProcessor | undefined;
let initialized = false;

const SEVERITY: Record<LogLevel, number> = {
  debug: SeverityNumber.DEBUG,
  info: SeverityNumber.INFO,
  warn: SeverityNumber.WARN,
  error: SeverityNumber.ERROR,
};

/**
 * Starts the OTel SDK once per execution environment and mirrors the JSON
 * logger into Loki. CloudWatch remains the baseline: an unreachable collector
 * degrades to stdout only and never fails the invocation.
 */
export function initTelemetry(moduleName: string): void {
  if (initialized) return;
  initialized = true;

  if (process.env.OTEL_SDK_DISABLED === "true") return;

  const tracesEndpoint = process.env.OTEL_EXPORTER_OTLP_ENDPOINT?.trim();
  const logsEndpoint = process.env.OTEL_EXPORTER_OTLP_LOGS_ENDPOINT?.trim();
  if (!tracesEndpoint && !logsEndpoint) return;

  const serviceName = process.env.OTEL_SERVICE_NAME?.trim() || `id-worker-${moduleName}`;

  try {
    const spanProcessors: BatchSpanProcessor[] = [];
    const logRecordProcessors: BatchLogRecordProcessor[] = [];

    if (tracesEndpoint) {
      spanProcessor = new BatchSpanProcessor({
        exporter: new OTLPTraceExporter({ url: tracesEndpoint }),
      });
      spanProcessors.push(spanProcessor);
    }
    if (logsEndpoint) {
      logProcessor = new BatchLogRecordProcessor({
        exporter: new OTLPLogExporter({ url: logsEndpoint }),
      });
      logRecordProcessors.push(logProcessor);
    }

    sdk = new NodeSDK({
      serviceName,
      spanProcessors,
      logRecordProcessors,
    });
    sdk.start();

    const otelLogger = logs.getLogger(serviceName);
    setLogSink((record: LogRecord) => {
      const spanContext = trace.getActiveSpan()?.spanContext();
      otelLogger.emit({
        severityNumber: SEVERITY[record.level],
        severityText: record.level.toUpperCase(),
        body: record.message,
        attributes: {
          "log.logger": record.logger,
          ...record.fields,
          ...(spanContext
            ? { trace_id: spanContext.traceId, span_id: spanContext.spanId }
            : {}),
        },
      });
    });

    log.info("OpenTelemetry started", { service_name: serviceName, traces: !!tracesEndpoint, logs: !!logsEndpoint });
  } catch (error) {
    log.error("OpenTelemetry initialization failed; CloudWatch logging continues", { error });
  }
}

/**
 * Flushes pending spans/logs at the end of an invocation. Lambda freezes the
 * environment after the handler returns, so batches left in the processor would
 * otherwise be dropped or delayed. A slow collector must not extend the
 * invocation indefinitely, hence the cap.
 */
export async function flushTelemetry(timeoutMs = 2_000): Promise<void> {
  const pending = [spanProcessor?.forceFlush(), logProcessor?.forceFlush()].filter(
    (value): value is Promise<void> => value !== undefined,
  );
  if (pending.length === 0) return;

  let timer: NodeJS.Timeout | undefined;
  const timeout = new Promise<void>((resolve) => {
    timer = setTimeout(resolve, timeoutMs);
    timer.unref?.();
  });
  try {
    await Promise.race([Promise.allSettled(pending), timeout]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

/** Test hook: reset module state between suites. */
export async function shutdownTelemetryForTests(): Promise<void> {
  try {
    await Promise.allSettled([spanProcessor?.forceFlush(), logProcessor?.forceFlush()]);
    await sdk?.shutdown();
  } catch {
    // ignore
  }
  sdk = undefined;
  spanProcessor = undefined;
  logProcessor = undefined;
  initialized = false;
  setLogSink(undefined);
}
