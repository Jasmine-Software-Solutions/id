import type { Wire } from "../../wire/codec";
import { requiredString } from "../../wire/codec";
import type { EmailSender } from "./resend-sender";
import {
  renderCredentialStuffingEmail,
  renderEmailChangeEmail,
  renderInvitationEmail,
  renderMagicLinkEmail,
} from "./templates";

/**
 * The four transactional mails, ported 1:1 from the JVM handlers. URL shapes
 * and subjects are part of the contract (golden-tested).
 */

export async function sendMagicLink(
  body: Wire,
  sender: EmailSender,
  baseUrl: string,
): Promise<void> {
  const email = requiredString(body, "email");
  const magicLinkId = requiredString(body, "magic_link_id");
  const token = requiredString(body, "token");

  const decisionUrl = `${baseUrl}/#/magic-links/${magicLinkId}?token=${token}`;
  await sender.send(email, "Jasmine ID Sign-On Link", renderMagicLinkEmail(decisionUrl));
}

export async function sendEmailChangeVerification(
  body: Wire,
  sender: EmailSender,
  baseUrl: string,
): Promise<void> {
  const accountId = requiredString(body, "account_id");
  const newEmail = requiredString(body, "new_email");

  const verifyUrl = `${baseUrl}/#/email-change/verify?changeId=${accountId}&newEmail=${newEmail}`;
  await sender.send(
    newEmail,
    "Verify your email address",
    renderEmailChangeEmail({ verifyUrl, newEmail }),
  );
}

export async function sendInvitation(
  body: Wire,
  sender: EmailSender,
  baseUrl: string,
): Promise<void> {
  const email = requiredString(body, "email");
  const invitationId = requiredString(body, "invitation_id");
  const tenantName = requiredString(body, "tenant_name");

  const acceptUrl = `${baseUrl}/#/invitations/${invitationId}`;
  await sender.send(
    email,
    `You've been invited to join ${tenantName} on Jasmine Identity`,
    renderInvitationEmail({ acceptUrl, tenantName }),
  );
}

export async function sendCredentialStuffingNotice(
  body: Wire,
  sender: EmailSender,
  baseUrl: string,
): Promise<void> {
  const email = requiredString(body, "email");
  const resetUrl = `${baseUrl}/account/reset-password`;
  await sender.send(
    email,
    "Security Alert — Your Account Was Protected",
    renderCredentialStuffingEmail(resetUrl),
  );
}
