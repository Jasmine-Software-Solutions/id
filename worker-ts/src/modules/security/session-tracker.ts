import type { DetectorStore } from "../../infrastructure/redis/windows";
import { createLogger, type Logger } from "../../runtime/logging";
import { requiredString, type Wire } from "../../wire/codec";
import { EventTypes } from "../../wire/event-types";
import { ID_SERVICE, systemEvent, type EventDispatcher } from "../../wire/publisher";

export const MAX_CONCURRENT_SESSIONS = 10;

/**
 * Tracks active session counts per account in Redis and raises
 * ConcurrentSessionLimitExceededSecurityEvent past the limit.
 *
 * The JVM kept this in a ConcurrentHashMap, so a restart reset every counter;
 * the Redis counter (30-day TTL) now survives a frozen Lambda environment and
 * is shared by every concurrent invocation.
 */
export class SessionTracker {
  private readonly logger: Logger;

  constructor(
    private readonly store: DetectorStore,
    private readonly dispatcher: EventDispatcher,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("security.sessions");
  }

  async handleSessionCreated(body: Wire): Promise<void> {
    const accountId = requiredString(body, "account_id");
    const count = await this.store.bumpSessionCount(accountId);

    if (count > MAX_CONCURRENT_SESSIONS) {
      this.logger.warn("Concurrent session limit exceeded", {
        account_id: accountId,
        sessions: count,
      });

      await this.dispatcher.dispatch(
        systemEvent(EventTypes.ConcurrentSessionLimitExceeded, {
          action: `${ID_SERVICE}:ConcurrentSessionLimitExceeded`,
          accountId,
          activeSessionCount: count,
          limit: MAX_CONCURRENT_SESSIONS,
        }),
      );
    }
  }

  async handleSessionRevoked(body: Wire): Promise<void> {
    await this.store.decrementSessionCount(requiredString(body, "account_id"));
  }

  async handleAllSessionsRevoked(body: Wire): Promise<void> {
    await this.store.resetSessionCount(requiredString(body, "account_id"));
  }

  async removeAccount(accountId: string): Promise<void> {
    await this.store.removeSessionCount(accountId);
  }
}
