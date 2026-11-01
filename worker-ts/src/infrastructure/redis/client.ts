import Redis from "ioredis";
import type { Logger } from "../../runtime/logging";

/** Every key the TypeScript worker owns is namespaced so it can be spotted
 * (and flushed) independently of id-server's session cache. */
export const KEY_PREFIX = "idw:v1:";

export function redisKey(...parts: string[]): string {
  return `${KEY_PREFIX}${parts.join(":")}`;
}

/**
 * Components are base64url-encoded because keys can carry IPv6 addresses
 * (colons) and email addresses (arbitrary bytes).
 */
export function component(value: string): string {
  return Buffer.from(value, "utf8").toString("base64url");
}

export function createRedisClient(url: string, logger?: Logger): Redis {
  const client = new Redis(url, {
    // Lambda invocations must not hang behind a dead Redis: fail the command
    // quickly so SQS retries / the DLQ catch persistent failures.
    maxRetriesPerRequest: 2,
    connectTimeout: 5_000,
    commandTimeout: 5_000,
    enableOfflineQueue: true,
    retryStrategy: (times) => Math.min(times * 200, 2_000),
  });
  client.on("error", (error) => {
    logger?.error("Redis error", { error });
  });
  return client;
}
