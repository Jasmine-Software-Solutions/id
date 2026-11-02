import { GetParametersCommand } from "@aws-sdk/client-ssm";
import { logs, SeverityNumber, trace } from "@opentelemetry/api";
import { NodeSDK } from "@opentelemetry/sdk-node";
import { BatchSpanProcessor } from "@opentelemetry/sdk-trace";
import { BatchLogRecordProcessor } from "@opentelemetry/sdk-logs";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http";
import { OTLPLogExporter } from "@opentelemetry/exporter-logs-otlp-http";
import { awsRegion, createSsmClient } from "./aws";
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
 * Loads the Grafana Cloud OTLP Basic-auth header into the standard exporter
 * environment variable. The SecureString value is the full header line, for
 * example `Authorization=Basic <base64>`. A failure degrades telemetry only;
 * CloudWatch logging continues and the invocation is unaffected.
 */
async function loadOtlpHeaders(): Promise<void> {
  if (process.env.OTEL_EXPORTER_OTLP_HEADERS) return;

  const parameterName = process.env.GRAFANA_CLOUD_BASIC_AUTH_HEADER_PARAMETER?.trim();
  if (!parameterName) return;

  try {
    const client = createSsmClient({
      region: awsRegion(),
      endpoint: process.env.SSM_ENDPOINT_OVERRIDE,
    });
    const response = await client.send(
      new GetParametersCommand({ Names: [parameterName], WithDecryption: true }),
    );
    const value = response.Parameters?.[0]?.Value?.trim();
    if (value) {
      process.env.OTEL_EXPORTER_OTLP_HEADERS = value;
    } else {
      log.warn("Grafana Cloud auth parameter is empty; telemetry is unauthenticated", {
        parameter: parameterName,
      });
    }
  } catch (error) {
    log.warn("Grafana Cloud auth header could not be loaded; telemetry is unauthenticated", {
      parameter: parameterName,
      error,
    });
  }
}

/**
 * Starts the OTel SDK once per execution environment and mirrors the JSON
 * logger into the OTLP backend. CloudWatch remains the baseline: an
 * unreachable collector degrades to stdout only and never fails the
 * invocation.
 */
export async function initTelemetry(moduleName: string): Promise<void> {
  if (initialized) return;
  initialized = true;

  if (process.env.OTEL_SDK_DISABLED === "true") return;

  const tracesEndpoint = process.env.OTEL_EXPORTER_OTLP_ENDPOINT?.trim();
  const logsEndpoint = process.env.OTEL_EXPORTER_OTLP_LOGS_ENDPOINT?.trim();
  if (!tracesEndpoint && !logsEndpoint) return;

  await loadOtlpHeaders();

  const serviceName = process.env.OTEL_SERVICE_NAME?.trim() || `id-worker-${moduleName}`;

  try {
    const spanProcessors: BatchSpanProcessor[] = [];
    const logRecordProcessors: BatchLogRecordProcessor[] = [];

    if (tracesEndpoint) {
      const baseUrl = tracesEndpoint.replace(/\/+$/, "");
      // The SDK does not append a path when an explicit url is supplied.
      const traceUrl = baseUrl.endsWith("/v1/traces") ? baseUrl : `${baseUrl}/v1/traces`;
      spanProcessor = new BatchSpanProcessor({
        exporter: new OTLPTraceExporter({ url: traceUrl }),
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
