import { env, envInt, envOr } from "./env";

export interface WitchwoodConfig {
  moduleName: "witchwood";
  ssmPrefix: string;
  host: string;
  port: number;
  /** Base64 PKCS#8 DER key from the environment (local runs); production reads SSM. */
  jwtEncodedPrivateKey?: string;
  protoRoot?: string;
}

export function loadWitchwoodConfig(): WitchwoodConfig {
  return {
    moduleName: "witchwood",
    ssmPrefix: envOr("SSM_PARAMETER_PREFIX", ""),
    host: envOr("WITCHWOOD_HOST", "localhost"),
    port: envInt("WITCHWOOD_PORT", 50051),
    jwtEncodedPrivateKey: env("JWT_ENCODED_PRIVATE_KEY"),
    protoRoot: env("PROTO_ROOT"),
  };
}
