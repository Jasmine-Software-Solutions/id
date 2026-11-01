import { randomUUID } from "node:crypto";
import type { DetectorStore } from "../../../infrastructure/redis/windows";
import { createLogger, type Logger } from "../../../runtime/logging";
import { asString, requiredString, type Wire } from "../../../wire/codec";
import { EventTypes } from "../../../wire/event-types";
import { ID_SERVICE, systemEvent, type EventDispatcher } from "../../../wire/publisher";
import type { RestrictionEnforcer } from "../restriction-enforcer";

export const PASSWORD_THRESHOLD = 5;
export const PASSWORD_WINDOW_MINUTES = 10;
export const TOTP_THRESHOLD = 5;
export const TOTP_WINDOW_MINUTES = 10;
export const LOCK_DURATION_MINUTES = 30;

/**
 * Brute-force detection for password and TOTP authentication.
 *
 * Sliding windows live in Redis (`bf:pw:{email}:{ip}`, `bf:totp:{account}:{ip}`);
 * the threshold call emits the security event and places the account lock and
 * IP block. Redis errors propagate so SQS retries (and the DLQ catches a
 * persistent outage) instead of silently dropping the failure count.
 */
export class BruteForceDetector {
  private readonly logger: Logger;

  constructor(
    private readonly store: DetectorStore,
    private readonly enforcer: RestrictionEnforcer,
    private readonly dispatcher: EventDispatcher,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("security.brute-force");
  }

  async handleInvalidPassword(body: Wire): Promise<void> {
    const email = asString(body.email);
    const ip = asString(body.ip_address);
    const accountId = asString(body.account_id);

    const count = await this.store.recordPasswordFailure(email, ip, accountId, PASSWORD_THRESHOLD);
    if (count < PASSWORD_THRESHOLD) return;

    this.logger.warn("Brute force detected", {
      email: email ?? "unknown",
      ip_address: ip ?? "unknown",
      failures: count,
    });

    const eventId = randomUUID();
    await this.dispatcher.dispatch(
      systemEvent(EventTypes.PasswordBruteForceAttempted, {
        eventId,
        action: `${ID_SERVICE}:BruteForceDetected`,
        accountId: accountId ?? null,
        email: email ?? null,
        ipAddress: ip ?? "unknown",
        failureCount: count,
        windowMinutes: PASSWORD_WINDOW_MINUTES,
      }),
    );

    if (accountId) {
      await this.enforcer.placeAccountLock(accountId, eventId, LOCK_DURATION_MINUTES);
    }
    if (ip) {
      await this.enforcer.placeIpBlock(ip, eventId, LOCK_DURATION_MINUTES);
    }
  }

  async handleInvalidTOTP(body: Wire): Promise<void> {
    const accountId = requiredString(body, "account_id");
    const ip = asString(body.ip_address);

    const count = await this.store.recordTotpFailure(accountId, ip, TOTP_THRESHOLD);
    if (count < TOTP_THRESHOLD) return;

    this.logger.warn("TOTP brute force detected", {
      account_id: accountId,
      ip_address: ip ?? "unknown",
      failures: count,
    });

    const eventId = randomUUID();
    await this.dispatcher.dispatch(
      systemEvent(EventTypes.PasswordBruteForceAttempted, {
        eventId,
        action: `${ID_SERVICE}:TOTPBruteForceDetected`,
        accountId,
        email: null,
        ipAddress: ip ?? "unknown",
        failureCount: count,
        windowMinutes: TOTP_WINDOW_MINUTES,
      }),
    );

    await this.enforcer.placeAccountLock(accountId, eventId, LOCK_DURATION_MINUTES);
  }

  /** Reset counters after a successful login for this email/ip and account/ip. */
  async handleAuthSucceeded(email: string, ip: string, accountId: string): Promise<void> {
    await this.store.clearPasswordFailures(email, ip);
    await this.store.clearTotpFailures(accountId, ip);
  }

  /** Clear counters tied to an account (password change, email change). */
  async clearForAccount(accountId: string): Promise<void> {
    await this.store.clearForAccount(accountId);
  }
}
