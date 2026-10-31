import { createLogger, type Logger } from "../../runtime/logging";
import type { WitchwoodClient } from "./client";
import type { PolicyCompiler } from "./policy-compiler";

export class PolicySyncer {
  private readonly logger: Logger;

  constructor(
    private readonly client: WitchwoodClient,
    private readonly compiler: PolicyCompiler,
    logger?: Logger,
  ) {
    this.logger = logger ?? createLogger("witchwood.policy");
  }

  /**
   * Update-then-create upsert: Witchwood has no upsert RPC, and "not found"
   * is indistinguishable from any other error at this altitude (the Java
   * client behaved the same way).
   */
  async upsert(policyId: string, document: unknown[]): Promise<void> {
    const statements = this.compiler.compile(document);
    this.logger.info("Upserting policy", { policy_id: policyId, statements: statements.length });

    try {
      await this.client.updatePolicy(policyId, statements);
      this.logger.info("Updated policy", { policy_id: policyId });
    } catch (error) {
      this.logger.warn("updatePolicy failed; trying createPolicy", {
        policy_id: policyId,
        error,
      });
      try {
        await this.client.createPolicy(policyId, statements);
        this.logger.info("Created policy", { policy_id: policyId });
      } catch (createError) {
        this.logger.error("createPolicy failed", { policy_id: policyId, error: createError });
        throw createError;
      }
    }
  }

  async delete(policyId: string): Promise<void> {
    await this.client.deletePolicy(policyId);
    this.logger.debug("Deleted Witchwood policy", { policy_id: policyId });
  }

  async assignToRole(roleId: string, policyId: string): Promise<void> {
    await this.client.assignPolicyToRole(roleId, policyId);
    this.logger.debug("Assigned policy to role", { policy_id: policyId, role_id: roleId });
  }

  async unassignFromRole(roleId: string, policyId: string): Promise<void> {
    await this.client.unassignPolicyFromRole(roleId, policyId);
    this.logger.debug("Unassigned policy from role", { policy_id: policyId, role_id: roleId });
  }

  async assignToPrincipal(principalId: string, policyId: string): Promise<void> {
    await this.client.assignPolicyToPrincipal(principalId, policyId);
    this.logger.debug("Assigned policy to principal", {
      policy_id: policyId,
      principal_id: principalId,
    });
  }

  async unassignFromPrincipal(principalId: string, policyId: string): Promise<void> {
    await this.client.unassignPolicyFromPrincipal(principalId, policyId);
    this.logger.debug("Unassigned policy from principal", {
      policy_id: policyId,
      principal_id: principalId,
    });
  }
}
