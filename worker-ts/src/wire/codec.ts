/**
 * Lenient accessors over the JSON wire format.
 *
 * The Java publishers use Gson, which omits null fields, so every optional
 * field can be absent, null, or the wrong type after a hand-edited message.
 * These helpers preserve Gson's "missing means null" behaviour without
 * throwing — the caller decides which fields are mandatory.
 */

export type Wire = Record<string, unknown>;

export class MalformedEventError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "MalformedEventError";
  }
}

export function isObject(value: unknown): value is Wire {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** A string field, or undefined when absent/null/of another type. */
export function asString(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

export function asNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

export function asBoolean(value: unknown): boolean | undefined {
  return typeof value === "boolean" ? value : undefined;
}

export function asObject(value: unknown): Wire | undefined {
  return isObject(value) ? value : undefined;
}

export function asArray(value: unknown): unknown[] | undefined {
  return Array.isArray(value) ? value : undefined;
}

/**
 * Reads an Instant serialized by Gson as epoch seconds. Gson writes a JSON
 * number; a numeric string is accepted too because some producers stringify
 * long values.
 */
export function asInstant(value: unknown): Date | undefined {
  const seconds =
    typeof value === "number"
      ? value
      : typeof value === "string" && /^-?\d+$/.test(value)
        ? Number(value)
        : undefined;
  if (seconds === undefined || !Number.isFinite(seconds)) return undefined;
  return new Date(seconds * 1000);
}

export function asUuid(value: unknown): string | undefined {
  const s = asString(value);
  return s === undefined || s.length === 0 ? undefined : s;
}

/** Reads a required string field; throws MalformedEventError when absent. */
export function requiredString(body: Wire, key: string): string {
  const value = asString(body[key]);
  if (value === undefined) {
    throw new MalformedEventError(`missing required field '${key}'`);
  }
  return value;
}

/** Reads a required UUID-shaped field without validating the spelling. */
export function requiredUuid(body: Wire, key: string): string {
  const value = asUuid(body[key]);
  if (value === undefined) {
    throw new MalformedEventError(`missing required field '${key}'`);
  }
  return value;
}

export function camelToSnake(key: string): string {
  return key.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`);
}

/**
 * Converts an in-process event object to the wire representation:
 * camelCase keys become snake_case and Date becomes epoch seconds, matching
 * `workerGson` (LOWER_CASE_WITH_UNDERSCORES + Instant serializer).
 */
export function toWire(value: unknown): unknown {
  if (value instanceof Date) {
    return Math.floor(value.getTime() / 1000);
  }
  if (Array.isArray(value)) {
    return value.map(toWire);
  }
  if (isObject(value)) {
    const out: Wire = {};
    for (const [key, entry] of Object.entries(value)) {
      // Gson's default omits nulls; a missing member is the wire contract for
      // every optional field (see the forms Gson note in project memory).
      if (entry === undefined || entry === null) continue;
      out[camelToSnake(key)] = toWire(entry);
    }
    return out;
  }
  return value;
}
