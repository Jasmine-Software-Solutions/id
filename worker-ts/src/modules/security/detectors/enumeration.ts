import { randomUUID } from "node:crypto";
import type { DetectorStore } from "../../../infrastructure/redis/windows";
import { createLogger, type Logger } from "../../../runtime/logging";
import { asString, type Wire } from "../../../wire/codec";
import { EventTypes } from "../../../wire/event-types";
import { ID_SERVICE, systemEvent, type EventDispatcher } from "../../../wire/publisher";
import type { RestrictionEnforcer } from "../restriction-enforcer";

export const FLOW_THRESHOLD = 20;
export const WINDOW_MINUTES = 5;
const BLOCK_DURATION_MINUTES = WINDOW_MINUTES * 6;

/** Email-enumeration detection: authentication flows started per IP. */
export class EnumerationDetector {
  private readonly logger: Logger;

  constructor(
    private readonly store: DetectorStore,
    private readonly enforcer: RestrictionEnforcer,
    private readonly dispatcher: EventDispatcher,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("security.enumeration");
  }

  async handleFlowStarted(body: Wire): Promise<void> {
    const ip = asString(body.ip_address);
    if (!ip) return;

    const count = await this.store.recordFlowStart(ip, FLOW_THRESHOLD);
    if (count < FLOW_THRESHOLD) return;

    this.logger.warn("Enumeration detected", { ip_address: ip, flow_starts: count });

    const eventId = randomUUID();
    await this.dispatcher.dispatch(
      systemEvent(EventTypes.EnumerationDetected, {
        eventId,
        action: `${ID_SERVICE}:EnumerationDetected`,
        ipAddress: ip,
        flowCount: count,
        windowMinutes: WINDOW_MINUTES,
      }),
    );

    await this.enforcer.placeIpBlock(ip, eventId, BLOCK_DURATION_MINUTES);
  }
}
