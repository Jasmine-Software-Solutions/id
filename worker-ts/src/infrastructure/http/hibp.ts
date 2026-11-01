import type { Logger } from "../../runtime/logging";

export interface HibpOptions {
  baseUrl: string;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
  logger?: Logger;
}

/**
 * K-anonymity lookup against the Pwned Passwords range API.
 *
 * Returns true (found), false (not found), or undefined when HIBP could not be
 * consulted — the Java detector returned without action in that case, and the
 * SQS message must still be treated as handled.
 */
export async function isPasswordPwned(
  passwordHash: string,
  options: HibpOptions,
): Promise<boolean | undefined> {
  const prefix = passwordHash.slice(0, 5).toUpperCase();
  const suffix = passwordHash.slice(5).toUpperCase();
  const fetchImpl = options.fetchImpl ?? fetch;

  try {
    const response = await fetchImpl(
      `${options.baseUrl.replace(/\/+$/, "")}/range/${prefix}`,
      {
        method: "GET",
        headers: { "Add-Padding": "true" },
        signal: AbortSignal.timeout(options.timeoutMs ?? 5_000),
      },
    );

    if (response.status !== 200) {
      options.logger?.warn("HIBP API returned non-200", { status: response.status, prefix });
      return undefined;
    }

    const body = await response.text();
    return body.split(/\r?\n/).some((line) => {
      const separator = line.indexOf(":");
      if (separator < 0) return false;
      return line.slice(0, separator).trim() === suffix;
    });
  } catch (error) {
    options.logger?.error("HIBP lookup failed", { prefix, error });
    return undefined;
  }
}
