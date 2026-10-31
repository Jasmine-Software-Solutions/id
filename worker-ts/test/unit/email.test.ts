import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  escapeAttribute,
  escapeHtml,
  renderCredentialStuffingEmail,
  renderEmailChangeEmail,
  renderInvitationEmail,
  renderMagicLinkEmail,
} from "../../src/modules/email/templates";
import {
  sendCredentialStuffingNotice,
  sendEmailChangeVerification,
  sendInvitation,
  sendMagicLink,
} from "../../src/modules/email/handlers";
import { ResendEmailSender } from "../../src/modules/email/resend-sender";
import type { Logger } from "../../src/runtime/logging";
import inputs from "../fixtures/emails/inputs.json";

const fixture = (name: string): string =>
  readFileSync(path.join(__dirname, "..", "fixtures", "emails", `${name}.html`), "utf8");

class RecordingLogger implements Logger {
  public readonly lines: string[] = [];
  debug(message: string): void {
    this.lines.push(`debug:${message}`);
  }
  info(message: string): void {
    this.lines.push(`info:${message}`);
  }
  warn(message: string): void {
    this.lines.push(`warn:${message}`);
  }
  error(message: string): void {
    this.lines.push(`error:${message}`);
  }
}

class RecordingSender {
  public readonly sent: { to: string; subject: string; html: string }[] = [];
  async send(to: string, subject: string, html: string): Promise<void> {
    this.sent.push({ to, subject, html });
  }
}

describe("email templates match the Java (JTE) goldens byte-for-byte", () => {
  it("magic link", () => {
    expect(renderMagicLinkEmail(inputs.magic_link.decisionUrl)).toBe(fixture("magic_link"));
  });

  it("email change verification", () => {
    expect(renderEmailChangeEmail(inputs.email_change)).toBe(fixture("email_change"));
  });

  it("invitation", () => {
    expect(renderInvitationEmail(inputs.invitation)).toBe(fixture("invitation"));
  });

  it("credential stuffing notice", () => {
    expect(renderCredentialStuffingEmail(inputs.credential_stuffing.resetUrl)).toBe(
      fixture("credential_stuffing"),
    );
  });

  it("uses JTE's exact escaping rules", () => {
    // gg.jte.html.escape.Escape#htmlContent
    expect(escapeHtml(`a&b<c>d"e'f`)).toBe(`a&amp;b&lt;c&gt;d"e'f`);
    // gg.jte.html.escape.Escape#htmlAttribute
    expect(escapeAttribute(`a&b<c>d"e'f`)).toBe(`a&amp;b&lt;c>d&#34;e&#39;f`);
  });
});

describe("email handlers", () => {
  const base = "https://id.jasmine.software";
  const sender = new RecordingSender();

  it("composes the magic-link mail", async () => {
    await sendMagicLink(
      {
        email: "person@example.com",
        magic_link_id: "11111111-1111-1111-1111-111111111111",
        token: "abc123",
      },
      sender,
      base,
    );

    expect(sender.sent.at(-1)).toEqual({
      to: "person@example.com",
      subject: "Jasmine ID Sign-On Link",
      html: fixture("magic_link"),
    });
  });

  it("composes the email-change mail to the new address", async () => {
    await sendEmailChangeVerification(
      { account_id: "22222222-2222-2222-2222-222222222222", new_email: "new@example.com" },
      sender,
      base,
    );

    expect(sender.sent.at(-1)).toEqual({
      to: "new@example.com",
      subject: "Verify your email address",
      html: fixture("email_change"),
    });
  });

  it("composes the invitation mail", async () => {
    await sendInvitation(
      {
        email: "invitee@example.com",
        invitation_id: "33333333-3333-3333-3333-333333333333",
        tenant_name: "Acme & Sons",
      },
      sender,
      base,
    );

    expect(sender.sent.at(-1)).toEqual({
      to: "invitee@example.com",
      subject: "You've been invited to join Acme & Sons on Jasmine Identity",
      html: fixture("invitation"),
    });
  });

  it("composes the credential-stuffing notice with the no-hash URL", async () => {
    await sendCredentialStuffingNotice({ email: "person@example.com" }, sender, base);

    expect(sender.sent.at(-1)).toEqual({
      to: "person@example.com",
      subject: "Security Alert — Your Account Was Protected",
      html: fixture("credential_stuffing"),
    });
  });
});

describe("Resend sender", () => {
  const logger = new RecordingLogger();

  it("logs instead of POSTing when the API key is blank", async () => {
    let calls = 0;
    const sender = new ResendEmailSender({
      apiKey: "",
      senderName: "Jasmine ID",
      senderEmail: "no-reply@example.com",
      baseUrl: "https://api.resend.com",
      fetchImpl: (async () => {
        calls += 1;
        throw new Error("should not be called");
      }) as unknown as typeof fetch,
      logger,
    });

    await sender.send("person@example.com", "Subject", "<html>body</html>");
    expect(calls).toBe(0);
    expect(logger.lines.some((line) => line.includes("[dev-mail] to=person@example.com"))).toBe(true);
  });

  it("POSTs the Resend payload on success", async () => {
    const requests: { url: string; init: RequestInit }[] = [];
    const sender = new ResendEmailSender({
      apiKey: "key",
      senderName: "Jasmine ID",
      senderEmail: "no-reply@example.com",
      baseUrl: "https://api.resend.test",
      fetchImpl: (async (url: string, init: RequestInit) => {
        requests.push({ url, init });
        return new Response("{}", { status: 200 });
      }) as unknown as typeof fetch,
      logger,
    });

    await sender.send("person@example.com", "Subject", "<html>body</html>");

    expect(requests).toHaveLength(1);
    expect(requests[0]!.url).toBe("https://api.resend.test/emails");
    expect((requests[0]!.init.headers as Record<string, string>).Authorization).toBe("Bearer key");
    expect(JSON.parse(String(requests[0]!.init.body))).toEqual({
      from: "Jasmine ID <no-reply@example.com>",
      to: ["person@example.com"],
      subject: "Subject",
      html: "<html>body</html>",
    });
  });

  it("swallows non-2xx responses and network errors", async () => {
    const failing = new ResendEmailSender({
      apiKey: "key",
      senderName: "",
      senderEmail: "",
      baseUrl: "https://api.resend.test",
      fetchImpl: (async () => new Response("nope", { status: 422 })) as unknown as typeof fetch,
      logger,
    });
    await expect(failing.send("a@b.c", "S", "H")).resolves.toBeUndefined();

    const throwing = new ResendEmailSender({
      apiKey: "key",
      senderName: "",
      senderEmail: "",
      baseUrl: "https://api.resend.test",
      fetchImpl: (async () => {
        throw new Error("network");
      }) as unknown as typeof fetch,
      logger,
    });
    await expect(throwing.send("a@b.c", "S", "H")).resolves.toBeUndefined();
  });
});
