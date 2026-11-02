import type { Context, SQSBatchResponse, SQSEvent } from "aws-lambda";
import { loadWitchwoodConfig, type WitchwoodConfig } from "../config/witchwood";
import { env } from "../config/env";
import { createSsmClient } from "../runtime/aws";
import { flushTelemetry, initTelemetry } from "../runtime/bootstrap";
import { runBatch } from "../runtime/batch-processor";
import { createLogger } from "../runtime/logging";
import { Secrets } from "../runtime/secrets";
import { WITCHWOOD_EVENT_TYPES } from "../wire/event-types";
import { bodyOf } from "../wire/parse";
import { WitchwoodClient } from "../modules/witchwood/client";
import { WitchwoodWorker } from "../modules/witchwood";
import { PolicyCompiler } from "../modules/witchwood/policy-compiler";
import { PolicySyncer } from "../modules/witchwood/policy-syncer";
import { PrincipalSyncer } from "../modules/witchwood/principal-syncer";
import { RoleSyncer } from "../modules/witchwood/role-syncer";

const logger = createLogger("handler.witchwood");

export interface WitchwoodServices {
  worker: WitchwoodWorker;
  client: WitchwoodClient;
}

let servicesPromise: Promise<WitchwoodServices> | undefined;

export function getWitchwoodServices(): Promise<WitchwoodServices> {
  servicesPromise ??= createWitchwoodServices().catch((error) => {
    servicesPromise = undefined;
    throw error;
  });
  return servicesPromise;
}

export async function createWitchwoodServices(
  config: WitchwoodConfig = loadWitchwoodConfig(),
): Promise<WitchwoodServices> {
  const privateKeyBase64 = await resolvePrivateKey(config);
  const client = new WitchwoodClient({
    host: config.host,
    port: config.port,
    privateKeyBase64,
    protoRoot: config.protoRoot,
    logger: createLogger("witchwood.client"),
  });

  const worker = new WitchwoodWorker(
    new PrincipalSyncer(client),
    new RoleSyncer(client),
    new PolicySyncer(client, new PolicyCompiler()),
  );
  return { worker, client };
}

async function resolvePrivateKey(config: WitchwoodConfig): Promise<string> {
  if (config.jwtEncodedPrivateKey !== undefined) return config.jwtEncodedPrivateKey;
  const secrets = await Secrets.load({
    names: ["id/jwt-encoded-private-key"],
    prefix: config.ssmPrefix,
    client: createSsmClient({
      region: process.env.AWS_REGION || "us-east-1",
      endpoint: env("SSM_ENDPOINT_OVERRIDE"),
    }),
    logger,
  });
  return secrets.get("id/jwt-encoded-private-key");
}

export const handler = async (event: SQSEvent, context: Context): Promise<SQSBatchResponse> => {
  await initTelemetry("witchwood");
  try {
    const services = await getWitchwoodServices();
    return await runBatch(event, context, {
      moduleName: "witchwood",
      knownTypes: WITCHWOOD_EVENT_TYPES,
      createContext: () => services,
      process: async (record, svc) => {
        await svc.worker.handle(record.message.eventType, bodyOf(record));
      },
    });
  } finally {
    await flushTelemetry();
  }
};

/** Test hook: drop the cold-start singleton. */
export function resetWitchwoodServicesForTests(): void {
  servicesPromise = undefined;
}
