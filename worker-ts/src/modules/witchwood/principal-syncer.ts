import { createLogger, type Logger } from "../../runtime/logging";
import type { WitchwoodClient } from "./client";

/**
 * Idempotently ensures principals exist (get-or-create). Any error from the
 * read — not-found, transport, auth — falls through to create, exactly as the
 * Java PrincipalSyncer did.
 */
export class PrincipalSyncer {
  private readonly logger: Logger;

  constructor(
    private readonly client: WitchwoodClient,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("witchwood.principal");
  }

  async ensureAccountPrincipal(accountId: string): Promise<void> {
    await this.ensure(accountId);
    this.logger.debug("Ensured Witchwood principal for account", { account_id: accountId });
  }

  async ensureClientPrincipal(clientId: string): Promise<void> {
    await this.ensure(clientId);
    this.logger.debug("Ensured Witchwood principal for client", { client_id: clientId });
  }

  private async ensure(id: string): Promise<void> {
    try {
      await this.client.getPrincipal(id);
    } catch {
      await this.client.createPrincipal(id, [], []);
    }
  }
}
