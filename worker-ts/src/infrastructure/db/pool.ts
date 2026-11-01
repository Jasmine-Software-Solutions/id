import { Pool } from "pg";
import type { DatabaseConfig } from "../../config/security";

/**
 * Small pool (max 2) sized for the security function's reserved concurrency;
 * schema ownership stays with id-server, so no migrations run here.
 */
export function createPool(config: DatabaseConfig & { password: string }): Pool {
  return new Pool({
    host: config.host,
    port: config.port,
    database: config.database,
    user: config.user,
    password: config.password,
    max: config.poolMax,
    connectionTimeoutMillis: config.connectTimeoutMs,
    idleTimeoutMillis: 30_000,
    statement_timeout: config.queryTimeoutMs,
    query_timeout: config.queryTimeoutMs,
    application_name: "id-worker-ts",
  });
}
