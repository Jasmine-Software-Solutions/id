import type { DetectorStore } from "../../../infrastructure/redis/windows";
import type { GeoLocator } from "../../../infrastructure/redis/geo-cache";
import { createLogger, type Logger } from "../../../runtime/logging";
import { asString, requiredString, type Wire } from "../../../wire/codec";
import { EventTypes } from "../../../wire/event-types";
import { ID_SERVICE, systemEvent, type EventDispatcher } from "../../../wire/publisher";
import { haversineDistance } from "./geo";

const FAR_AWAY_KM = 500.0;

/**
 * Detects TOTP being disabled from a country other than where it was enabled,
 * more than 500 km away, or when the enable location is unknown.
 *
 * A disable from the same IP as the enable is always allowed. The enable
 * record is deleted after a disable (its TTL also bounds it if the disable
 * never arrives).
 */
export class TotpDisableDetector {
  private readonly logger: Logger;

  constructor(
    private readonly store: DetectorStore,
    private readonly dispatcher: EventDispatcher,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("security.totp-disable");
  }

  async handleTOTPEnabled(body: Wire, geo: GeoLocator): Promise<void> {
    const accountId = requiredString(body, "account_id");
    const eventIp = asString(body.ip_address);
    const ip = eventIp ?? "unknown";
    const location = eventIp ? geo.getSync(eventIp) : null;
    await this.store.setTotpEnable(accountId, { ip, location });
  }

  async handleTOTPDisabled(body: Wire, geo: GeoLocator): Promise<void> {
    const accountId = requiredString(body, "account_id");
    const disabledIp = asString(body.ip_address);
    if (!disabledIp) return;

    const enableRecord = await this.store.getTotpEnable(accountId);
    if (!enableRecord) return;
    if (disabledIp === enableRecord.ip) return;

    const disabledLocation = geo.getSync(disabledIp);
    if (!disabledLocation) {
      // The Java detector scheduled a lookup and removed the record regardless
      // of the lookup result; preserve that (the event itself needs a location).
      await this.store.removeTotpEnable(accountId);
      return;
    }

    const enabledLocation = enableRecord.location;

    const differentCountry =
      enabledLocation !== null &&
      enabledLocation.country !== null &&
      disabledLocation.country !== null &&
      enabledLocation.country !== disabledLocation.country;

    const farAway =
      enabledLocation !== null &&
      enabledLocation.latitude !== null &&
      enabledLocation.longitude !== null &&
      disabledLocation.latitude !== null &&
      disabledLocation.longitude !== null &&
      haversineDistance(
        enabledLocation.latitude,
        enabledLocation.longitude,
        disabledLocation.latitude,
        disabledLocation.longitude,
      ) > FAR_AWAY_KM;

    const enabledLocationUnknown = enabledLocation === null;

    if (differentCountry || farAway || enabledLocationUnknown) {
      this.logger.warn("TOTP disabled from unusual location", {
        account_id: accountId,
        enabled_ip: enableRecord.ip,
        disabled_ip: disabledIp,
        different_country: differentCountry,
        far_away: farAway,
        unknown_location: enabledLocationUnknown,
      });

      await this.dispatcher.dispatch(
        systemEvent(EventTypes.TOTPDisabledFromUnusualLocation, {
          action: `${ID_SERVICE}:TOTPDisabledFromUnusualLocation`,
          accountId,
          enabledIp: enableRecord.ip,
          enabledLocation,
          disabledIp,
          disabledLocation,
        }),
      );
    }

    await this.store.removeTotpEnable(accountId);
  }

  async removeRecord(accountId: string): Promise<void> {
    await this.store.removeTotpEnable(accountId);
  }
}
