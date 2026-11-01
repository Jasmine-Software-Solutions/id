import { randomUUID } from "node:crypto";
import type Redis from "ioredis";
import { component, redisKey } from "./client";
import type { RedisScripts } from "./scripts";
import type { SecurityEventLocation } from "../../wire/location";
import { toLocation } from "../../wire/location";

export const PASSWORD_WINDOW_MS = 10 * 60 * 1000;
export const TOTP_WINDOW_MS = 10 * 60 * 1000;
export const ENUMERATION_WINDOW_MS = 5 * 60 * 1000;
export const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
export const RECORD_TTL_MS = 90 * 24 * 60 * 60 * 1000;

export interface LastLoginRecord {
  sessionId: string;
  ip: string;
  location: SecurityEventLocation | null;
  /** Epoch milliseconds. */
  timestamp: number;
}

export interface TotpEnableRecord {
  ip: string;
  location: SecurityEventLocation | null;
}

export function passwordFailureKey(email: string | undefined, ip: string | undefined): string {
  return redisKey("bf", "pw", component(email ?? "unknown"), component(ip ?? "unknown"));
}

export function totpFailureKey(accountId: string, ip: string | undefined): string {
  return redisKey("bf", "totp", accountId, component(ip ?? "unknown"));
}

/**
 * Detector state moved from JVM maps to Redis so multiple Lambda environments
 * share one view of a sliding window. Keys follow the plan's table; the two
 * index sets are an implementation detail that makes `clearForAccount` work
 * without SCAN (the Java map could iterate its keys in memory).
 */
export class DetectorStore {
  constructor(
    private readonly redis: Redis,
    private readonly scripts: RedisScripts,
  ) {}

  async recordPasswordFailure(
    email: string | undefined,
    ip: string | undefined,
    accountId: string | undefined,
    threshold: number,
  ): Promise<number> {
    const key = passwordFailureKey(email, ip);
    const count = await this.scripts.slideWindow(key, uniqueMember(), PASSWORD_WINDOW_MS, threshold);
    if (accountId) {
      await this.rememberIndex(redisKey("bf", "pw", "idx", accountId), key, PASSWORD_WINDOW_MS);
    }
    return count;
  }

  async recordTotpFailure(
    accountId: string,
    ip: string | undefined,
    threshold: number,
  ): Promise<number> {
    const key = totpFailureKey(accountId, ip);
    const count = await this.scripts.slideWindow(key, uniqueMember(), TOTP_WINDOW_MS, threshold);
    await this.rememberIndex(redisKey("bf", "totp", "idx", accountId), key, TOTP_WINDOW_MS);
    return count;
  }

  async recordFlowStart(ip: string, threshold: number): Promise<number> {
    return this.scripts.slideWindow(
      redisKey("enum", component(ip)),
      uniqueMember(),
      ENUMERATION_WINDOW_MS,
      threshold,
    );
  }

  async recordCreation(ip: string, threshold: number, windowMinutes: number): Promise<number> {
    return this.scripts.slideWindow(
      redisKey("rapid", component(ip)),
      uniqueMember(),
      windowMinutes * 60 * 1000,
      threshold,
    );
  }

  /** Reset after a successful login: email/ip (password) and account/ip (TOTP). */
  async clearPasswordFailures(email: string | undefined, ip: string | undefined): Promise<void> {
    await this.redis.del(passwordFailureKey(email, ip));
  }

  async clearTotpFailures(accountId: string, ip: string | undefined): Promise<void> {
    await this.redis.del(totpFailureKey(accountId, ip));
  }

  /** Clear every window tied to an account (password change / email change). */
  async clearForAccount(accountId: string): Promise<void> {
    await this.scripts.clearIndex(redisKey("bf", "pw", "idx", accountId));
    await this.scripts.clearIndex(redisKey("bf", "totp", "idx", accountId));
  }

  async bumpSessionCount(accountId: string): Promise<number> {
    return this.scripts.incrementCounter(redisKey("sess", accountId), 1, SESSION_TTL_MS);
  }

  async decrementSessionCount(accountId: string): Promise<number> {
    return this.scripts.incrementCounter(redisKey("sess", accountId), -1, SESSION_TTL_MS);
  }

  async resetSessionCount(accountId: string): Promise<void> {
    await this.redis.set(redisKey("sess", accountId), "0", "PX", SESSION_TTL_MS);
  }

  async removeSessionCount(accountId: string): Promise<void> {
    await this.redis.del(redisKey("sess", accountId));
  }

  async getLastLogin(accountId: string): Promise<LastLoginRecord | null> {
    const json = await this.redis.get(redisKey("it", "last", accountId));
    if (!json) return null;
    try {
      const parsed = JSON.parse(json) as Record<string, unknown>;
      if (typeof parsed.ip !== "string" || typeof parsed.timestamp !== "number") return null;
      return {
        sessionId: typeof parsed.sessionId === "string" ? parsed.sessionId : "",
        ip: parsed.ip,
        location: toLocation(parsed.location),
        timestamp: parsed.timestamp,
      };
    } catch {
      return null;
    }
  }

  /** Deterministic CAS by stored timestamp — concurrent invocations cannot
   * move the record backwards. */
  async setLastLogin(accountId: string, record: LastLoginRecord): Promise<boolean> {
    return this.scripts.setIfNewer(
      redisKey("it", "last", accountId),
      JSON.stringify(record),
      record.timestamp,
      RECORD_TTL_MS,
    );
  }

  async removeLastLogin(accountId: string): Promise<void> {
    await this.redis.del(redisKey("it", "last", accountId));
  }

  async getTotpEnable(accountId: string): Promise<TotpEnableRecord | null> {
    const json = await this.redis.get(redisKey("totp", "enable", accountId));
    if (!json) return null;
    try {
      const parsed = JSON.parse(json) as Record<string, unknown>;
      if (typeof parsed.ip !== "string") return null;
      return {
        ip: parsed.ip,
        location: toLocation(parsed.location),
      };
    } catch {
      return null;
    }
  }

  async setTotpEnable(accountId: string, record: TotpEnableRecord): Promise<void> {
    await this.redis.set(
      redisKey("totp", "enable", accountId),
      JSON.stringify(record),
      "PX",
      RECORD_TTL_MS,
    );
  }

  async removeTotpEnable(accountId: string): Promise<void> {
    await this.redis.del(redisKey("totp", "enable", accountId));
  }

  private async rememberIndex(indexKey: string, member: string, ttlMs: number): Promise<void> {
    await this.redis
      .multi()
      .sadd(indexKey, member)
      .pexpire(indexKey, ttlMs)
      .exec();
  }
}

/** Unique ZSET member; the score is Redis time, the member only needs distinctness. */
function uniqueMember(): string {
  return `${Date.now()}:${randomUUID()}`;
}
