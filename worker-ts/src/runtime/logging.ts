/**
 * Structured JSON logging to stdout (the CloudWatch baseline).
 *
 * A sink can be installed by the telemetry bootstrap so the same records are
 * mirrored to Loki over OTLP without every module importing OpenTelemetry.
 */

export type LogLevel = "debug" | "info" | "warn" | "error";

const LEVEL_ORDER: Record<LogLevel, number> = { debug: 10, info: 20, warn: 30, error: 40 };

export interface LogRecord {
  time: string;
  level: LogLevel;
  logger: string;
  message: string;
  fields: Record<string, unknown>;
}

export type LogSink = (record: LogRecord) => void;

let sink: LogSink | undefined;
let threshold: LogLevel = parseLevel(process.env.LOG_LEVEL);

export function setLogSink(value: LogSink | undefined): void {
  sink = value;
}

export function setLogLevel(level: LogLevel): void {
  threshold = level;
}

export interface Logger {
  debug(message: string, fields?: Record<string, unknown>): void;
  info(message: string, fields?: Record<string, unknown>): void;
  warn(message: string, fields?: Record<string, unknown>): void;
  error(message: string, fields?: Record<string, unknown>): void;
}

export function createLogger(name: string): Logger {
  const write = (level: LogLevel, message: string, fields?: Record<string, unknown>): void => {
    if (LEVEL_ORDER[level] < LEVEL_ORDER[threshold]) return;
    const record: LogRecord = {
      time: new Date().toISOString(),
      level,
      logger: name,
      message,
      fields: normalize(fields ?? {}),
    };
    // CloudWatch captures stdout; keep one JSON object per line.
    const line = JSON.stringify({
      time: record.time,
      level: record.level,
      logger: record.logger,
      msg: record.message,
      ...record.fields,
    });
    if (level === "error") console.error(line);
    else if (level === "warn") console.warn(line);
    else console.log(line);

    if (sink) {
      try {
        sink(record);
      } catch {
        // Telemetry must never break the baseline logger.
      }
    }
  };

  return {
    debug: (message, fields) => write("debug", message, fields),
    info: (message, fields) => write("info", message, fields),
    warn: (message, fields) => write("warn", message, fields),
    error: (message, fields) => write("error", message, fields),
  };
}

/** Reduces Errors and other non-JSON values to loggable strings/objects. */
function normalize(fields: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(fields)) {
    if (value instanceof Error) {
      out[key] = value.message;
      if (value.stack) out[`${key}_stack`] = value.stack;
    } else if (value === undefined) {
      continue;
    } else if (typeof value === "object" && value !== null) {
      try {
        JSON.stringify(value);
        out[key] = value;
      } catch {
        out[key] = String(value);
      }
    } else {
      out[key] = value;
    }
  }
  return out;
}

function parseLevel(raw: string | undefined): LogLevel {
  const value = raw?.trim().toLowerCase();
  return value === "debug" || value === "info" || value === "warn" || value === "error" ? value : "info";
}
