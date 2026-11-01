import { describe, expect, it, vi } from "vitest";
import type { Context, SQSEvent, SQSRecord } from "aws-lambda";
import { runBatch } from "../../src/runtime/batch-processor";
import { EventTypes } from "../../src/wire/event-types";

function record(id: string, eventType: string = EventTypes.AccountCreated): SQSRecord {
  return {
    messageId: id,
    receiptHandle: "rh",
    body: JSON.stringify({ event_type: eventType, account_id: id }),
    attributes: {
      ApproximateReceiveCount: "1",
      SentTimestamp: "0",
      SenderId: "sender",
      ApproximateFirstReceiveTimestamp: "0",
    },
    messageAttributes: {},
    md5OfBody: "",
    eventSource: "aws:sqs",
    eventSourceARN: "arn:aws:sqs:us-east-1:123:q",
    awsRegion: "us-east-1",
  };
}

function event(...records: SQSRecord[]): SQSEvent {
  return { Records: records };
}

function context(remaining: number | (() => number) = 30_000): Context {
  return {
    callbackWaitsForEmptyEventLoop: true,
    functionName: "test",
    functionVersion: "1",
    invokedFunctionArn: "arn",
    memoryLimitInMB: "128",
    awsRequestId: "req",
    logGroupName: "log",
    logStreamName: "stream",
    getRemainingTimeInMillis: typeof remaining === "function" ? remaining : () => remaining,
    done: () => undefined,
    fail: () => undefined,
    succeed: () => undefined,
  };
}

const knownTypes = new Set([EventTypes.AccountCreated]);

describe("runBatch", () => {
  it("reports exactly the message whose handler threw", async () => {
    const processed: string[] = [];
    const response = await runBatch(
      event(record("1"), record("2"), record("3")),
      context(),
      {
        moduleName: "test",
        knownTypes,
        createContext: () => undefined,
        process: async (parsed) => {
          processed.push(parsed.record.messageId);
          if (parsed.record.messageId === "2") throw new Error("boom");
        },
      },
    );

    expect(processed).toEqual(["1", "2", "3"]);
    expect(response.batchItemFailures).toEqual([{ itemIdentifier: "2" }]);
  });

  it("acks malformed and unknown records", async () => {
    const process = vi.fn(async () => undefined);
    const malformed: SQSRecord = { ...record("bad"), body: "{oops" };
    const unknown = record("unknown", "NotAnEvent");

    const response = await runBatch(event(malformed, unknown, record("ok")), context(), {
      moduleName: "test",
      knownTypes,
      createContext: () => undefined,
      process,
    });

    expect(process).toHaveBeenCalledTimes(1);
    expect(response.batchItemFailures).toEqual([]);
  });

  it("fails the tail when the deadline margin is gone", async () => {
    let remaining = 30_000;
    const processed: string[] = [];

    const response = await runBatch(
      event(record("1"), record("2"), record("3")),
      context(() => remaining),
      {
        moduleName: "test",
        knownTypes,
        createContext: () => undefined,
        process: async (parsed) => {
          processed.push(parsed.record.messageId);
          remaining = 2_000; // consumed the margin
        },
      },
    );

    expect(processed).toEqual(["1"]);
    expect(response.batchItemFailures).toEqual([
      { itemIdentifier: "2" },
      { itemIdentifier: "3" },
    ]);
  });

  it("fails every record when preparation fails", async () => {
    const response = await runBatch(event(record("1"), record("2")), context(), {
      moduleName: "test",
      knownTypes,
      createContext: () => undefined,
      prepare: async () => {
        throw new Error("redis down");
      },
      process: async () => undefined,
    });

    expect(response.batchItemFailures.map((f) => f.itemIdentifier)).toEqual(["1", "2"]);
  });
});
