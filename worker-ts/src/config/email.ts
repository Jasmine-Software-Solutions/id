import { env, envOr } from "./env";

export interface EmailConfig {
  moduleName: "email";
  ssmPrefix: string;
  /** API key from the environment (local runs); production reads SSM. */
  resendApiKey?: string;
  resendBaseUrl: string;
  senderName: string;
  senderEmail: string;
  idServerBaseUrl: string;
}

export function loadEmailConfig(): EmailConfig {
  return {
    moduleName: "email",
    ssmPrefix: envOr("SSM_PARAMETER_PREFIX", ""),
    resendApiKey: env("RESEND_API_KEY"),
    resendBaseUrl: envOr("RESEND_BASE_URL", "https://api.resend.com"),
    senderName: envOr("RESEND_SENDER_NAME", ""),
    senderEmail: envOr("RESEND_SENDER_EMAIL", ""),
    // Matches EmailWorkerEnv.Default.
    idServerBaseUrl: envOr("ID_SERVER_BASE_URL", "http://localhost:80"),
  };
}
