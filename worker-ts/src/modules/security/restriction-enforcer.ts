import { randomUUID } from "node:crypto";
import type { RestrictionRepository } from "../../infrastructure/db/restrictions";
import { createLogger, type Logger } from "../../runtime/logging";
import { EventTypes } from "../../wire/event-types";
import { ID_SERVICE, systemEvent, type EventDispatcher } from "../../wire/publisher";

/**
 * Places restrictions directly in the shared id database and emits
 * RestrictionPlacedEvent — the port of RestrictionEnforcer. Failures are
 * logged, never thrown: a detector that fired must not retry forever because
 * the restriction insert failed.
 */
export class RestrictionEnforcer {
  private readonly logger: Logger;

  constructor(
    private readonly repository: RestrictionRepository,
    private readonly dispatcher: EventDispatcher,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("security.restriction");
  }

  async placeAccountLock(accountId: string, triggerEventId: string, durationMinutes: number): Promise<void> {
    await this.place("ACCOUNT_LOCK", "ACCOUNT", accountId, triggerEventId, durationMinutes);
  }

  async placeIpBlock(ip: string, triggerEventId: string, durationMinutes: number): Promise<void> {
    await this.place("IP_BLOCK", "IP", ip, triggerEventId, durationMinutes);
  }

  private async place(
    type: "ACCOUNT_LOCK" | "IP_BLOCK",
    targetType: "ACCOUNT" | "IP",
    targetId: string,
    triggerEventId: string,
    durationMinutes: number,
  ): Promise<void> {
    try {
      const now = Date.now();
      const restriction = {
        id: randomUUID(),
        createdAt: now,
        type,
        targetType,
        targetId,
        eventId: triggerEventId,
        durationMinutes,
        expiresAt: now + durationMinutes * 60_000,
      };

      await this.repository.create(restriction);

      await this.dispatcher.dispatch(
        systemEvent(EventTypes.RestrictionPlaced, {
          action: `${ID_SERVICE}:RestrictionPlaced`,
          restrictionId: restriction.id,
          triggerEventId,
          type,
          targetType,
          targetId,
          durationMinutes,
        }),
      );

      this.logger.info(`Placed ${type.toLowerCase()}`, {
        target_id: targetId,
        duration_minutes: durationMinutes,
        trigger_event_id: triggerEventId,
      });
    } catch (error) {
      this.logger.error(`Failed to place ${type.toLowerCase()}`, { target_id: targetId, error });
    }
  }
}
