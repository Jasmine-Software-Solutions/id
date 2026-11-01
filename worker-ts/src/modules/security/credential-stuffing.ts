import type { Pool } from "pg";
import { deletePasswords } from "../../infrastructure/db/passwords";
import { deleteActiveSessions } from "../../infrastructure/db/sessions";
import { isPasswordPwned } from "../../infrastructure/http/hibp";
import { createLogger, type Logger } from "../../runtime/logging";
import { requiredString, asString, type Wire } from "../../wire/codec";
import { EventTypes } from "../../wire/event-types";
import { ID_SERVICE, systemEvent, type EventDispatcher } from "../../wire/publisher";

/**
 * Revokes every unexpired session for an account and emits
 * AllSessionsRevokedEvent only when something was deleted — matching
 * ExposedSessionRevoker's "empty list → no event" branch.
 */
export class SessionRevoker {
  private readonly logger: Logger;

  constructor(
    private readonly pool: Pool,
    private readonly dispatcher: EventDispatcher,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("security.sessions");
  }

  async revokeAllSessions(accountId: string): Promise<void> {
    try {
      const sessionIds = await deleteActiveSessions(this.pool, accountId, Date.now());
      if (sessionIds.length === 0) return;

      this.logger.info("Revoked sessions for account (credential stuffing)", {
        account_id: accountId,
        count: sessionIds.length,
      });

      await this.dispatcher.dispatch(
        systemEvent(EventTypes.AllSessionsRevoked, {
          action: `${ID_SERVICE}:AllSessionsRevoked`,
          accountId,
          revokedCount: sessionIds.length,
        }),
      );
    } catch (error) {
      this.logger.error("Failed to revoke sessions", { account_id: accountId, error });
    }
  }
}

/** Deletes every password row for an account (no event of its own). */
export class PasswordDeleter {
  private readonly logger: Logger;

  constructor(
    private readonly pool: Pool,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("security.passwords");
  }

  async deletePassword(accountId: string): Promise<void> {
    try {
      const passwordIds = await deletePasswords(this.pool, accountId);
      if (passwordIds.length === 0) return;
      this.logger.info("Deleted passwords for account (credential stuffing)", {
        account_id: accountId,
        count: passwordIds.length,
      });
    } catch (error) {
      this.logger.error("Failed to delete passwords", { account_id: accountId, error });
    }
  }
}

export interface CredentialStuffingDetectorOptions {
  hibpBaseUrl: string;
  fetchImpl?: typeof fetch;
  logger?: Logger;
}

/**
 * Checks the submitted plaintext password's SHA-1 against HIBP's k-anonymity
 * range API. On a hit: emit the security event, revoke sessions, delete the
 * password. HIBP failures return without action (the event is still deleted).
 */
export class CredentialStuffingDetector {
  private readonly logger: Logger;

  constructor(
    private readonly dispatcher: EventDispatcher,
    private readonly sessionRevoker: SessionRevoker,
    private readonly passwordDeleter: PasswordDeleter,
    private readonly options: CredentialStuffingDetectorOptions,
  ) {
    this.logger = options.logger ?? createLogger("security.credential-stuffing");
  }

  async handleValidPassword(body: Wire): Promise<void> {
    const accountId = requiredString(body, "account_id");
    const email = requiredString(body, "email");
    const ipAddress = asString(body.ip_address);
    const passwordHash = requiredString(body, "password_hash");
    const prefix = passwordHash.slice(0, 5).toUpperCase();

    try {
      const pwned = await isPasswordPwned(passwordHash, {
        baseUrl: this.options.hibpBaseUrl,
        fetchImpl: this.options.fetchImpl,
        logger: this.logger,
      });
      if (!pwned) return;

      this.logger.warn("Credential stuffing detected", {
        account_id: accountId,
        email,
        ip_address: ipAddress,
        hash_prefix: prefix,
      });

      await this.dispatcher.dispatch(
        systemEvent(EventTypes.CredentialStuffingSucceeded, {
          action: `${ID_SERVICE}:CredentialStuffingDetected`,
          accountId,
          email,
          ipAddress: ipAddress ?? "unknown",
          hashPrefix: prefix,
        }),
      );

      await this.sessionRevoker.revokeAllSessions(accountId);
      await this.passwordDeleter.deletePassword(accountId);
    } catch (error) {
      this.logger.error("Failed to check HIBP", { account_id: accountId, error });
    }
  }
}
