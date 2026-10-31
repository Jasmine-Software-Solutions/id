import type { DetectorStore } from "../../../infrastructure/redis/windows";
import type { GeoLocator } from "../../../infrastructure/redis/geo-cache";
import { createLogger, type Logger } from "../../../runtime/logging";
import { asString, requiredString, type Wire } from "../../../wire/codec";
import { EventTypes } from "../../../wire/event-types";
import { ID_SERVICE, systemEvent, type EventDispatcher } from "../../../wire/publisher";
import { haversineDistance } from "./geo";

export const MAX_SPEED_KMH = 1000.0;

/**
 * Compares consecutive successful logins for an account. The previous login
 * record lives in Redis; the current location was prefetched for the batch.
 * A detection does not change the outcome of the login itself.
 */
export class ImpossibleTravelDetector {
  private readonly logger: Logger;

  constructor(
    private readonly store: DetectorStore,
    private readonly dispatcher: EventDispatcher,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("security.impossible-travel");
  }

  async handleAuthSucceeded(body: Wire, geo: GeoLocator): Promise<void> {
    const accountId = requiredString(body, "account_id");
    const ip = asString(body.ip_address);
    if (!ip) return;

    const now = new Date();
    const currentLocation = geo.getSync(ip);
    const previous = await this.store.getLastLogin(accountId);

    if (previous) {
      const previousLocation = previous.location;
      if (previousLocation && currentLocation) {
        const prevLat = previousLocation.latitude;
        const prevLng = previousLocation.longitude;
        const curLat = currentLocation.latitude;
        const curLng = currentLocation.longitude;

        if (prevLat !== null && prevLng !== null && curLat !== null && curLng !== null) {
          const distanceKm = haversineDistance(prevLat, prevLng, curLat, curLng);
          const hoursElapsed = (now.getTime() - previous.timestamp) / 3_600_000;

          if (hoursElapsed > 0) {
            const speedKmh = distanceKm / hoursElapsed;
            if (speedKmh > MAX_SPEED_KMH) {
              this.logger.warn("Impossible travel detected", {
                account_id: accountId,
                from: previousLocation.city,
                from_country: previousLocation.country,
                to: currentLocation.city,
                to_country: currentLocation.country,
                distance_km: distanceKm,
                speed_kmh: speedKmh,
              });

              await this.dispatcher.dispatch(
                systemEvent(EventTypes.ImpossibleTravelDetected, {
                  action: `${ID_SERVICE}:ImpossibleTravelDetected`,
                  accountId,
                  previousIp: previous.ip,
                  previousLocation,
                  previousLoginAt: new Date(previous.timestamp),
                  currentIp: ip,
                  currentLocation,
                  currentLoginAt: now,
                }),
              );
            }
          }
        }
      }
    }

    // Deterministic by timestamp: concurrent invocations cannot move the
    // record backwards to an older login.
    await this.store.setLastLogin(accountId, {
      sessionId: asString(body.session_id) ?? "",
      ip,
      location: currentLocation,
      timestamp: now.getTime(),
    });
  }

  async removeLoginRecord(accountId: string): Promise<void> {
    await this.store.removeLastLogin(accountId);
  }
}
