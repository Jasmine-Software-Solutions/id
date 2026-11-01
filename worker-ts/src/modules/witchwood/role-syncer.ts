import { createLogger, type Logger } from "../../runtime/logging";
import type { WitchwoodClient } from "./client";

export class RoleSyncer {
  private readonly logger: Logger;

  constructor(
    private readonly client: WitchwoodClient,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("witchwood.role");
  }

  async ensureRole(roleId: string): Promise<void> {
    try {
      await this.client.getRole(roleId);
    } catch {
      await this.client.createRole(roleId, []);
    }
    this.logger.debug("Ensured Witchwood role", { role_id: roleId });
  }

  async deleteRole(roleId: string): Promise<void> {
    await this.client.deleteRole(roleId);
    this.logger.debug("Deleted Witchwood role", { role_id: roleId });
  }

  async assignToPrincipal(principalId: string, roleId: string): Promise<void> {
    await this.client.assignRoleToPrincipal(principalId, roleId);
    this.logger.debug("Assigned role to principal", { role_id: roleId, principal_id: principalId });
  }

  async unassignFromPrincipal(principalId: string, roleId: string): Promise<void> {
    await this.client.unassignRoleFromPrincipal(principalId, roleId);
    this.logger.debug("Unassigned role from principal", { role_id: roleId, principal_id: principalId });
  }
}

/** The principal addressed by an assignment event: account first, then client. */
export function assignmentPrincipalId(
  accountId: string | undefined,
  clientId: string | undefined,
): string | undefined {
  return accountId ?? clientId;
}
