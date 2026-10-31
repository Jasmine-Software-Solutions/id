/**
 * Port of Witchwood's `CompiledResourcePath` / `ResourcePathCompiler`.
 *
 * Path grammar: `service://type:id/type:id`, wildcards `*` (one node) and
 * `**` (zero or more) in patterns, `type:*` for any id of a type. UUID parsing
 * follows `java.util.UUID.fromString`, which accepts abbreviated groups
 * (`1-2-3-4-5`) — policy documents written by Java clients must not be
 * rejected by a stricter parser here.
 */

export const SERVICE_SEPARATOR = "://";
export const RESOURCE_SEPARATOR = "/";
export const TYPE_ID_SEPARATOR = ":";

export interface WildcardNode {
  kind: "wildcard";
  greedy: boolean;
  child: Node | null;
}

export interface ResourceNode {
  kind: "resource";
  type: string;
  resourceId: ResourceId;
  child: Node | null;
}

export interface DefiniteId {
  kind: "definite";
  /** Canonical lowercase UUID string, as Java's UUID.toString(). */
  id: string;
}

export interface WildcardId {
  kind: "wildcard-id";
}

export type ResourceId = DefiniteId | WildcardId;

export type Node = WildcardNode | ResourceNode;

export interface CompiledResourcePath {
  service: string;
  root: Node | null;
}

export function compileResource(path: string): CompiledResourcePath {
  return compile(path, false);
}

export function compilePattern(path: string): CompiledResourcePath {
  return compile(path, true);
}

function compile(path: string, pattern: boolean): CompiledResourcePath {
  const separator = path.indexOf(SERVICE_SEPARATOR);
  if (separator <= 0) {
    throw new Error(`Invalid resource path: ${path}`);
  }

  const service = path.slice(0, separator);
  const resourcePath = path.slice(separator + 3);

  if (resourcePath.length === 0) {
    return { service, root: null };
  }

  let child: Node | null = null;
  let end = resourcePath.length;
  if (resourcePath.charAt(end - 1) === RESOURCE_SEPARATOR) end -= 1;

  while (end > 0) {
    const start = resourcePath.lastIndexOf(RESOURCE_SEPARATOR, end - 1);
    child = compileSegment(resourcePath, start + 1, end, child, pattern);
    if (start < 0) break;
    end = start;
  }

  return { service, root: child };
}

function compileSegment(
  path: string,
  start: number,
  end: number,
  child: Node | null,
  pattern: boolean,
): Node {
  const length = end - start;

  if (length === 1 && path.charAt(start) === "*") {
    requirePattern(pattern, "*");
    return { kind: "wildcard", greedy: false, child };
  }

  if (length === 2 && path.charAt(start) === "*" && path.charAt(start + 1) === "*") {
    requirePattern(pattern, "**");
    return { kind: "wildcard", greedy: true, child };
  }

  const separator = path.indexOf(TYPE_ID_SEPARATOR, start);
  if (separator <= start || separator >= end) {
    throw new Error(`Invalid resource segment: ${path.slice(start, end)}`);
  }

  const type = path.slice(start, separator);
  const idStart = separator + 1;
  let resourceId: ResourceId;

  if (end - idStart === 1 && path.charAt(idStart) === "*") {
    requirePattern(pattern, "*");
    resourceId = { kind: "wildcard-id" };
  } else {
    try {
      resourceId = { kind: "definite", id: parseJavaUuid(path.slice(idStart, end)) };
    } catch {
      throw new Error(`Invalid UUID in segment: ${path.slice(start, end)}`);
    }
  }

  return { kind: "resource", type, resourceId, child };
}

function requirePattern(pattern: boolean, segment: string): void {
  if (pattern) return;
  throw new Error(`Wildcard not allowed in resource: ${segment}`);
}

export function resourcePathToString(path: CompiledResourcePath): string {
  const start = `${path.service}${SERVICE_SEPARATOR}`;
  if (path.root === null) return start;

  const parts: string[] = [];
  let node: Node | null = path.root;
  while (node !== null) {
    parts.push(nodeToString(node));
    node = node.child;
  }
  return start + parts.join(RESOURCE_SEPARATOR);
}

function nodeToString(node: Node): string {
  if (node.kind === "wildcard") return node.greedy ? "**" : "*";
  const id = node.resourceId.kind === "definite" ? node.resourceId.id : "*";
  return `${node.type}${TYPE_ID_SEPARATOR}${id}`;
}

/**
 * `java.util.UUID.fromString` port: five groups of up to 16 hex digits each;
 * the first three pack into the high 64 bits, the last two into the low 64.
 * Values are masked to 64 bits exactly as Java's long shifts do.
 */
export function parseJavaUuid(input: string): string {
  const parts = input.split("-");
  if (parts.length !== 5) {
    throw new Error(`Invalid UUID: ${input}`);
  }

  const values = parts.map((part) => {
    if (part.length === 0 || part.length > 16 || !/^[0-9a-fA-F]+$/.test(part)) {
      throw new Error(`Invalid UUID component: ${part}`);
    }
    return BigInt(`0x${part}`);
  });

  const mask64 = (1n << 64n) - 1n;
  const most = (((values[0]! << 32n) | (values[1]! << 16n) | values[2]!) & mask64);
  const least = (((values[3]! << 48n) | values[4]!) & mask64);
  const value = (most << 64n) | least;

  const hex = value.toString(16).padStart(32, "0");
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20, 32),
  ].join("-");
}

/** UUID → 16 big-endian bytes, matching ResourcePathSerde.toProto. */
export function uuidToBytes(uuid: string): Buffer {
  const hex = parseJavaUuid(uuid).replace(/-/g, "");
  return Buffer.from(hex, "hex");
}

export function uuidFromBytes(bytes: Buffer): string {
  if (bytes.length !== 16) throw new Error(`expected 16 UUID bytes, got ${bytes.length}`);
  const hex = bytes.toString("hex");
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20, 32),
  ].join("-");
}

export function pathsEqual(a: CompiledResourcePath, b: CompiledResourcePath): boolean {
  return resourcePathToString(a) === resourcePathToString(b);
}
