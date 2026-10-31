import { createLogger } from "../../runtime/logging";
import type { Wire } from "../../wire/codec";
import { EventTypes } from "../../wire/event-types";
import {
  sendCredentialStuffingNotice,
  sendEmailChangeVerification,
  sendInvitation,
  sendMagicLink,
} from "./handlers";
import type { EmailSender } from "./resend-sender";

/**
 * Routes email events to their handlers — the port of EmailWorker.
 */
export class EmailWorker {
  private readonly logger = createLogger("email.worker");

  constructor(
    private readonly sender: EmailSender,
    private readonly baseUrl: string,
  ) {}

  async handle(eventType: string, body: Wire): Promise<void> {
    switch (eventType) {
      case EventTypes.MagicLinkRequested:
        await sendMagicLink(body, this.sender, this.baseUrl);
        return;
      case EventTypes.AccountEmailChangeStarted:
        await sendEmailChangeVerification(body, this.sender, this.baseUrl);
        return;
      case EventTypes.InvitationCreated:
        await sendInvitation(body, this.sender, this.baseUrl);
        return;
      case EventTypes.CredentialStuffingSucceeded:
        await sendCredentialStuffingNotice(body, this.sender, this.baseUrl);
        return;
      default:
        this.logger.debug("EmailWorker ignoring event", { event_type: eventType });
    }
  }
}
