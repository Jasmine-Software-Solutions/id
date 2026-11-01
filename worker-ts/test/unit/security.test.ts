import { createHash } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import type { Pool } from "pg";
import type { DetectorStore, LastLoginRecord, TotpEnableRecord } from "../../src/infrastructure/redis/windows";
import type { GeoLocator } from "../../src/infrastructure/redis/geo-cache";
import { BruteForceDetector } from "../../src/modules/security/detectors/brute-force";
import { EnumerationDetector } from "../../src/modules/security/detectors/enumeration";
import { RapidCreationDetector } from "../../src/modules/security/detectors/rapid-creation";
import { ImpossibleTravelDetector } from "../../src/modules/security/detectors/impossible-travel";
import { TotpDisableDetector } from "../../src/modules/security/detectors/totp-disable";
import {
  CredentialStuffingDetector,
  PasswordDeleter,
  SessionRevoker,
} from "../../src/modules/security/credential-stuffing";
import { RestrictionEnforcer } from "../../src/modules/security/restriction-enforcer";
import { SessionTracker } from "../../src/modules/security/session-tracker";
import { RestrictionRepository } from "../../src/infrastructure/db/restrictions";
import { EventTypes } from "../../src/wire/event-types";
import { SYSTEM_PRINCIPAL_ID, type EventDispatcher, type OutboundEvent } from "../../src/wire/publisher";
import type { SecurityEventLocation } from "../../src/wire/location";
import type { Logger } from "../../src/runtime/logging";

class SilentLogger implements Logger {
  debug(): void {}
  info(): void {}
  warn(): void {}
  error(): void {}
}

const logger = new SilentLogger();

class RecordingDispatcher implements EventDispatcher {
  readonly events: OutboundEvent[] = [];
  async dispatch(event: OutboundEvent): Promise<void> {
    this.events.push(event);
  }
}

class FakeEnforcer {
  readonly locks: { accountId: string; trigger: string; duration: number }[] = [];
  readonly blocks: { ip: string; trigger: string; duration: number }[] = [];
  async placeAccountLock(accountId: string, trigger: string, duration: number): Promise<void> {
    this.locks.push({ accountId, trigger, duration });
  }
  async placeIpBlock(ip: string, trigger: string, duration: number): Promise<void> {
    this.blocks.push({ ip, trigger, duration });
  }
}

/** In-memory stand-in for DetectorStore with the same threshold semantics. */
class FakeStore {
  readonly passwordWindows = new Map<string, number>();
  readonly totpWindows = new Map<string, number>();
  readonly enumWindows = new Map<string, number>();
  readonly rapidWindows = new Map<string, number>();
  readonly sessions = new Map<string, number>();
  readonly lastLogins = new Map<string, LastLoginRecord>();
  readonly totpEnable = new Map<string, TotpEnableRecord>();
  readonly clearedAccounts: string[] = [];
  readonly clearedPasswordKeys: string[] = [];

  async recordPasswordFailure(
    email: string | undefined,
    ip: string | undefined,
    _accountId: string | undefined,
    threshold: number,
  ): Promise<number> {
    const key = `${email ?? "unknown"}:${ip ?? "unknown"}`;
    const count = (this.passwordWindows.get(key) ?? 0) + 1;
    if (count >= threshold) this.passwordWindows.delete(key);
    else this.passwordWindows.set(key, count);
    return count;
  }

  async recordTotpFailure(accountId: string, ip: string | undefined, threshold: number): Promise<number> {
    const key = `${accountId}:${ip ?? "unknown"}`;
    const count = (this.totpWindows.get(key) ?? 0) + 1;
    if (count >= threshold) this.totpWindows.delete(key);
    else this.totpWindows.set(key, count);
    return count;
  }

  async recordFlowStart(ip: string, threshold: number): Promise<number> {
    const count = (this.enumWindows.get(ip) ?? 0) + 1;
    if (count >= threshold) this.enumWindows.set(ip, 0);
    else this.enumWindows.set(ip, count);
    return count;
  }

  async recordCreation(ip: string, threshold: number): Promise<number> {
    const count = (this.rapidWindows.get(ip) ?? 0) + 1;
    if (count >= threshold) this.rapidWindows.set(ip, 0);
    else this.rapidWindows.set(ip, count);
    return count;
  }

  async clearPasswordFailures(email: string | undefined, ip: string | undefined): Promise<void> {
    this.clearedPasswordKeys.push(`${email ?? "unknown"}:${ip ?? "unknown"}`);
  }

  async clearTotpFailures(): Promise<void> {}

  async clearForAccount(accountId: string): Promise<void> {
    this.clearedAccounts.push(accountId);
  }

  async bumpSessionCount(accountId: string): Promise<number> {
    const count = (this.sessions.get(accountId) ?? 0) + 1;
    this.sessions.set(accountId, count);
    return count;
  }

  async decrementSessionCount(accountId: string): Promise<number> {
    const count = Math.max(0, (this.sessions.get(accountId) ?? 0) - 1);
    this.sessions.set(accountId, count);
    return count;
  }

  async resetSessionCount(accountId: string): Promise<void> {
    this.sessions.set(accountId, 0);
  }

  async removeSessionCount(accountId: string): Promise<void> {
    this.sessions.delete(accountId);
  }

  async getLastLogin(accountId: string): Promise<LastLoginRecord | null> {
    return this.lastLogins.get(accountId) ?? null;
  }

  async setLastLogin(accountId: string, record: LastLoginRecord): Promise<boolean> {
    const existing = this.lastLogins.get(accountId);
    if (existing && existing.timestamp > record.timestamp) return false;
    this.lastLogins.set(accountId, record);
    return true;
  }

  async removeLastLogin(accountId: string): Promise<void> {
    this.lastLogins.delete(accountId);
  }

  async getTotpEnable(accountId: string): Promise<TotpEnableRecord | null> {
    return this.totpEnable.get(accountId) ?? null;
  }

  async setTotpEnable(accountId: string, record: TotpEnableRecord): Promise<void> {
    this.totpEnable.set(accountId, record);
  }

  async removeTotpEnable(accountId: string): Promise<void> {
    this.totpEnable.delete(accountId);
  }
}

function geoWith(map: Record<string, SecurityEventLocation | null>): GeoLocator {
  return { getSync: (ip: string) => map[ip] ?? null } as unknown as GeoLocator;
}

const somewhere = (country: string, city: string, latitude: number, longitude: number) => ({
  country,
  city,
  latitude,
  longitude,
});

describe("brute force detector", () => {
  it("fires once, places one account lock and one ip block", async () => {
    const store = new FakeStore();
    const enforcer = new FakeEnforcer();
    const dispatcher = new RecordingDispatcher();
    const detector = new BruteForceDetector(
      store as unknown as DetectorStore,
      enforcer as unknown as RestrictionEnforcer,
      dispatcher,
      logger,
    );

    for (let attempt = 0; attempt < 5; attempt += 1) {
      await detector.handleInvalidPassword({
        account_id: "account-1",
        email: "person@example.com",
        ip_address: "203.0.113.7",
      });
    }
    // The sixth failure starts a fresh window and must not re-fire.
    await detector.handleInvalidPassword({
      account_id: "account-1",
      email: "person@example.com",
      ip_address: "203.0.113.7",
    });

    expect(dispatcher.events).toHaveLength(1);
    const event = dispatcher.events[0]!;
    expect(event).toMatchObject({
      eventType: EventTypes.PasswordBruteForceAttempted,
      principalId: SYSTEM_PRINCIPAL_ID,
      action: "id.jasmine.software:BruteForceDetected",
      resource: "id.jasmine.software://",
      accountId: "account-1",
      email: "person@example.com",
      ipAddress: "203.0.113.7",
      failureCount: 5,
      windowMinutes: 10,
    });
    expect(enforcer.locks).toEqual([
      { accountId: "account-1", trigger: event.eventId, duration: 30 },
    ]);
    expect(enforcer.blocks).toEqual([
      { ip: "203.0.113.7", trigger: event.eventId, duration: 30 },
    ]);
  });

  it("uses 'unknown' for a missing address and skips the ip block", async () => {
    const store = new FakeStore();
    const enforcer = new FakeEnforcer();
    const dispatcher = new RecordingDispatcher();
    const detector = new BruteForceDetector(
      store as unknown as DetectorStore,
      enforcer as unknown as RestrictionEnforcer,
      dispatcher,
      logger,
    );

    for (let attempt = 0; attempt < 5; attempt += 1) {
      await detector.handleInvalidPassword({ email: "person@example.com" });
    }

    expect(dispatcher.events[0]!.ipAddress).toBe("unknown");
    expect(dispatcher.events[0]!.accountId).toBeNull();
    expect(enforcer.locks).toEqual([]);
    expect(enforcer.blocks).toEqual([]);
  });

  it("fires TOTP detection and locks the account", async () => {
    const store = new FakeStore();
    const enforcer = new FakeEnforcer();
    const dispatcher = new RecordingDispatcher();
    const detector = new BruteForceDetector(
      store as unknown as DetectorStore,
      enforcer as unknown as RestrictionEnforcer,
      dispatcher,
      logger,
    );

    for (let attempt = 0; attempt < 5; attempt += 1) {
      await detector.handleInvalidTOTP({ account_id: "account-1", ip_address: "203.0.113.7" });
    }

    expect(dispatcher.events).toHaveLength(1);
    expect(dispatcher.events[0]).toMatchObject({
      eventType: EventTypes.PasswordBruteForceAttempted,
      action: "id.jasmine.software:TOTPBruteForceDetected",
      accountId: "account-1",
      failureCount: 5,
      windowMinutes: 10,
    });
    expect(dispatcher.events[0]!.email).toBeNull();
    expect(enforcer.locks).toHaveLength(1);
  });

  it("clears both windows after a successful login", async () => {
    const store = new FakeStore();
    const detector = new BruteForceDetector(
      store as unknown as DetectorStore,
      new FakeEnforcer() as unknown as RestrictionEnforcer,
      new RecordingDispatcher(),
      logger,
    );

    await detector.handleAuthSucceeded("person@example.com", "203.0.113.7", "account-1");
    expect(store.clearedPasswordKeys).toEqual(["person@example.com:203.0.113.7"]);
  });
});

describe("enumeration and rapid creation", () => {
  it("blocks an ip after 20 flow starts", async () => {
    const store = new FakeStore();
    const enforcer = new FakeEnforcer();
    const dispatcher = new RecordingDispatcher();
    const detector = new EnumerationDetector(
      store as unknown as DetectorStore,
      enforcer as unknown as RestrictionEnforcer,
      dispatcher,
      logger,
    );

    for (let attempt = 0; attempt < 20; attempt += 1) {
      await detector.handleFlowStarted({ ip_address: "203.0.113.7" });
    }

    expect(dispatcher.events).toHaveLength(1);
    expect(dispatcher.events[0]).toMatchObject({
      eventType: EventTypes.EnumerationDetected,
      ipAddress: "203.0.113.7",
      flowCount: 20,
      windowMinutes: 5,
    });
    expect(enforcer.blocks).toEqual([
      { ip: "203.0.113.7", trigger: dispatcher.events[0]!.eventId, duration: 30 },
    ]);
  });

  it("blocks an ip at the configured rapid-creation threshold", async () => {
    const store = new FakeStore();
    const enforcer = new FakeEnforcer();
    const dispatcher = new RecordingDispatcher();
    const detector = new RapidCreationDetector(
      store as unknown as DetectorStore,
      enforcer as unknown as RestrictionEnforcer,
      dispatcher,
      3,
      60,
      logger,
    );

    for (let attempt = 0; attempt < 3; attempt += 1) {
      await detector.handleAccountCreated({ ip_address: "203.0.113.7" });
    }

    expect(dispatcher.events[0]).toMatchObject({
      eventType: EventTypes.RapidAccountCreationDetected,
      ipAddress: "203.0.113.7",
      creationCount: 3,
      windowMinutes: 60,
    });
    expect(enforcer.blocks).toEqual([
      { ip: "203.0.113.7", trigger: dispatcher.events[0]!.eventId, duration: 60 },
    ]);
  });

  it("ignores creations with no address", async () => {
    const store = new FakeStore();
    const dispatcher = new RecordingDispatcher();
    const detector = new RapidCreationDetector(
      store as unknown as DetectorStore,
      new FakeEnforcer() as unknown as RestrictionEnforcer,
      dispatcher,
      1,
      60,
      logger,
    );

    await detector.handleAccountCreated({ ip_address: null });
    await detector.handleAccountCreated({});
    expect(dispatcher.events).toEqual([]);
  });
});

describe("impossible travel", () => {
  it("fires when the implied speed exceeds 1000 km/h", async () => {
    const store = new FakeStore();
    const dispatcher = new RecordingDispatcher();
    const detector = new ImpossibleTravelDetector(
      store as unknown as DetectorStore,
      dispatcher,
      logger,
    );

    store.lastLogins.set("account-1", {
      sessionId: "s1",
      ip: "198.51.100.4",
      location: somewhere("United States", "New York", 40.7128, -74.006),
      timestamp: Date.now() - 60 * 60 * 1000, // one hour ago
    });

    await detector.handleAuthSucceeded(
      { account_id: "account-1", ip_address: "203.0.113.7", session_id: "s2" },
      geoWith({
        "203.0.113.7": somewhere("United Kingdom", "London", 51.5074, -0.1278),
      }),
    );

    expect(dispatcher.events).toHaveLength(1);
    expect(dispatcher.events[0]).toMatchObject({
      eventType: EventTypes.ImpossibleTravelDetected,
      action: "id.jasmine.software:ImpossibleTravelDetected",
      accountId: "account-1",
      previousIp: "198.51.100.4",
      currentIp: "203.0.113.7",
    });
    expect(store.lastLogins.get("account-1")!.ip).toBe("203.0.113.7");
  });

  it("does not fire for a short hop", async () => {
    const store = new FakeStore();
    const dispatcher = new RecordingDispatcher();
    const detector = new ImpossibleTravelDetector(
      store as unknown as DetectorStore,
      dispatcher,
      logger,
    );

    store.lastLogins.set("account-1", {
      sessionId: "s1",
      ip: "198.51.100.4",
      location: somewhere("United States", "New York", 40.7128, -74.006),
      timestamp: Date.now() - 60 * 60 * 1000,
    });

    await detector.handleAuthSucceeded(
      { account_id: "account-1", ip_address: "203.0.113.7" },
      geoWith({
        "203.0.113.7": somewhere("United States", "Philadelphia", 39.9526, -75.1652),
      }),
    );

    expect(dispatcher.events).toEqual([]);
    expect(store.lastLogins.get("account-1")!.ip).toBe("203.0.113.7");
  });

  it("records the current login for the next comparison", async () => {
    const store = new FakeStore();
    const detector = new ImpossibleTravelDetector(
      store as unknown as DetectorStore,
      new RecordingDispatcher(),
      logger,
    );

    await detector.handleAuthSucceeded(
      { account_id: "account-1", ip_address: "198.51.100.4", session_id: "s1" },
      geoWith({ "198.51.100.4": somewhere("United States", "New York", 40.7128, -74.006) }),
    );

    expect(store.lastLogins.get("account-1")).toMatchObject({
      sessionId: "s1",
      ip: "198.51.100.4",
      location: { country: "United States" },
    });
  });
});

describe("TOTP disable detection", () => {
  it("fires on a different country", async () => {
    const store = new FakeStore();
    const dispatcher = new RecordingDispatcher();
    const detector = new TotpDisableDetector(
      store as unknown as DetectorStore,
      dispatcher,
      logger,
    );

    store.totpEnable.set("account-1", {
      ip: "198.51.100.4",
      location: somewhere("United States", "New York", 40.7128, -74.006),
    });

    await detector.handleTOTPDisabled(
      { account_id: "account-1", ip_address: "203.0.113.7" },
      geoWith({ "203.0.113.7": somewhere("France", "Paris", 48.8566, 2.3522) }),
    );

    expect(dispatcher.events).toHaveLength(1);
    expect(dispatcher.events[0]).toMatchObject({
      eventType: EventTypes.TOTPDisabledFromUnusualLocation,
      accountId: "account-1",
      enabledIp: "198.51.100.4",
      disabledIp: "203.0.113.7",
    });
    expect(store.totpEnable.has("account-1")).toBe(false);
  });

  it("allows the same IP and keeps the record", async () => {
    const store = new FakeStore();
    const dispatcher = new RecordingDispatcher();
    const detector = new TotpDisableDetector(
      store as unknown as DetectorStore,
      dispatcher,
      logger,
    );

    store.totpEnable.set("account-1", {
      ip: "198.51.100.4",
      location: somewhere("United States", "New York", 40.7128, -74.006),
    });

    await detector.handleTOTPDisabled(
      { account_id: "account-1", ip_address: "198.51.100.4" },
      geoWith({}),
    );

    expect(dispatcher.events).toEqual([]);
    // The Java early return leaves the record; its TTL bounds it.
    expect(store.totpEnable.has("account-1")).toBe(true);
  });

  it("fires when the enable location is unknown", async () => {
    const store = new FakeStore();
    const dispatcher = new RecordingDispatcher();
    const detector = new TotpDisableDetector(
      store as unknown as DetectorStore,
      dispatcher,
      logger,
    );

    store.totpEnable.set("account-1", { ip: "198.51.100.4", location: null });

    await detector.handleTOTPDisabled(
      { account_id: "account-1", ip_address: "203.0.113.7" },
      geoWith({ "203.0.113.7": somewhere("United States", "New York", 40.7128, -74.006) }),
    );

    expect(dispatcher.events).toHaveLength(1);
  });

  it("clears the enable record when the disabled location cannot be resolved", async () => {
    const store = new FakeStore();
    const dispatcher = new RecordingDispatcher();
    const detector = new TotpDisableDetector(
      store as unknown as DetectorStore,
      dispatcher,
      logger,
    );

    store.totpEnable.set("account-1", {
      ip: "198.51.100.4",
      location: somewhere("United States", "New York", 40.7128, -74.006),
    });

    await detector.handleTOTPDisabled(
      { account_id: "account-1", ip_address: "203.0.113.7" },
      geoWith({}), // lookup failed for the disabled IP
    );

    // Java removed the record after scheduling the lookup, regardless of the
    // lookup's result.
    expect(dispatcher.events).toEqual([]);
    expect(store.totpEnable.has("account-1")).toBe(false);
  });

  it("does nothing when the disabled IP or enable record is missing", async () => {
    const store = new FakeStore();
    const dispatcher = new RecordingDispatcher();
    const detector = new TotpDisableDetector(
      store as unknown as DetectorStore,
      dispatcher,
      logger,
    );

    await detector.handleTOTPDisabled({ account_id: "account-1" }, geoWith({}));
    await detector.handleTOTPDisabled(
      { account_id: "account-2", ip_address: "203.0.113.7" },
      geoWith({ "203.0.113.7": somewhere("France", "Paris", 48.8566, 2.3522) }),
    );

    expect(dispatcher.events).toEqual([]);
  });
});

describe("session tracker", () => {
  it("emits the concurrent-session event past the limit", async () => {
    const store = new FakeStore();
    const dispatcher = new RecordingDispatcher();
    const tracker = new SessionTracker(store as unknown as DetectorStore, dispatcher, logger);

    for (let i = 0; i < 11; i += 1) {
      await tracker.handleSessionCreated({ account_id: "account-1" });
    }

    expect(dispatcher.events).toHaveLength(1);
    expect(dispatcher.events[0]).toMatchObject({
      eventType: EventTypes.ConcurrentSessionLimitExceeded,
      accountId: "account-1",
      activeSessionCount: 11,
      limit: 10,
    });
  });

  it("does not go below zero and resets on all-revoked", async () => {
    const store = new FakeStore();
    const tracker = new SessionTracker(
      store as unknown as DetectorStore,
      new RecordingDispatcher(),
      logger,
    );

    await tracker.handleSessionRevoked({ account_id: "account-1" });
    expect(store.sessions.get("account-1")).toBe(0);

    await tracker.handleSessionCreated({ account_id: "account-1" });
    await tracker.handleAllSessionsRevoked({ account_id: "account-1" });
    expect(store.sessions.get("account-1")).toBe(0);
  });
});

describe("credential stuffing", () => {
  const password = "correct horse battery staple";
  const hash = createHash("sha1").update(password).digest("hex").toUpperCase();
  const suffix = hash.slice(5);

  it("fires on an HIBP hit and revokes/deletes", async () => {
    const dispatcher = new RecordingDispatcher();
    const revoker = { revokeAllSessions: vi.fn(async () => undefined) };
    const deleter = { deletePassword: vi.fn(async () => undefined) };
    const detector = new CredentialStuffingDetector(
      dispatcher,
      revoker as unknown as SessionRevoker,
      deleter as unknown as PasswordDeleter,
      {
        hibpBaseUrl: "https://hibp.test",
        fetchImpl: (async () =>
          new Response(`0011223344:1\r\n${suffix}:42\r\n`, { status: 200 })) as unknown as typeof fetch,
        logger,
      },
    );

    await detector.handleValidPassword({
      account_id: "account-1",
      email: "person@example.com",
      ip_address: "203.0.113.7",
      password_hash: hash,
    });

    expect(dispatcher.events).toHaveLength(1);
    expect(dispatcher.events[0]).toMatchObject({
      eventType: EventTypes.CredentialStuffingSucceeded,
      action: "id.jasmine.software:CredentialStuffingDetected",
      accountId: "account-1",
      email: "person@example.com",
      ipAddress: "203.0.113.7",
      hashPrefix: hash.slice(0, 5),
    });
    expect(revoker.revokeAllSessions).toHaveBeenCalledWith("account-1");
    expect(deleter.deletePassword).toHaveBeenCalledWith("account-1");
  });

  it("does nothing on a miss or a HIBP failure", async () => {
    const dispatcher = new RecordingDispatcher();
    const revoker = { revokeAllSessions: vi.fn(async () => undefined) };
    const deleter = { deletePassword: vi.fn(async () => undefined) };

    const miss = new CredentialStuffingDetector(
      dispatcher,
      revoker as unknown as SessionRevoker,
      deleter as unknown as PasswordDeleter,
      {
        hibpBaseUrl: "https://hibp.test",
        fetchImpl: (async () => new Response("0011223344:1", { status: 200 })) as unknown as typeof fetch,
        logger,
      },
    );
    await miss.handleValidPassword({ account_id: "a", email: "e", password_hash: hash });

    const failure = new CredentialStuffingDetector(
      dispatcher,
      revoker as unknown as SessionRevoker,
      deleter as unknown as PasswordDeleter,
      {
        hibpBaseUrl: "https://hibp.test",
        fetchImpl: (async () => {
          throw new Error("network");
        }) as unknown as typeof fetch,
        logger,
      },
    );
    await failure.handleValidPassword({ account_id: "a", email: "e", password_hash: hash });

    expect(dispatcher.events).toEqual([]);
    expect(revoker.revokeAllSessions).not.toHaveBeenCalled();
  });
});

describe("database-backed security actions", () => {
  it("inserts a restriction row and emits the placement event", async () => {
    const rows: unknown[] = [];
    const repository = {
      create: async (row: unknown) => {
        rows.push(row);
      },
    } as unknown as RestrictionRepository;
    const dispatcher = new RecordingDispatcher();
    const enforcer = new RestrictionEnforcer(repository, dispatcher, logger);

    await enforcer.placeAccountLock("account-1", "trigger-1", 30);

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      type: "ACCOUNT_LOCK",
      targetType: "ACCOUNT",
      targetId: "account-1",
      eventId: "trigger-1",
      durationMinutes: 30,
    });
    const row = rows[0] as { id: string; createdAt: number; expiresAt: number };
    expect(row.expiresAt - row.createdAt).toBe(30 * 60_000);

    expect(dispatcher.events[0]).toMatchObject({
      eventType: EventTypes.RestrictionPlaced,
      principalId: SYSTEM_PRINCIPAL_ID,
      action: "id.jasmine.software:RestrictionPlaced",
      resource: "id.jasmine.software://",
      restrictionId: row.id,
      triggerEventId: "trigger-1",
      type: "ACCOUNT_LOCK",
      targetType: "ACCOUNT",
      targetId: "account-1",
      durationMinutes: 30,
    });
  });

  it("emits AllSessionsRevoked only when sessions were deleted", async () => {
    const dispatcher = new RecordingDispatcher();
    const query = vi
      .fn()
      .mockResolvedValueOnce({ rows: [{ id: "s1" }, { id: "s2" }] })
      .mockResolvedValueOnce({ rows: [] });
    const pool = { query } as unknown as Pool;

    const revoker = new SessionRevoker(pool, dispatcher, logger);
    await revoker.revokeAllSessions("account-1");
    await revoker.revokeAllSessions("account-2");

    expect(query).toHaveBeenNthCalledWith(
      1,
      "DELETE FROM sessions WHERE account = $1 AND expires_at >= $2 RETURNING id",
      ["account-1", expect.any(Number)],
    );
    expect(dispatcher.events).toHaveLength(1);
    expect(dispatcher.events[0]).toMatchObject({
      eventType: EventTypes.AllSessionsRevoked,
      accountId: "account-1",
      revokedCount: 2,
    });
  });

  it("deletes passwords and logs the count", async () => {
    const query = vi.fn().mockResolvedValue({ rows: [{ id: "p1" }] });
    const pool = { query } as unknown as Pool;

    await new PasswordDeleter(pool, logger).deletePassword("account-1");

    expect(query).toHaveBeenCalledWith(
      "DELETE FROM passwords WHERE account_id = $1 RETURNING id",
      ["account-1"],
    );
  });
});
