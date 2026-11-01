import { awsRegion } from "../runtime/aws";
import { env, envInt, envOr } from "./env";

export interface DatabaseConfig {
  host: string;
  port: number;
  database: string;
  user: string;
  /** Only populated from the environment; production loads it from SSM. */
  password?: string;
  poolMax: number;
  connectTimeoutMs: number;
  queryTimeoutMs: number;
}

export interface SecurityConfig {
  moduleName: "security";
  awsRegion: string;
  snsTopicArn: string;
  snsRegion: string;
  snsEndpoint?: string;
  ssmPrefix: string;
  database: DatabaseConfig;
  redisUrl: string;
  creationThreshold: number;
  creationWindowMinutes: number;
  ipApiBaseUrl: string;
  hibpBaseUrl: string;
}

export function loadSecurityConfig(): SecurityConfig {
  const region = awsRegion(env("SNS_REGION"));
  return {
    moduleName: "security",
    awsRegion: region,
    snsTopicArn: envOr("SNS_TOPIC_ARN", ""),
    snsRegion: envOr("SNS_REGION", region),
    snsEndpoint: env("SNS_ENDPOINT_OVERRIDE"),
    ssmPrefix: envOr("SSM_PARAMETER_PREFIX", ""),
    database: {
      host: envOr("PGHOST", "localhost"),
      port: envInt("PGPORT", 5432),
      database: envOr("PGDATABASE", "id"),
      user: envOr("PGUSER", "user"),
      password: env("PGPASSWORD"),
      poolMax: envInt("PGPOOL_MAX", 2),
      connectTimeoutMs: envInt("PG_CONNECT_TIMEOUT_MS", 5_000),
      queryTimeoutMs: envInt("PG_QUERY_TIMEOUT_MS", 5_000),
    },
    redisUrl: envOr("REDIS_URL", "redis://localhost:6379"),
    creationThreshold: envInt("CREATION_THRESHOLD", 10),
    creationWindowMinutes: envInt("CREATION_WINDOW_MINUTES", 60),
    ipApiBaseUrl: envOr("IP_API_BASE_URL", "http://ip-api.com"),
    hibpBaseUrl: envOr("HIBP_BASE_URL", "https://api.pwnedpasswords.com"),
  };
}
