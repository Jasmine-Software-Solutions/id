import type { GeoLocator } from "../../infrastructure/redis/geo-cache";
import { createLogger } from "../../runtime/logging";
import { asString, requiredString, type Wire } from "../../wire/codec";
import { EventTypes } from "../../wire/event-types";
import type { BruteForceDetector } from "./detectors/brute-force";
import type { CredentialStuffingDetector } from "./credential-stuffing";
import type { EnumerationDetector } from "./detectors/enumeration";
import type { ImpossibleTravelDetector } from "./detectors/impossible-travel";
import type { RapidCreationDetector } from "./detectors/rapid-creation";
import type { TotpDisableDetector } from "./detectors/totp-disable";
import type { SessionTracker } from "./session-tracker";

/**
 * Thin orchestrator over the detectors — the port of SecurityWorker. All
 * detection logic and state live in the individual components; this class
 * only routes each event to the right one.
 */
export class SecurityWorker {
  private readonly logger = createLogger("security.worker");

  constructor(
    private readonly bruteForce: BruteForceDetector,
    private readonly enumeration: EnumerationDetector,
    private readonly rapidCreation: RapidCreationDetector,
    private readonly impossibleTravel: ImpossibleTravelDetector,
    private readonly totpDisable: TotpDisableDetector,
    private readonly sessionTracker: SessionTracker,
    private readonly credentialStuffing: CredentialStuffingDetector,
  ) {}

  async handle(eventType: string, body: Wire, geo: GeoLocator): Promise<void> {
    switch (eventType) {
      case EventTypes.ValidPasswordAtAuthentication:
        await this.credentialStuffing.handleValidPassword(body);
        return;

      case EventTypes.InvalidPasswordAtAuthentication:
        await this.bruteForce.handleInvalidPassword(body);
        return;

      case EventTypes.InvalidTOTPAtAuthentication:
        await this.bruteForce.handleInvalidTOTP(body);
        return;

      case EventTypes.AuthenticationSucceeded: {
        await this.bruteForce.handleAuthSucceeded(
          requiredString(body, "email"),
          asString(body.ip_address) ?? "unknown",
          requiredString(body, "account_id"),
        );
        await this.impossibleTravel.handleAuthSucceeded(body, geo);
        return;
      }

      case EventTypes.AuthenticationFlowStarted:
        await this.enumeration.handleFlowStarted(body);
        return;

      case EventTypes.SessionCreated:
        await this.sessionTracker.handleSessionCreated(body);
        return;

      case EventTypes.SessionRevoked:
        await this.sessionTracker.handleSessionRevoked(body);
        return;

      case EventTypes.AllSessionsRevoked:
        await this.sessionTracker.handleAllSessionsRevoked(body);
        return;

      case EventTypes.AccountCreated:
        await this.rapidCreation.handleAccountCreated(body);
        return;

      case EventTypes.AccountDeleted: {
        const accountId = requiredString(body, "account_id");
        await this.impossibleTravel.removeLoginRecord(accountId);
        await this.sessionTracker.removeAccount(accountId);
        await this.totpDisable.removeRecord(accountId);
        return;
      }

      case EventTypes.AccountPasswordSet:
      case EventTypes.AccountEmailChangeVerified:
        await this.bruteForce.clearForAccount(requiredString(body, "account_id"));
        return;

      case EventTypes.AccountTOTPEnabled:
        await this.totpDisable.handleTOTPEnabled(body, geo);
        return;

      case EventTypes.AccountTOTPDisabled:
        await this.totpDisable.handleTOTPDisabled(body, geo);
        return;

      default:
        this.logger.debug("SecurityWorker ignoring event", { event_type: eventType });
    }
  }
}
