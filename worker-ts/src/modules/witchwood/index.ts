import { createLogger } from "../../runtime/logging";
import { asArray, asString, requiredString, type Wire } from "../../wire/codec";
import { EventTypes } from "../../wire/event-types";
import type { PolicySyncer } from "./policy-syncer";
import type { PrincipalSyncer } from "./principal-syncer";
import { assignmentPrincipalId, type RoleSyncer } from "./role-syncer";

/**
 * Routes role/policy/principal events to Witchwood — the port of
 * WitchwoodSyncer. The whole body is wrapped in try/catch: the Java worker
 * logged and swallowed every failure (message deleted), and that posture is
 * preserved even though it means a missed sync is not retried.
 */
export class WitchwoodWorker {
  private readonly logger = createLogger("witchwood.worker");

  constructor(
    private readonly principals: PrincipalSyncer,
    private readonly roles: RoleSyncer,
    private readonly policies: PolicySyncer,
  ) {}

  async handle(eventType: string, body: Wire): Promise<void> {
    this.logger.info("Handling Witchwood event", { event_type: eventType });
    try {
      switch (eventType) {
        case EventTypes.AccountCreated:
        case EventTypes.AuthenticationSucceeded:
          await this.principals.ensureAccountPrincipal(requiredString(body, "account_id"));
          return;

        case EventTypes.ClientCreated:
          await this.principals.ensureClientPrincipal(requiredString(body, "client_id"));
          return;

        case EventTypes.RoleCreated:
        case EventTypes.RoleUpdated:
          await this.roles.ensureRole(requiredString(body, "role_id"));
          return;

        case EventTypes.RoleDeleted:
          await this.roles.deleteRole(requiredString(body, "role_id"));
          return;

        case EventTypes.RoleAssigned: {
          const principalId = assignmentPrincipalId(
            asString(body.account_id),
            asString(body.client_id),
          );
          if (principalId) {
            await this.roles.assignToPrincipal(principalId, requiredString(body, "role_id"));
          }
          return;
        }

        case EventTypes.RoleUnassigned: {
          const principalId = assignmentPrincipalId(
            asString(body.account_id),
            asString(body.client_id),
          );
          if (principalId) {
            await this.roles.unassignFromPrincipal(principalId, requiredString(body, "role_id"));
          }
          return;
        }

        case EventTypes.PolicyCreated:
          await this.policies.upsert(requiredString(body, "policy_id"), requireDocument(body));
          return;

        case EventTypes.PolicyUpdated: {
          const newDocument = asArray(body.new_document);
          // `event.newDocument?.let { ... }` — an update that only renamed a
          // policy (or cleared its document) does not touch Witchwood.
          if (newDocument) {
            await this.policies.upsert(requiredString(body, "policy_id"), newDocument);
          }
          return;
        }

        case EventTypes.PolicyDeleted:
          await this.policies.delete(requiredString(body, "policy_id"));
          return;

        case EventTypes.PolicyAssigned: {
          const policyId = requiredString(body, "policy_id");
          const roleId = asString(body.role_id);
          const principalId = assignmentPrincipalId(asString(body.account_id), asString(body.client_id));
          if (roleId) await this.policies.assignToRole(roleId, policyId);
          if (principalId) await this.policies.assignToPrincipal(principalId, policyId);
          return;
        }

        case EventTypes.PolicyUnassigned: {
          const policyId = requiredString(body, "policy_id");
          const roleId = asString(body.role_id);
          const principalId = assignmentPrincipalId(asString(body.account_id), asString(body.client_id));
          if (roleId) await this.policies.unassignFromRole(roleId, policyId);
          if (principalId) await this.policies.unassignFromPrincipal(principalId, policyId);
          return;
        }

        default:
          this.logger.debug("WitchwoodSyncer ignoring event", { event_type: eventType });
      }
    } catch (error) {
      this.logger.error("Failed to handle Witchwood event", { event_type: eventType, error });
    }
  }
}

function requireDocument(body: Wire): unknown[] {
  const document = asArray(body.document);
  if (!document) throw new Error("policy document is missing");
  return document;
}
