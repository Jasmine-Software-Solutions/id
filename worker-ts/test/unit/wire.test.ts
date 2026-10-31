import { describe, expect, it } from "vitest";
import {
  asInstant,
  MalformedEventError,
  toWire,
} from "../../src/wire/codec";
import { parseMessage } from "../../src/wire/envelope";
import { parseBatch } from "../../src/wire/parse";
import { EventTypes, SECURITY_EVENT_TYPES } from "../../src/wire/event-types";
import { serializeEvent, SYSTEM_PRINCIPAL_ID } from "../../src/wire/publisher";
import type { SQSEvent, SQSRecord } from "aws-lambda";

function sqsRecord(body: string, attributes: Record<string, string> = {}, id = "msg-1"): SQSRecord {
  return {
    messageId: id,
    receiptHandle: "rh",
    body,
    attributes: {
      ApproximateReceiveCount: "1",
      SentTimestamp: "0",
      SenderId: "sender",
      ApproximateFirstReceiveTimestamp: "0",
    },
    messageAttributes: Object.fromEntries(
      Object.entries(attributes).map(([key, value]) => [
        key,
        { stringValue: value, dataType: "String" },
      ]),
    ),
    md5OfBody: "",
    eventSource: "aws:sqs",
    eventSourceARN: "arn:aws:sqs:us-east-1:123:q",
    awsRegion: "us-east-1",
  };
}

function sqsEvent(records: SQSRecord[]): SQSEvent {
  return { Records: records };
}

describe("codec", () => {
  it("serializes camelCase as snake_case and Dates as epoch seconds", () => {
    expect(
      toWire({
        eventId: "abc",
        accountId: "acct",
        timestamp: new Date(1_700_000_000_000),
        nested: { ipAddress: "1.2.3.4", loginAt: new Date(1_700_000_001_999) },
        list: [new Date(0)],
      }),
    ).toEqual({
      event_id: "abc",
      account_id: "acct",
      timestamp: 1_700_000_000,
      nested: { ip_address: "1.2.3.4", login_at: 1_700_000_001 },
      list: [0],
    });
  });

  it("omits nulls and undefined like Gson", () => {
    expect(toWire({ present: 1, missing: undefined, nil: null })).toEqual({ present: 1 });
  });

  it("reads epoch-second instants", () => {
    expect(asInstant(1_700_000_000)?.getTime()).toBe(1_700_000_000_000);
    expect(asInstant("1700000000")?.getTime()).toBe(1_700_000_000_000);
    expect(asInstant(null)).toBeUndefined();
  });
});

describe("envelope parsing", () => {
  it("parses a raw event by body event_type", () => {
    const parsed = parseMessage(
      JSON.stringify({ event_type: EventTypes.AccountCreated, account_id: "a" }),
    );
    expect(parsed.eventType).toBe(EventTypes.AccountCreated);
    expect(parsed.body.account_id).toBe("a");
  });

  it("parses an SNS envelope", () => {
    const parsed = parseMessage(
      JSON.stringify({
        Type: "Notification",
        Message: JSON.stringify({ event_type: EventTypes.RoleCreated }),
      }),
    );
    expect(parsed.eventType).toBe(EventTypes.RoleCreated);
  });

  it("falls back to legacy eventType in the body", () => {
    const parsed = parseMessage(JSON.stringify({ eventType: EventTypes.RoleCreated }));
    expect(parsed.eventType).toBe(EventTypes.RoleCreated);
  });

  it("falls back to the SQS message attribute", () => {
    // The JVM worker published events without a body discriminator; these may
    // still be queued when the new consumer starts.
    const parsed = parseMessage(JSON.stringify({ account_id: "a" }), {
      event_type: { stringValue: EventTypes.AccountCreated },
    });
    expect(parsed.eventType).toBe(EventTypes.AccountCreated);
  });

  it("prefers body event_type over the attribute", () => {
    const parsed = parseMessage(JSON.stringify({ event_type: EventTypes.RoleCreated }), {
      event_type: { stringValue: EventTypes.AccountCreated },
    });
    expect(parsed.eventType).toBe(EventTypes.RoleCreated);
  });

  it("rejects malformed JSON and missing discriminators", () => {
    expect(() => parseMessage("{nope")).toThrow(MalformedEventError);
    expect(() => parseMessage(JSON.stringify({ hello: 1 }))).toThrow(/missing event_type/);
  });
});

describe("parseBatch", () => {
  it("skips malformed and unknown event types", () => {
    const event = sqsEvent([
      sqsRecord(JSON.stringify({ event_type: EventTypes.AccountCreated })),
      sqsRecord("not-json", {}, "msg-2"),
      sqsRecord(JSON.stringify({ event_type: "SomethingElse" }), {}, "msg-3"),
    ]);

    const result = parseBatch(event, SECURITY_EVENT_TYPES);
    expect(result.parsed.map((p) => p.record.messageId)).toEqual(["msg-1"]);
    expect(result.skipped.map((s) => s.messageId)).toEqual(["msg-2", "msg-3"]);
  });
});

describe("publisher serialization", () => {
  it("stamps event_type and uses snake_case / epoch seconds", () => {
    const body = serializeEvent({
      eventType: EventTypes.RestrictionPlaced,
      eventId: "11111111-1111-1111-1111-111111111111",
      principalId: SYSTEM_PRINCIPAL_ID,
      action: "id.jasmine.software:RestrictionPlaced",
      resource: "id.jasmine.software://",
      timestamp: new Date(1_700_000_000_000),
      restrictionId: "22222222-2222-2222-2222-222222222222",
      durationMinutes: 30,
      triggerEventId: null,
    });
    const parsed = JSON.parse(body);

    expect(parsed).toMatchObject({
      event_type: EventTypes.RestrictionPlaced,
      event_id: "11111111-1111-1111-1111-111111111111",
      principal_id: SYSTEM_PRINCIPAL_ID,
      action: "id.jasmine.software:RestrictionPlaced",
      resource: "id.jasmine.software://",
      timestamp: 1_700_000_000,
      restriction_id: "22222222-2222-2222-2222-222222222222",
      duration_minutes: 30,
    });
    expect(parsed).not.toHaveProperty("trigger_event_id");
  });

  it("does not duplicate an event_type already present", () => {
    const body = serializeEvent({
      eventType: EventTypes.RoleCreated,
      eventId: "e",
      principalId: SYSTEM_PRINCIPAL_ID,
      action: "a",
      resource: "r",
      timestamp: new Date(0),
      event_type: EventTypes.RoleCreated,
    });
    expect(JSON.parse(body).event_type).toBe(EventTypes.RoleCreated);
  });
});
