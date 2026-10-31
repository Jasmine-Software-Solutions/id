import { createLogger, type Logger } from "../../runtime/logging";

export interface EmailSender {
  send(to: string, subject: string, html: string): Promise<void>;
}

export interface ResendSenderOptions {
  apiKey: string;
  senderName: string;
  senderEmail: string;
  baseUrl: string;
  fetchImpl?: typeof fetch;
  logger?: Logger;
}

/**
 * Resend API sender.
 *
 * A blank API key is a supported dev configuration: the rendered mail (with its
 * magic-link / invitation URL) is logged instead of POSTed. Every failure is
 * logged and swallowed — the JVM worker never let a mail failure retry the
 * event, and the email function's ESM would redeliver on a throw.
 */
export class ResendEmailSender implements EmailSender {
  private readonly logger: Logger;
  private readonly fetchImpl: typeof fetch;

  constructor(private readonly options: ResendSenderOptions) {
    this.logger = options.logger ?? createLogger("email.resend");
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  async send(to: string, subject: string, html: string): Promise<void> {
    if (!this.options.apiKey) {
      this.logger.info(`[dev-mail] to=${to} subject=${subject}\n${html}`);
      return;
    }

    const from = `${this.options.senderName} <${this.options.senderEmail}>`;
    const body = JSON.stringify({ from, to: [to], subject, html });

    try {
      const response = await this.fetchImpl(
        `${this.options.baseUrl.replace(/\/+$/, "")}/emails`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${this.options.apiKey}`,
            "Content-Type": "application/json",
          },
          body,
          signal: AbortSignal.timeout(10_000),
        },
      );

      if (response.status >= 200 && response.status < 300) {
        this.logger.info("Email sent", { to, subject });
      } else {
        const text = await response.text().catch(() => "");
        this.logger.error("Resend API error", { status: response.status, body: text, to, subject });
      }
    } catch (error) {
      this.logger.error("Failed to send email", { to, subject, error });
    }
  }
}
