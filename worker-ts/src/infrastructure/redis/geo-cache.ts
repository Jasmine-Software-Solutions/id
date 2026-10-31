import type Redis from "ioredis";
import type { Logger } from "../../runtime/logging";
import { locationFromJson, locationToJson, type SecurityEventLocation } from "../../wire/location";
import { fetchIpLocations } from "../http/ip-api";
import { component, redisKey } from "./client";
import type { RedisScripts } from "./scripts";

const GEO_TTL_MS = 24 * 60 * 60 * 1000;
const MAX_BATCH_SIZE = 100;
const DEFAULT_RATE_LIMIT = 14;

export interface GeoLocatorOptions {
  redis: Redis;
  scripts: RedisScripts;
  baseUrl: string;
  fetchImpl?: typeof fetch;
  logger?: Logger;
  rateLimitPerMinute?: number;
}

/**
 * Per-invocation geolocation resolver.
 *
 * The JVM worker ran a background thread with an in-memory cache; with no
 * process lifetime to rely on, the cache lives in Redis and each invocation
 * prefetches every IP its batch could need in a single batch request. Detector
 * code then reads synchronously from this invocation's map, preserving the
 * detector call shapes.
 */
export class GeoLocator {
  private readonly resolved = new Map<string, SecurityEventLocation | null>();

  constructor(private readonly options: GeoLocatorOptions) {}

  /** Resolves all previously unseen IPs; never throws for ip-api trouble. */
  async prefetch(ips: Iterable<string>): Promise<void> {
    const unique = [...new Set(ips)].filter((ip) => ip.length > 0 && !this.resolved.has(ip));
    if (unique.length === 0) return;

    const { redis } = this.options;
    const keys = unique.map((ip) => redisKey("geo", component(ip)));
    const cached = await redis.mget(...keys);

    const misses: string[] = [];
    unique.forEach((ip, index) => {
      const value = cached[index];
      if (value === null || value === undefined) {
        misses.push(ip);
      } else {
        this.resolved.set(ip, locationFromJson(value));
      }
    });

    for (let offset = 0; offset < misses.length; offset += MAX_BATCH_SIZE) {
      const chunk = misses.slice(offset, offset + MAX_BATCH_SIZE);
      await this.lookupChunk(chunk);
    }
  }

  getSync(ip: string): SecurityEventLocation | null {
    return this.resolved.get(ip) ?? null;
  }

  private async lookupChunk(ips: string[]): Promise<void> {
    const { redis, scripts, logger } = this.options;

    if (!(await this.takeRateSlot())) {
      // Over the free-tier rate limit: degrade to "unknown" for this
      // invocation without poisoning the 24h cache.
      logger?.warn("ip-api rate limit reached; geolocation degraded to null", { count: ips.length });
      for (const ip of ips) this.resolved.set(ip, null);
      return;
    }

    try {
      const locations = await fetchIpLocations(ips, {
        baseUrl: this.options.baseUrl,
        fetchImpl: this.options.fetchImpl,
      });
      await this.writeCache(ips, locations);
      for (const ip of ips) this.resolved.set(ip, locations.get(ip) ?? null);
    } catch (error) {
      // A failed batch is cached as null for 24h, matching the JVM service's
      // negative caching so one broken minute cannot hammer ip-api.
      logger?.warn("ip-api batch lookup failed; caching null responses", { error });
      await this.writeCache(ips, new Map(ips.map((ip) => [ip, null])));
      for (const ip of ips) this.resolved.set(ip, null);
    }
  }

  private async writeCache(
    ips: string[],
    locations: Map<string, SecurityEventLocation | null>,
  ): Promise<void> {
    const pipeline = this.options.redis.pipeline();
    for (const ip of ips) {
      pipeline.set(
        redisKey("geo", component(ip)),
        locationToJson(locations.get(ip) ?? null),
        "PX",
        GEO_TTL_MS,
      );
    }
    await pipeline.exec();
  }

  private async takeRateSlot(): Promise<boolean> {
    const minute = Math.floor(Date.now() / 60_000);
    const count = await this.options.scripts.rateLimit(redisKey("geo", "rl", String(minute)), 120_000);
    return count <= (this.options.rateLimitPerMinute ?? DEFAULT_RATE_LIMIT);
  }
}
