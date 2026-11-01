import { existsSync } from "node:fs";
import path from "node:path";
import { createPrivateKey } from "node:crypto";
import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import { sign } from "jsonwebtoken";
import { createLogger, type Logger } from "../../runtime/logging";
import type { PolicyStatement } from "./policy-compiler";
import { statementToProto } from "./proto-codec";

const ISSUER = "https://id.jasmine.software/";
const AUDIENCE = "https://witchwood.jasmine.software/";
const TOKEN_TTL_SECONDS = 5 * 60;
const CALL_DEADLINE_MS = 5_000;

export interface RoleAssignment {
  roleId: string;
  effective?: number;
  expiration?: number;
}

export interface WitchwoodClientOptions {
  host: string;
  port: number;
  /** Base64-encoded PKCS#8 DER private key (the SSM secret). */
  privateKeyBase64: string;
  protoRoot?: string;
  logger?: Logger;
}

type Callback = (error: grpc.ServiceError | null, response: unknown) => void;
type UnaryMethod = (
  request: unknown,
  metadata: grpc.Metadata,
  options: grpc.CallOptions,
  callback: Callback,
) => void;

interface WitchwoodGrpcClient extends grpc.Client {
  createPrincipal: UnaryMethod;
  getPrincipal: UnaryMethod;
  updatePrincipal: UnaryMethod;
  deletePrincipal: UnaryMethod;
  createPolicy: UnaryMethod;
  getPolicy: UnaryMethod;
  updatePolicy: UnaryMethod;
  deletePolicy: UnaryMethod;
  createRole: UnaryMethod;
  getRole: UnaryMethod;
  updateRole: UnaryMethod;
  deleteRole: UnaryMethod;
  assignPolicyToRole: UnaryMethod;
  unassignPolicyFromRole: UnaryMethod;
  assignRoleToPrincipal: UnaryMethod;
  unassignRoleFromPrincipal: UnaryMethod;
  assignPolicyToPrincipal: UnaryMethod;
  unassignPolicyFromPrincipal: UnaryMethod;
  evaluate: UnaryMethod;
  registerService: UnaryMethod;
  deregisterService: UnaryMethod;
  putActions: UnaryMethod;
  queryResources: UnaryMethod;
}

interface ServiceClientConstructor extends grpc.ServiceClientConstructor {
  new (address: string, credentials: grpc.ChannelCredentials, options?: object): WitchwoodGrpcClient;
}

/**
 * gRPC client for Witchwood — the port of the Java `WitchwoodClient` surface
 * the workers use. The channel follows DNS (`dns:///host:port`) so a replaced
 * Witchwood task is picked up without pinning the first IP, and every call
 * carries a freshly signed RS256 bearer token.
 */
export class WitchwoodClient {
  private readonly client: WitchwoodGrpcClient;
  private readonly tokenSupplier: () => string;
  private readonly logger: Logger;

  constructor(options: WitchwoodClientOptions) {
    this.logger = options.logger ?? createLogger("witchwood.client");
    this.tokenSupplier = createTokenSupplier(options.privateKeyBase64);

    const protoRoot = resolveProtoRoot(options.protoRoot);
    const definition = protoLoader.loadSync("witchwood/v1/service.proto", {
      includeDirs: [protoRoot],
      keepCase: false,
      longs: String,
      enums: String,
      defaults: true,
      oneofs: true,
      bytes: Buffer,
    });
    const loaded = grpc.loadPackageDefinition(definition) as unknown as {
      witchwood: { v1: { Witchwood: ServiceClientConstructor } };
    };
    const Client = loaded.witchwood.v1.Witchwood;
    this.client = new Client(`dns:///${options.host}:${options.port}`, grpc.credentials.createInsecure());
    this.logger.info("Witchwood channel created", {
      host: options.host,
      port: options.port,
      proto_root: protoRoot,
    });
  }

  // -- Principal ----------------------------------------------------------

  async createPrincipal(
    id: string,
    roleAssignments: RoleAssignment[] = [],
    policyIds: string[] = [],
  ): Promise<string> {
    const response = (await this.call("createPrincipal", {
      id,
      roles: roleAssignments.map((assignment) => ({
        roleId: assignment.roleId,
        window: {
          effective: assignment.effective ?? 0,
          expiration: assignment.expiration ?? "9223372036854775807",
        },
      })),
      policies: policyIds.map((policyId) => ({ policyId })),
    })) as { id: string };
    return response.id;
  }

  async getPrincipal(id: string): Promise<unknown> {
    return this.call("getPrincipal", { id });
  }

  async updatePrincipal(
    id: string,
    roleAssignments: RoleAssignment[] = [],
    policyIds: string[] = [],
  ): Promise<void> {
    await this.call("updatePrincipal", {
      id,
      roles: roleAssignments.map((assignment) => ({
        roleId: assignment.roleId,
        window: {
          effective: assignment.effective ?? 0,
          expiration: assignment.expiration ?? "9223372036854775807",
        },
      })),
      policies: policyIds.map((policyId) => ({ policyId })),
    });
  }

  async deletePrincipal(id: string): Promise<void> {
    await this.call("deletePrincipal", { id });
  }

  // -- Policy -------------------------------------------------------------

  async createPolicy(id: string, statements: PolicyStatement[]): Promise<string> {
    const response = (await this.call("createPolicy", {
      id,
      statements: statements.map(statementToProto),
    })) as { id: string };
    return response.id;
  }

  async updatePolicy(id: string, statements: PolicyStatement[]): Promise<void> {
    await this.call("updatePolicy", { id, statements: statements.map(statementToProto) });
  }

  async deletePolicy(id: string): Promise<void> {
    await this.call("deletePolicy", { id });
  }

  // -- Role ---------------------------------------------------------------

  async createRole(id: string, policyIds: Iterable<string>): Promise<string> {
    const response = (await this.call("createRole", {
      id,
      policyIds: [...policyIds],
    })) as { id: string };
    return response.id;
  }

  async getRole(id: string): Promise<unknown> {
    return this.call("getRole", { id });
  }

  async updateRole(id: string, policyIds: Iterable<string>): Promise<void> {
    await this.call("updateRole", { id, policyIds: [...policyIds] });
  }

  async deleteRole(id: string): Promise<void> {
    await this.call("deleteRole", { id });
  }

  // -- Incremental assignments -------------------------------------------

  async assignPolicyToRole(roleId: string, policyId: string): Promise<void> {
    await this.call("assignPolicyToRole", { roleId, policyId });
  }

  async unassignPolicyFromRole(roleId: string, policyId: string): Promise<void> {
    await this.call("unassignPolicyFromRole", { roleId, policyId });
  }

  async assignRoleToPrincipal(principalId: string, roleId: string): Promise<void> {
    await this.call("assignRoleToPrincipal", { principalId, roleId });
  }

  async unassignRoleFromPrincipal(principalId: string, roleId: string): Promise<void> {
    await this.call("unassignRoleFromPrincipal", { principalId, roleId });
  }

  async assignPolicyToPrincipal(principalId: string, policyId: string): Promise<void> {
    await this.call("assignPolicyToPrincipal", { principalId, policyId });
  }

  async unassignPolicyFromPrincipal(principalId: string, policyId: string): Promise<void> {
    await this.call("unassignPolicyFromPrincipal", { principalId, policyId });
  }

  close(): void {
    this.client.close();
  }

  /** Test hook: confirms a loaded proto actually exposed the RPC. */
  hasRpc(name: string): boolean {
    return typeof (this.client as unknown as Record<string, unknown>)[name] === "function";
  }

  // -- Internal -----------------------------------------------------------

  private call(method: keyof WitchwoodGrpcClient & string, request: unknown): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const methodFn = this.client[method] as UnaryMethod;
      if (typeof methodFn !== "function") {
        reject(new Error(`Witchwood RPC not found: ${method}`));
        return;
      }

      let token: string;
      try {
        token = this.tokenSupplier();
      } catch (error) {
        reject(error);
        return;
      }

      const metadata = new grpc.Metadata();
      metadata.set("authorization", `Bearer ${token}`);

      methodFn.call(
        this.client,
        request,
        metadata,
        { deadline: Date.now() + CALL_DEADLINE_MS },
        (error: grpc.ServiceError | null, response: unknown) => {
          if (error) reject(error);
          else resolve(response);
        },
      );
    });
  }
}

function createTokenSupplier(privateKeyBase64: string): () => string {
  const key = createPrivateKey({
    key: Buffer.from(privateKeyBase64, "base64"),
    format: "der",
    type: "pkcs8",
  });

  return () => {
    const now = Math.floor(Date.now() / 1000);
    return sign(
      {
        sub: ISSUER,
        iss: ISSUER,
        aud: AUDIENCE,
        iat: now,
        nbf: now,
        exp: now + TOKEN_TTL_SECONDS,
      },
      key,
      { algorithm: "RS256" },
    );
  };
}

/**
 * Locates `proto/` both from the bundled handler (`dist/handlers/*.js`) and
 * from source when tests import this module directly.
 */
export function resolveProtoRoot(explicit?: string): string {
  const candidates = [
    explicit,
    process.env.PROTO_ROOT,
    path.resolve(__dirname, "../../proto"),
    path.resolve(__dirname, "../../../proto"),
    path.resolve(process.cwd(), "proto"),
  ].filter((candidate): candidate is string => typeof candidate === "string" && candidate.length > 0);

  for (const candidate of candidates) {
    if (existsSync(path.join(candidate, "witchwood/v1/service.proto"))) return candidate;
  }
  throw new Error(`could not locate witchwood protos (tried: ${candidates.join(", ")})`);
}
