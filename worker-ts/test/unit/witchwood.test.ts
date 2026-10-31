import { generateKeyPairSync, randomUUID } from "node:crypto";
import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import { verify } from "jsonwebtoken";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { WitchwoodClient, resolveProtoRoot } from "../../src/modules/witchwood/client";
import { PolicyCompiler } from "../../src/modules/witchwood/policy-compiler";
import { PolicySyncer } from "../../src/modules/witchwood/policy-syncer";
import { PrincipalSyncer } from "../../src/modules/witchwood/principal-syncer";
import { RoleSyncer, assignmentPrincipalId } from "../../src/modules/witchwood/role-syncer";
import { WitchwoodWorker } from "../../src/modules/witchwood";
import { EventTypes } from "../../src/wire/event-types";
import type { Logger } from "../../src/runtime/logging";

const RPC_METHODS = [
  "createPrincipal",
  "getPrincipal",
  "updatePrincipal",
  "deletePrincipal",
  "createPolicy",
  "getPolicy",
  "updatePolicy",
  "deletePolicy",
  "createRole",
  "getRole",
  "updateRole",
  "deleteRole",
  "assignPolicyToRole",
  "unassignPolicyFromRole",
  "assignRoleToPrincipal",
  "unassignRoleFromPrincipal",
  "assignPolicyToPrincipal",
  "unassignPolicyFromPrincipal",
  "evaluate",
  "registerService",
  "deregisterService",
  "putActions",
  "queryResources",
] as const;

const protoRoot = resolveProtoRoot();

const { publicKey, privateKey } = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  publicKeyEncoding: { type: "spki", format: "pem" },
  privateKeyEncoding: { type: "pkcs8", format: "der" },
});

interface RecordedCall {
  method: string;
  request: Record<string, unknown>;
  authorization: string | undefined;
}

function recordingServer(calls: RecordedCall[]): grpc.Server {
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
    witchwood: { v1: { Witchwood: { service: grpc.ServiceDefinition } } };
  };
  const service = loaded.witchwood.v1.Witchwood.service;

  const handlers: Record<string, grpc.handleUnaryCall<Record<string, unknown>, unknown>> = {};
  for (const method of RPC_METHODS) {
    handlers[method] = (call, callback) => {
      calls.push({
        method,
        request: call.request,
        authorization: call.metadata.get("authorization")[0] as string | undefined,
      });
      if (method === "createPrincipal" || method === "createPolicy" || method === "createRole") {
        callback(null, { id: (call.request as { id?: string }).id });
      } else if (method === "getPrincipal") {
        callback(null, { principal: { id: (call.request as { id?: string }).id } });
      } else if (method === "getRole") {
        callback(null, { role: { id: (call.request as { id?: string }).id } });
      } else {
        callback(null, {});
      }
    };
  }

  const server = new grpc.Server();
  server.addService(service, handlers);
  return server;
}

describe("Witchwood gRPC client", () => {
  let server: grpc.Server;
  let port: number;
  let client: WitchwoodClient;
  const calls: RecordedCall[] = [];

  beforeAll(async () => {
    server = recordingServer(calls);
    port = await new Promise<number>((resolve, reject) => {
      server.bindAsync("127.0.0.1:0", grpc.ServerCredentials.createInsecure(), (error, boundPort) => {
        if (error) reject(error);
        else resolve(boundPort);
      });
    });

    client = new WitchwoodClient({
      host: "127.0.0.1",
      port,
      privateKeyBase64: privateKey.toString("base64"),
      protoRoot,
    });
  });

  afterAll(() => {
    client?.close();
    server?.forceShutdown();
  });

  it("loads all 23 RPCs", () => {
    for (const method of RPC_METHODS) {
      expect(client.hasRpc(method), method).toBe(true);
    }
    expect(RPC_METHODS).toHaveLength(23);
  });

  it("maps requests and sends a valid RS256 bearer token", async () => {
    const accountId = randomUUID();
    await expect(client.createPrincipal(accountId)).resolves.toBe(accountId);
    await expect(client.getRole(accountId)).resolves.toBeDefined();
    await client.deleteRole(accountId);
    await client.assignPolicyToRole(randomUUID(), randomUUID());

    expect(calls.map((c) => c.method)).toEqual([
      "createPrincipal",
      "getRole",
      "deleteRole",
      "assignPolicyToRole",
    ]);
    expect(calls[0]!.request).toMatchObject({ id: accountId, roles: [], policies: [] });

    const authorization = calls[0]!.authorization;
    expect(authorization).toMatch(/^Bearer /);
    const token = authorization!.slice("Bearer ".length);
    const claims = verify(token, publicKey, { algorithms: ["RS256"] }) as Record<string, unknown>;
    expect(claims.sub).toBe("https://id.jasmine.software/");
    expect(claims.iss).toBe("https://id.jasmine.software/");
    expect(claims.aud).toBe("https://witchwood.jasmine.software/");
    expect(Number(claims.exp) - Number(claims.iat)).toBe(300);
    expect(Number(claims.nbf)).toBe(Number(claims.iat));
  });

  it("sends compiled policy statements", async () => {
    const policyId = randomUUID();
    const [statement] = new PolicyCompiler().compile([
      {
        effect: "allow",
        actions: ["id.jasmine.software:GetAccount"],
        resources: [`id.jasmine.software://account:${randomUUID()}`],
      },
    ]);

    await client.createPolicy(policyId, [statement!]);
    const call = calls.at(-1)!;
    expect(call.method).toBe("createPolicy");
    expect((call.request as { id: string }).id).toBe(policyId);
    const statements = (call.request as { statements: Record<string, unknown>[] }).statements;
    expect(statements).toHaveLength(1);
    expect(statements[0]).toMatchObject({ id: 0, effect: "ALLOW" });
    expect(statements[0]!.resources).toBeInstanceOf(Array);
    const resources = statements[0]!.resources as { root: { typed: { definiteId: Buffer } } }[];
    expect(resources[0]!.root.typed.definiteId).toBeInstanceOf(Buffer);
    expect(resources[0]!.root.typed.definiteId).toHaveLength(16);
  });
});

class SilentLogger implements Logger {
  debug(): void {}
  info(): void {}
  warn(): void {}
  error(): void {}
}

describe("syncers", () => {
  const logger = new SilentLogger();

  it("principal get-or-create falls back to create on any error", async () => {
    const created: string[] = [];
    const failingRead = {
      getPrincipal: async () => {
        throw new Error("not found");
      },
      createPrincipal: async (id: string) => {
        created.push(id);
        return id;
      },
    } as unknown as WitchwoodClient;

    await new PrincipalSyncer(failingRead, logger).ensureAccountPrincipal("account-1");
    expect(created).toEqual(["account-1"]);

    const existing = {
      getPrincipal: async () => ({}),
      createPrincipal: async () => {
        throw new Error("should not create");
      },
    } as unknown as WitchwoodClient;
    await expect(
      new PrincipalSyncer(existing, logger).ensureClientPrincipal("client-1"),
    ).resolves.toBeUndefined();
  });

  it("role get-or-create falls back to create", async () => {
    const created: string[] = [];
    const client = {
      getRole: async () => {
        throw new Error("not found");
      },
      createRole: async (id: string) => {
        created.push(id);
        return id;
      },
    } as unknown as WitchwoodClient;

    await new RoleSyncer(client, logger).ensureRole("role-1");
    expect(created).toEqual(["role-1"]);
  });

  it("policy upsert is update-then-create", async () => {
    const calls: string[] = [];
    const document = [{ actions: ["a"], resources: ["svc://"] }];

    const updatable = {
      updatePolicy: async () => {
        calls.push("update");
      },
      createPolicy: async () => {
        calls.push("create");
        return "";
      },
    } as unknown as WitchwoodClient;
    await new PolicySyncer(updatable, new PolicyCompiler(), logger).upsert("p", document);
    expect(calls).toEqual(["update"]);

    calls.length = 0;
    const missing = {
      updatePolicy: async () => {
        throw new Error("missing");
      },
      createPolicy: async () => {
        calls.push("create");
        return "";
      },
    } as unknown as WitchwoodClient;
    await new PolicySyncer(missing, new PolicyCompiler(), logger).upsert("p", document);
    expect(calls).toEqual(["create"]);

    const broken = {
      updatePolicy: async () => {
        throw new Error("missing");
      },
      createPolicy: async () => {
        throw new Error("create failed");
      },
    } as unknown as WitchwoodClient;
    await expect(
      new PolicySyncer(broken, new PolicyCompiler(), logger).upsert("p", document),
    ).rejects.toThrow("create failed");
  });

  it("resolves assignment principals account-first", () => {
    expect(assignmentPrincipalId("a", "c")).toBe("a");
    expect(assignmentPrincipalId(undefined, "c")).toBe("c");
    expect(assignmentPrincipalId(undefined, undefined)).toBeUndefined();
  });
});

describe("WitchwoodWorker mapping", () => {
  const logger = new SilentLogger();
  const record = () => {
    const calls: string[] = [];
    const principals = {
      ensureAccountPrincipal: async (id: string) => calls.push(`account:${id}`),
      ensureClientPrincipal: async (id: string) => calls.push(`client:${id}`),
    };
    const roles = {
      ensureRole: async (id: string) => calls.push(`ensureRole:${id}`),
      deleteRole: async (id: string) => calls.push(`deleteRole:${id}`),
      assignToPrincipal: async (principal: string, role: string) =>
        calls.push(`assign:${principal}:${role}`),
      unassignFromPrincipal: async (principal: string, role: string) =>
        calls.push(`unassign:${principal}:${role}`),
    };
    const policies = {
      upsert: async (id: string) => calls.push(`upsert:${id}`),
      delete: async (id: string) => calls.push(`deletePolicy:${id}`),
      assignToRole: async (role: string, policy: string) => calls.push(`assignRole:${role}:${policy}`),
      unassignFromRole: async (role: string, policy: string) =>
        calls.push(`unassignRole:${role}:${policy}`),
      assignToPrincipal: async (principal: string, policy: string) =>
        calls.push(`assignPrincipal:${principal}:${policy}`),
      unassignFromPrincipal: async (principal: string, policy: string) =>
        calls.push(`unassignPrincipal:${principal}:${policy}`),
    };
    const worker = new WitchwoodWorker(
      principals as unknown as PrincipalSyncer,
      roles as unknown as RoleSyncer,
      policies as unknown as PolicySyncer,
    );
    return { worker, calls };
  };

  it("routes each of the 13 event types", async () => {
    const { worker, calls } = record();

    await worker.handle(EventTypes.AccountCreated, { account_id: "a" });
    await worker.handle(EventTypes.ClientCreated, { client_id: "c" });
    await worker.handle(EventTypes.AuthenticationSucceeded, { account_id: "a" });
    await worker.handle(EventTypes.RoleCreated, { role_id: "r" });
    await worker.handle(EventTypes.RoleUpdated, { role_id: "r" });
    await worker.handle(EventTypes.RoleDeleted, { role_id: "r" });
    await worker.handle(EventTypes.RoleAssigned, { role_id: "r", account_id: "a" });
    await worker.handle(EventTypes.RoleUnassigned, { role_id: "r", client_id: "c" });
    await worker.handle(EventTypes.PolicyCreated, { policy_id: "p", document: [] });
    await worker.handle(EventTypes.PolicyUpdated, { policy_id: "p", new_document: [] });
    await worker.handle(EventTypes.PolicyDeleted, { policy_id: "p" });
    await worker.handle(EventTypes.PolicyAssigned, { policy_id: "p", role_id: "r", client_id: "c" });
    await worker.handle(EventTypes.PolicyUnassigned, {
      policy_id: "p",
      account_id: "a",
      role_id: "r",
    });

    expect(calls).toEqual([
      "account:a",
      "client:c",
      "account:a",
      "ensureRole:r",
      "ensureRole:r",
      "deleteRole:r",
      "assign:a:r",
      "unassign:c:r",
      "upsert:p",
      "upsert:p",
      "deletePolicy:p",
      "assignRole:r:p",
      "assignPrincipal:c:p",
      "unassignRole:r:p",
      "unassignPrincipal:a:p",
    ]);
  });

  it("skips a policy update with no new document", async () => {
    const { worker, calls } = record();
    await worker.handle(EventTypes.PolicyUpdated, { policy_id: "p" });
    expect(calls).toEqual([]);
  });

  it("swallows mapper failures (the Java whole-body catch)", async () => {
    const { worker, calls } = record();
    await expect(
      worker.handle(EventTypes.AccountCreated, {}), // missing account_id
    ).resolves.toBeUndefined();
    expect(calls).toEqual([]);
  });
});
