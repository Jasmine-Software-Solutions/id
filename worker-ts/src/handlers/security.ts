import type { Context, SQSBatchResponse, SQSEvent } from "aws-lambda";
import type Redis from "ioredis";
import type { SecurityConfig } from "../config/security";
import { loadSecurityConfig } from "../config/security";
import { env } from "../config/env";
import { createPool } from "../infrastructure/db/pool";
import { RestrictionRepository } from "../infrastructure/db/restrictions";
import { createRedisClient } from "../infrastructure/redis/client";
import { GeoLocator } from "../infrastructure/redis/geo-cache";
import { RedisScripts } from "../infrastructure/redis/scripts";
import { DetectorStore } from "../infrastructure/redis/windows";
import { createSsmClient } from "../runtime/aws";
import { flushTelemetry, initTelemetry } from "../runtime/bootstrap";
import { runBatch, type BatchDefinition } from "../runtime/batch-processor";
import { createLogger, type Logger } from "../runtime/logging";
import { Secrets } from "../runtime/secrets";
import { asString } from "../wire/codec";
import { EventTypes, SECURITY_EVENT_TYPES } from "../wire/event-types";
import { bodyOf, type ParsedRecord } from "../wire/parse";
import { createPublisher, type EventDispatcher } from "../wire/publisher";
import { CredentialStuffingDetector, PasswordDeleter, SessionRevoker } from "../modules/security/credential-stuffing";
import { BruteForceDetector } from "../modules/security/detectors/brute-force";
import { EnumerationDetector } from "../modules/security/detectors/enumeration";
import { ImpossibleTravelDetector } from "../modules/security/detectors/impossible-travel";
import { RapidCreationDetector } from "../modules/security/detectors/rapid-creation";
import { TotpDisableDetector } from "../modules/security/detectors/totp-disable";
import { SecurityWorker } from "../modules/security";
import { RestrictionEnforcer } from "../modules/security/restriction-enforcer";
import { SessionTracker } from "../modules/security/session-tracker";

const logger = createLogger("handler.security");

export interface SecurityServices {
  config: SecurityConfig;
  store: DetectorStore;
  worker: SecurityWorker;
  redis: Redis;
  scripts: RedisScripts;
  publisher: EventDispatcher;
}

export interface SecurityInvocation {
  geo: GeoLocator;
}

let servicesPromise: Promise<SecurityServices> | undefined;

export function getSecurityServices(): Promise<SecurityServices> {
  servicesPromise ??= createSecurityServices().catch((error) => {
    servicesPromise = undefined;
    throw error;
  });
  return servicesPromise;
}

export async function createSecurityServices(
  config: SecurityConfig = loadSecurityConfig(),
): Promise<SecurityServices> {
  const password = await resolveDbPassword(config);
  const pool = createPool({ ...config.database, password });
  const redis = createRedisClient(config.redisUrl, logger);
  const scripts = new RedisScripts(redis);
  const store = new DetectorStore(redis, scripts);
  const publisher = createPublisher({
    topicArn: config.snsTopicArn,
    region: config.snsRegion,
    endpoint: config.snsEndpoint,
    logger: createLogger("security.publisher"),
  });

  const repository = new RestrictionRepository(pool);
  const enforcer = new RestrictionEnforcer(repository, publisher, createLogger("security.restriction"));
  const sessionRevoker = new SessionRevoker(pool, publisher, createLogger("security.sessions"));
  const passwordDeleter = new PasswordDeleter(pool, createLogger("security.passwords"));

  const worker = new SecurityWorker(
    new BruteForceDetector(store, enforcer, publisher),
    new EnumerationDetector(store, enforcer, publisher),
    new RapidCreationDetector(
      store,
      enforcer,
      publisher,
      config.creationThreshold,
      config.creationWindowMinutes,
    ),
    new ImpossibleTravelDetector(store, publisher),
    new TotpDisableDetector(store, publisher),
    new SessionTracker(store, publisher),
    new CredentialStuffingDetector(publisher, sessionRevoker, passwordDeleter, {
      hibpBaseUrl: config.hibpBaseUrl,
    }),
  );

  return { config, store, worker, redis, scripts, publisher };
}

async function resolveDbPassword(config: SecurityConfig): Promise<string> {
  if (config.database.password !== undefined) return config.database.password;
  if (!config.ssmPrefix) {
    throw new Error("database password is unavailable: set PGPASSWORD or SSM_PARAMETER_PREFIX");
  }

  const secrets = await Secrets.load({
    names: ["db-password"],
    prefix: config.ssmPrefix,
    client: createSsmClient({
      region: config.awsRegion,
      endpoint: env("SSM_ENDPOINT_OVERRIDE"),
    }),
    logger,
  });
  return secrets.get("db-password");
}

/**
 * Prefetches every IP the batch could ask the geolocator about.
 *
 * The JVM had a background thread that resolved IPs while the worker kept
 * running; a Lambda invocation gets one chance, so the stored login/TOTP
 * records are read here and all unique IPs (current and previous) are resolved
 * in one ip-api batch. Detectors then read synchronously from the invocation's
 * map.
 */
async function prepareBatch(
  records: ParsedRecord[],
  services: SecurityServices,
  invocation: SecurityInvocation,
): Promise<void> {
  const ips = new Set<string>();

  for (const record of records) {
    const body = bodyOf(record);
    switch (record.message.eventType) {
      case EventTypes.AuthenticationSucceeded: {
        const accountId = asString(body.account_id);
        if (accountId) {
          const previous = await services.store.getLastLogin(accountId);
          if (previous) ips.add(previous.ip);
        }
        const ip = asString(body.ip_address);
        if (ip) ips.add(ip);
        break;
      }

      case EventTypes.AccountTOTPEnabled: {
        const ip = asString(body.ip_address);
        if (ip) ips.add(ip);
        break;
      }

      case EventTypes.AccountTOTPDisabled: {
        const ip = asString(body.ip_address);
        if (ip) ips.add(ip);
        const accountId = asString(body.account_id);
        if (accountId) {
          const enableRecord = await services.store.getTotpEnable(accountId);
          if (enableRecord && enableRecord.ip !== "unknown") ips.add(enableRecord.ip);
        }
        break;
      }

      default:
        break;
    }
  }

  await invocation.geo.prefetch(ips);
}

export function securityBatchDefinition(
  services: SecurityServices,
): BatchDefinition<SecurityInvocation> {
  return {
    moduleName: "security",
    knownTypes: SECURITY_EVENT_TYPES,
    createContext: (): SecurityInvocation => ({
      geo: new GeoLocator({
        redis: services.redis,
        scripts: services.scripts,
        baseUrl: services.config.ipApiBaseUrl,
        logger: createLogger("security.geo"),
      }),
    }),
    prepare: (records, invocation) => prepareBatch(records, services, invocation),
    process: async (record, invocation) => {
      await services.worker.handle(record.message.eventType, bodyOf(record), invocation.geo);
    },
  };
}

export const handler = async (event: SQSEvent, context: Context): Promise<SQSBatchResponse> => {
  initTelemetry("security");
  try {
    const services = await getSecurityServices();
    return await runBatch(event, context, securityBatchDefinition(services));
  } finally {
    await flushTelemetry();
  }
};

/** Test hook: drop the cold-start singleton. */
export function resetSecurityServicesForTests(): void {
  servicesPromise = undefined;
}

