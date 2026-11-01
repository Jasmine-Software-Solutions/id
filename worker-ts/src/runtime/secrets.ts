import { GetParametersCommand, type SSMClient } from "@aws-sdk/client-ssm";

/**
 * Cold-start secret cache.
 *
 * One SSM `GetParameters` call (decryption on) fetches every secret a function
 * needs; values stay in the execution environment for its lifetime. An
 * environment variable of the same fallback name wins — that is how local RIE
 * runs and the dev stack work without SSM.
 */
export class Secrets {
  private readonly values: Map<string, string>;

  private constructor(values: Map<string, string>) {
    this.values = values;
  }

  static async load(options: {
    names: string[];
    prefix: string;
    client?: SSMClient;
    envFallbacks?: Record<string, string | undefined>;
    logger?: { info(message: string, fields?: Record<string, unknown>): void };
  }): Promise<Secrets> {
    const values = new Map<string, string>();
    const missing: string[] = [];
    const fallbacks = options.envFallbacks ?? {};

    for (const name of options.names) {
      const fallback = fallbacks[name];
      if (fallback !== undefined && fallback !== "") {
        values.set(name, fallback);
      } else {
        missing.push(name);
      }
    }

    if (missing.length > 0) {
      if (!options.client) {
        throw new Error(`no SSM client for secrets: ${missing.join(", ")}`);
      }
      const paths = missing.map((name) => parameterPath(options.prefix, name));
      const response = await options.client.send(
        new GetParametersCommand({ Names: paths, WithDecryption: true }),
      );
      const byPath = new Map((response.Parameters ?? []).map((p) => [p.Name ?? "", p.Value ?? ""]));
      for (const [index, name] of missing.entries()) {
        const value = byPath.get(paths[index]!);
        if (value === undefined) {
          throw new Error(`SSM parameter not found: ${paths[index]}`);
        }
        values.set(name, value);
      }
      options.logger?.info("Loaded secrets from SSM", { count: missing.length });
    }

    return new Secrets(values);
  }

  get(name: string): string {
    const value = this.values.get(name);
    if (value === undefined) {
      throw new Error(`secret '${name}' was not loaded`);
    }
    return value;
  }
}

export function parameterPath(prefix: string, name: string): string {
  const normalizedPrefix = prefix.replace(/\/+$/, "");
  return normalizedPrefix ? `${normalizedPrefix}/${name}` : `/${name}`;
}
