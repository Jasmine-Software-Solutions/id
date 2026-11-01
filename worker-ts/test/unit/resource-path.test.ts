import { describe, expect, it } from "vitest";
import { randomUUID } from "node:crypto";
import {
  compilePattern,
  compileResource,
  parseJavaUuid,
  resourcePathToString,
  uuidFromBytes,
  uuidToBytes,
  type ResourceNode,
  type WildcardNode,
} from "../../src/modules/witchwood/resource-path";

/**
 * Port of Witchwood's ResourcePathCompilerTest (all cases).
 */
describe("ResourcePathCompiler — compileResource", () => {
  it("compiles a simple resource", () => {
    const id = randomUUID();
    const path = compileResource(`svc://bucket:${id}`);

    expect(path.service).toBe("svc");
    const node = path.root as ResourceNode;
    expect(node.kind).toBe("resource");
    expect(node.type).toBe("bucket");
    expect(node.resourceId).toEqual({ kind: "definite", id });
    expect(node.child).toBeNull();
  });

  it("compiles a multi-segment resource", () => {
    const id1 = randomUUID();
    const id2 = randomUUID();
    const path = compileResource(`iam://account:${id1}/user:${id2}`);

    const root = path.root as ResourceNode;
    expect(root.type).toBe("account");
    expect(root.resourceId).toEqual({ kind: "definite", id: id1 });

    const child = root.child as ResourceNode;
    expect(child.type).toBe("user");
    expect(child.resourceId).toEqual({ kind: "definite", id: id2 });
    expect(child.child).toBeNull();
  });

  it("rejects a wildcard node", () => {
    expect(() => compileResource("svc://*")).toThrow();
  });

  it("rejects a greedy wildcard", () => {
    expect(() => compileResource("svc://**")).toThrow();
  });

  it("rejects a wildcard id", () => {
    expect(() => compileResource("svc://bucket:*")).toThrow();
  });

  it("allows a trailing slash", () => {
    const id = randomUUID();
    const path = compileResource(`svc://bucket:${id}/`);

    const node = path.root as ResourceNode;
    expect(node.type).toBe("bucket");
    expect(node.resourceId).toEqual({ kind: "definite", id });
  });

  it("compiles an empty resource path", () => {
    const path = compileResource("svc://");
    expect(path.service).toBe("svc");
    expect(path.root).toBeNull();
  });
});

describe("ResourcePathCompiler — compilePattern", () => {
  it("compiles a wildcard node", () => {
    const path = compilePattern("svc://*");
    const node = path.root as WildcardNode;
    expect(node.kind).toBe("wildcard");
    expect(node.greedy).toBe(false);
    expect(node.child).toBeNull();
  });

  it("compiles a greedy wildcard", () => {
    const path = compilePattern("svc://**");
    const node = path.root as WildcardNode;
    expect(node.greedy).toBe(true);
    expect(node.child).toBeNull();
  });

  it("compiles a wildcard id", () => {
    const path = compilePattern("svc://bucket:*");
    const node = path.root as ResourceNode;
    expect(node.type).toBe("bucket");
    expect(node.resourceId).toEqual({ kind: "wildcard-id" });
  });

  it("compiles mixed segments", () => {
    const id = randomUUID();
    const path = compilePattern(`storage://bucket:${id}/*`);

    const root = path.root as ResourceNode;
    expect(root.type).toBe("bucket");
    expect(root.resourceId).toEqual({ kind: "definite", id });

    const child = root.child as WildcardNode;
    expect(child.kind).toBe("wildcard");
    expect(child.greedy).toBe(false);
    expect(child.child).toBeNull();
  });

  it("compiles a greedy wildcard followed by a node", () => {
    const id = randomUUID();
    const path = compilePattern(`svc://**/bucket:${id}`);

    const root = path.root as WildcardNode;
    expect(root.greedy).toBe(true);

    const child = root.child as ResourceNode;
    expect(child.type).toBe("bucket");
    expect(child.resourceId).toEqual({ kind: "definite", id });
  });

  it("allows regular segments", () => {
    const id = randomUUID();
    const path = compilePattern(`svc://bucket:${id}`);

    const node = path.root as ResourceNode;
    expect(node.type).toBe("bucket");
    expect(node.resourceId).toEqual({ kind: "definite", id });
  });
});

describe("ResourcePathCompiler — invalid paths", () => {
  it.each(["", "svc", "svc:", "://", "://bucket:id"])("throws for %j", (path) => {
    expect(() => compileResource(path)).toThrow();
  });

  it("throws for an invalid UUID", () => {
    expect(() => compileResource("svc://bucket:not-a-uuid")).toThrow();
  });

  it("throws when the type separator is missing", () => {
    expect(() =>
      compileResource(`svc://bucket${randomUUID().replace(/-/g, "")}`),
    ).toThrow();
  });
});

describe("ResourcePathCompiler — identity and round trips", () => {
  it("identical paths stringify identically", () => {
    const id = randomUUID();
    const path = `svc://account:${id}/user:${randomUUID()}`;
    expect(resourcePathToString(compileResource(path))).toBe(
      resourcePathToString(compileResource(path)),
    );
  });

  it("different services, ids and lengths differ", () => {
    const id = randomUUID();
    const a = resourcePathToString(compileResource(`svc1://bucket:${id}`));
    const b = resourcePathToString(compileResource(`svc2://bucket:${id}`));
    expect(a).not.toBe(b);

    const c = resourcePathToString(compileResource(`svc://bucket:${randomUUID()}`));
    expect(resourcePathToString(compileResource(`svc://bucket:${randomUUID()}`))).not.toBe(c);

    const short = resourcePathToString(compileResource(`svc://bucket:${id}`));
    const long = resourcePathToString(compileResource(`svc://bucket:${id}/obj:${randomUUID()}`));
    expect(short).not.toBe(long);
  });

  it("round-trips a resource through toString", () => {
    const original = `svc://bucket:${randomUUID()}`;
    const compiled = compileResource(original);
    const roundTripped = compileResource(resourcePathToString(compiled));
    expect(resourcePathToString(roundTripped)).toBe(resourcePathToString(compiled));
  });

  it("round-trips a pattern through toString", () => {
    const compiled = compilePattern("svc://bucket:*/**");
    const roundTripped = compilePattern(resourcePathToString(compiled));
    expect(resourcePathToString(roundTripped)).toBe(resourcePathToString(compiled));
  });
});

describe("Java UUID compatibility and wire bytes", () => {
  it("accepts abbreviated groups like UUID.fromString", () => {
    expect(parseJavaUuid("1-2-3-4-5")).toBe("00000001-0002-0003-0004-000000000005");
    expect(parseJavaUuid("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF")).toBe(
      "ffffffff-ffff-ffff-ffff-ffffffffffff",
    );
  });

  it("rejects malformed UUIDs", () => {
    expect(() => parseJavaUuid("not-a-uuid")).toThrow();
    expect(() => parseJavaUuid("1-2-3-4")).toThrow();
    expect(() => parseJavaUuid("1-2-3-4-5-6")).toThrow();
  });

  it("encodes UUIDs as 16 big-endian bytes", () => {
    const uuid = "00112233-4455-6677-8899-aabbccddeeff";
    expect(uuidToBytes(uuid).toString("hex")).toBe("00112233445566778899aabbccddeeff");
    expect(uuidFromBytes(uuidToBytes(uuid))).toBe(uuid);
  });
});
