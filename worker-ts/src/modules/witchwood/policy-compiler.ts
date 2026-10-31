import { isObject } from "../../wire/codec";
import {
  compilePattern,
  compileResource,
  resourcePathToString,
  type CompiledResourcePath,
} from "./resource-path";

export interface PolicyStatement {
  id: number;
  effect: string;
  actions: string[];
  resources: CompiledResourcePath[];
  /** Compact JSON; empty string means "no condition". */
  condition: string;
}

/**
 * Compiles a policy document (the event's `document` JSON array) into
 * Witchwood policy statements. Pure function — port of PolicyCompiler.kt.
 *
 * Statement ids are positional. Effects default to ALLOW and are uppercased.
 * Actions and resources keep first-seen order and are de-duplicated.
 */
export class PolicyCompiler {
  compile(document: unknown): PolicyStatement[] {
    if (!Array.isArray(document)) {
      throw new Error("policy document is not an array");
    }

    return document.map((element, index) => {
      if (!isObject(element)) {
        throw new Error(`policy statement ${index} is not an object`);
      }

      const effect =
        element.effect === undefined ? "ALLOW" : requireString(element.effect, "effect").toUpperCase();

      const actions = unique(requireStringArray(element.actions, "actions"));

      const resources = uniqueByString(
        requireStringArray(element.resources, "resources").map((resource) =>
          resource.includes("*") ? compilePattern(resource) : compileResource(resource),
        ),
        resourcePathToString,
      );

      const condition = Object.prototype.hasOwnProperty.call(element, "condition")
        ? (JSON.stringify(element.condition) ?? "")
        : "";

      return { id: index, effect, actions, resources, condition };
    });
  }
}

function requireString(value: unknown, field: string): string {
  if (typeof value !== "string") throw new Error(`policy ${field} is not a string`);
  return value;
}

function requireStringArray(value: unknown, field: string): string[] {
  if (value === undefined) return [];
  if (!Array.isArray(value)) throw new Error(`policy ${field} is not an array`);
  return value.map((entry) => requireString(entry, field));
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}

function uniqueByString<T>(values: T[], key: (value: T) => string): T[] {
  const seen = new Set<string>();
  const out: T[] = [];
  for (const value of values) {
    const k = key(value);
    if (!seen.has(k)) {
      seen.add(k);
      out.push(value);
    }
  }
  return out;
}
