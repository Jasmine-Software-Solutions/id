import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { createRedisClient, redisKey } from "../../src/infrastructure/redis/client";
import { RedisScripts } from "../../src/infrastructure/redis/scripts";
import { DetectorStore } from "../../src/infrastructure/redis/windows";
import { GeoLocator } from "../../src/infrastructure/redis/geo-cache";
import type Redis from "ioredis";

/**
 * These run against the dev-stack Redis (localhost:6379). Set
 * REDIS_TEST_URL to point elsewhere, or SKIP_INTEGRATION=1 to skip.
 */
const url = process.env.REDIS_TEST_URL ?? "redis://localhost:6379";

async function available(): Promise<boolean> {
  if (process.env.SKIP_INTEGRATION === "1") return false;
  const probe = createRedisClient(url);
  try {
    await probe.ping();
    return true;
  } catch {
    return false;
  } finally {
    probe.disconnect();
  }
}

const canRun = await available();
const runId = randomUUID().slice(0, 8);
const created: string[] = [];

describe.skipIf(!canRun)("Redis detector state", () => {
  let redis: Redis;
  let scripts: RedisScripts;
  let store: DetectorStore;

  beforeAll(() => {
    redis = createRedisClient(url);
    scripts = new RedisScripts(redis);
    store = new DetectorStore(redis, scripts);
  });

  afterAll(async () => {
    if (created.length > 0) await redis.del(...created);
    redis.disconnect();
  });

  it("fires a password window exactly once at the threshold", async () => {
    const email = `threshold-${runId}@example.com`;
    const ip = `203.0.113.${Math.floor(Math.random() * 200) + 1}`;
    created.push(redisKey("bf", "pw", Buffer.from(email).toString("base64url"), Buffer.from(ip).toString("base64url")));

    const counts: number[] = [];
    for (let i = 0; i < 6; i += 1) {
      counts.push(await store.recordPasswordFailure(email, ip, undefined, 5));
    }

    // 1..5 then a fresh window; the fifth call deleted the key so no later
    // caller can double-fire on the same window.
    expect(counts).toEqual([1, 2, 3, 4, 5, 1]);
  });

  it("fires once under concurrent threshold calls", async () => {
    const email = `concurrent-${runId}@example.com`;
    const ip = "198.51.100.77";
    const key = redisKey(
      "bf",
      "pw",
      Buffer.from(email).toString("base64url"),
      Buffer.from(ip).toString("base64url"),
    );
    created.push(key);

    const counts = await Promise.all(
      Array.from({ length: 5 }, () => store.recordPasswordFailure(email, ip, undefined, 5)),
    );

    expect(counts.filter((count) => count >= 5)).toHaveLength(1);
    expect(await redis.exists(key)).toBe(0);
  });

  it("applies a TTL to windows and indexes, and clears by account", async () => {
    const accountId = randomUUID();
    const email = `index-${runId}@example.com`;
    const ip = "203.0.113.90";
    const windowKey = redisKey(
      "bf",
      "pw",
      Buffer.from(email).toString("base64url"),
      Buffer.from(ip).toString("base64url"),
    );
    const indexKey = redisKey("bf", "pw", "idx", accountId);
    created.push(windowKey, indexKey);

    await store.recordPasswordFailure(email, ip, accountId, 100);

    const ttl = await redis.pttl(windowKey);
    expect(ttl).toBeGreaterThan(0);
    expect(ttl).toBeLessThanOrEqual(10 * 60 * 1000);
    expect(await redis.sismember(indexKey, windowKey)).toBe(1);

    await store.clearForAccount(accountId);
    expect(await redis.exists(windowKey, indexKey)).toBe(0);
  });

  it("keeps session counters at or above zero", async () => {
    const accountId = randomUUID();
    created.push(redisKey("sess", accountId));

    expect(await store.decrementSessionCount(accountId)).toBe(0);
    expect(await store.bumpSessionCount(accountId)).toBe(1);
    expect(await store.decrementSessionCount(accountId)).toBe(0);
    expect(await store.decrementSessionCount(accountId)).toBe(0);
  });

  it("stores last-login records with CAS by timestamp", async () => {
    const accountId = randomUUID();
    created.push(redisKey("it", "last", accountId));

    const now = Date.now();
    await store.setLastLogin(accountId, {
      sessionId: "new",
      ip: "203.0.113.1",
      location: null,
      timestamp: now,
    });
    const moved = await store.setLastLogin(accountId, {
      sessionId: "old",
      ip: "198.51.100.1",
      location: null,
      timestamp: now - 10_000,
    });

    expect(moved).toBe(false);
    expect((await store.getLastLogin(accountId))!.sessionId).toBe("new");
    const ttl = await redis.pttl(redisKey("it", "last", accountId));
    expect(ttl).toBeGreaterThan(0);
    expect(ttl).toBeLessThanOrEqual(90 * 24 * 60 * 60 * 1000);
  });

  it("caches geolocation, honours negatives, and rate-limits lookups", async () => {
    const calls: string[][] = [];
    const fetchImpl = (async (_url: string, init: RequestInit) => {
      const body = JSON.parse(String(init.body)) as { query: string }[];
      calls.push(body.map((entry) => entry.query));
      return new Response(
        JSON.stringify(
          body.map((entry) => ({
            status: entry.query.startsWith("203.") ? "success" : "fail",
            country: "United States",
            city: "New York",
            lat: 40.7,
            lon: -74,
            query: entry.query,
            message: "reserved range",
          })),
        ),
        { status: 200 },
      );
    }) as unknown as typeof fetch;

    const ip = `203.0.113.${Math.floor(Math.random() * 200) + 1}`;
    const unknownIp = `198.51.100.${Math.floor(Math.random() * 200) + 1}`;
    created.push(redisKey("geo", Buffer.from(ip).toString("base64url")));
    created.push(redisKey("geo", Buffer.from(unknownIp).toString("base64url")));

    const first = new GeoLocator({ redis, scripts, baseUrl: "http://ip-api.test", fetchImpl });
    await first.prefetch([ip, unknownIp]);
    expect(calls).toHaveLength(1);
    expect(first.getSync(ip)).toMatchObject({ country: "United States", city: "New York" });
    expect(first.getSync(unknownIp)).toBeNull();

    // A second invocation reads the Redis cache instead of calling ip-api.
    const second = new GeoLocator({ redis, scripts, baseUrl: "http://ip-api.test", fetchImpl });
    await second.prefetch([ip, unknownIp]);
    expect(calls).toHaveLength(1);
    expect(second.getSync(ip)).toMatchObject({ country: "United States" });

    expect(await redis.pttl(redisKey("geo", Buffer.from(ip).toString("base64url")))).toBeGreaterThan(0);
  });

  it("stops issuing ip-api batches past the per-minute limit", async () => {
    const minute = Math.floor(Date.now() / 60_000);
    const bucket = redisKey("geo", "rl", String(minute));
    created.push(bucket);
    await redis.del(bucket);

    let calls = 0;
    const fetchImpl = (async () =>
      new Response("[]", { status: 200 })) as unknown as typeof fetch;

    const geo = new GeoLocator({
      redis,
      scripts,
      baseUrl: "http://ip-api.test",
      fetchImpl: (async (...args: Parameters<typeof fetch>) => {
        calls += 1;
        return fetchImpl(...args);
      }) as unknown as typeof fetch,
      rateLimitPerMinute: 1,
    });

    const first = `203.0.113.${Math.floor(Math.random() * 100) + 1}`;
    const second = `203.0.113.${Math.floor(Math.random() * 100) + 150}`;
    created.push(redisKey("geo", Buffer.from(first).toString("base64url")));
    created.push(redisKey("geo", Buffer.from(second).toString("base64url")));

    // Two separate batches in one minute: the second is skipped.
    await geo.prefetch([first]);
    await geo.prefetch([second]);
    expect(calls).toBe(1);
  });
});
