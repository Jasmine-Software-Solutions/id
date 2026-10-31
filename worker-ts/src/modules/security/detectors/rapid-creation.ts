import { randomUUID } from "node:crypto";
import type { DetectorStore } from "../../../infrastructure/redis/windows";
import { createLogger, type Logger } from "../../../runtime/logging";
import { asString, type Wire } from "../../../wire/codec";
import { EventTypes } from "../../../wire/event-types";
import { ID_SERVICE, systemEvent, type EventDispatcher } from "../../../wire/publisher";
import type { RestrictionEnforcer } from "../restriction-enforcer";

export const DEFAULT_CREATION_THRESHOLD = 10;
export const DEFAULT_WINDOW_MINUTES = 60;

/**
 * Rapid account creation from one IP.
 *
 * Every creation counts regardless of its principal (self-service sign-ups
 * included); only the threshold and window are configurable. This mirrors the
 * Java detector's deliberate no-exemptions rule — see RapidCreationDetectorTest.
 */
export class RapidCreationDetector {
  private readonly logger: Logger;

  constructor(
    private readonly store: DetectorStore,
    private readonly enforcer: RestrictionEnforcer,
    private readonly dispatcher: EventDispatcher,
    private readonly creationThreshold: number = DEFAULT_CREATION_THRESHOLD,
    private readonly windowMinutes: number = DEFAULT_WINDOW_MINUTES,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("security.rapid-creation");
  }

  async handleAccountCreated(body: Wire): Promise<void> {
    const ip = asString(body.ip_address);
    if (!ip) return;

    const count = await this.store.recordCreation(ip, this.creationThreshold, this.windowMinutes);
    if (count < this.creationThreshold) return;

    this.logger.warn("Rapid account creation detected", {
      ip_address: ip,
      creations: count,
      window_minutes: this.windowMinutes,
    });

    const eventId = randomUUID();
    await this.dispatcher.dispatch(
      systemEvent(EventTypes.RapidAccountCreationDetected, {
        eventId,
        action: `${ID_SERVICE}:RapidAccountCreationDetected`,
        ipAddress: ip,
        creationCount: count,
        windowMinutes: this.windowMinutes,
      }),
    );

    await this.enforcer.placeIpBlock(ip, eventId, this.windowMinutes);
  }
}
