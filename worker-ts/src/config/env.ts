export function env(name: string): string | undefined {
  const value = process.env[name];
  return value === undefined || value === "" ? undefined : value;
}

export function envOr(name: string, fallback: string): string {
  return env(name) ?? fallback;
}

export function envInt(name: string, fallback: number): number {
  const raw = env(name);
  if (raw === undefined) return fallback;
  const value = Number.parseInt(raw, 10);
  return Number.isFinite(value) ? value : fallback;
}

export function envBool(name: string, fallback: boolean): boolean {
  const raw = env(name)?.trim().toLowerCase();
  if (raw === undefined) return fallback;
  return raw === "1" || raw === "true" || raw === "yes" || raw === "on";
}

export interface TelemetryConfig {
  tracesEndpoint?: string;
  logsEndpoint?: string;
}

export function telemetryConfig(): TelemetryConfig {
  return {
    tracesEndpoint: env("OTEL_EXPORTER_OTLP_ENDPOINT"),
    logsEndpoint: env("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT"),
  };
}
