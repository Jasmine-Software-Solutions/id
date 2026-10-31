import { describe, expect, it } from "vitest";
import { PolicyCompiler } from "../../src/modules/witchwood/policy-compiler";
import { statementToProto } from "../../src/modules/witchwood/proto-codec";
import { uuidToBytes } from "../../src/modules/witchwood/resource-path";
import { randomUUID } from "node:crypto";

const compiler = new PolicyCompiler();

describe("PolicyCompiler", () => {
  it("uses positional ids and defaults the effect to ALLOW", () => {
    const statements = compiler.compile([
      { actions: ["a:One"], resources: ["svc://bucket:00000000-0000-0000-0000-000000000001"] },
      {
        effect: "deny",
        actions: ["a:Two"],
        resources: ["svc://bucket:00000000-0000-0000-0000-000000000002"],
      },
    ]);

    expect(statements.map((s) => s.id)).toEqual([0, 1]);
    expect(statements.map((s) => s.effect)).toEqual(["ALLOW", "DENY"]);
  });

  it("de-duplicates actions and resources preserving order", () => {
    const id = randomUUID();
    const [statement] = compiler.compile([
      {
        actions: ["a:One", "a:Two", "a:One"],
        resources: [`svc://bucket:${id}`, `svc://bucket:${id}`],
      },
    ]);

    expect(statement!.actions).toEqual(["a:One", "a:Two"]);
    expect(statement!.resources).toHaveLength(1);
  });

  it("compiles wildcard resources as patterns", () => {
    const [statement] = compiler.compile([
      { actions: ["a:One"], resources: ["svc://bucket:*/**"] },
    ]);

    expect(statement!.resources[0]!.root).toMatchObject({ kind: "resource", type: "bucket" });
  });

  it("serializes a condition as compact JSON", () => {
    const [statement] = compiler.compile([
      {
        actions: ["a"],
        resources: ["svc://"],
        condition: { stringEquals: { "request.ip": "1.2.3.4" } },
      },
    ]);
    expect(statement!.condition).toBe('{"stringEquals":{"request.ip":"1.2.3.4"}}');
    expect(statementToProto(statement!).condition).toEqual({
      json: '{"stringEquals":{"request.ip":"1.2.3.4"}}',
    });
  });

  it("omits a missing condition (empty string)", () => {
    const [statement] = compiler.compile([{ actions: ["a"], resources: ["svc://"] }]);
    expect(statement!.condition).toBe("");
    expect(statementToProto(statement!)).not.toHaveProperty("condition");
  });

  it("rejects malformed documents", () => {
    expect(() => compiler.compile({})).toThrow();
    expect(() => compiler.compile([{ actions: "nope", resources: [] }])).toThrow();
    expect(() => compiler.compile([{ actions: [], resources: "nope" }])).toThrow();
  });
});

describe("statementToProto", () => {
  it("encodes definite ids as 16-byte Buffers and wildcards via oneofs", () => {
    const id = "00112233-4455-6677-8899-aabbccddeeff";
    const [statement] = compiler.compile([
      {
        actions: ["a:One"],
        resources: [`svc://bucket:${id}/**`, "svc://bucket:*"],
      },
    ]);

    const proto = statementToProto(statement!);
    const first = proto.resources[0]!;

    expect(first.service).toBe("svc");
    expect(first.root).toMatchObject({ typed: { type: "bucket" } });
    expect(first.root!.typed!.definiteId).toEqual(uuidToBytes(id));
    expect(Buffer.isBuffer(first.root!.typed!.definiteId)).toBe(true);
    expect(first.root!.typed!.wildcardId).toBeUndefined();

    const greedy = first.root!.typed!.child!;
    expect(greedy.wildcard).toEqual({ greedy: true });

    const second = proto.resources[1]!;
    expect(second.root!.typed!.wildcardId).toBe(true);
    expect(second.root!.typed!.definiteId).toBeUndefined();
  });
});
