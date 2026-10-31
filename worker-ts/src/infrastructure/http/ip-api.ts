import type { SecurityEventLocation } from "../../wire/location";

interface IpApiEntry {
  status?: string;
  country?: string;
  city?: string;
  lat?: number;
  lon?: number;
  query?: string;
  message?: string;
}

export interface IpLookupOptions {
  baseUrl: string;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
}

/**
 * One POST to ip-api's batch endpoint (HTTP only; free tier). Returns one
 * entry per requested IP; a failed or unknown lookup maps to null, which the
 * caller caches for a day. Throws on transport errors / non-200 so the caller
 * can distinguish "no data" from "lookup unavailable".
 */
export async function fetchIpLocations(
  ips: string[],
  options: IpLookupOptions,
): Promise<Map<string, SecurityEventLocation | null>> {
  const fetchImpl = options.fetchImpl ?? fetch;
  const result = new Map<string, SecurityEventLocation | null>();
  if (ips.length === 0) return result;

  const response = await fetchImpl(`${options.baseUrl.replace(/\/+$/, "")}/batch`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(ips.map((query) => ({ query }))),
    signal: AbortSignal.timeout(options.timeoutMs ?? 10_000),
  });

  if (response.status !== 200) {
    throw new Error(`ip-api returned HTTP ${response.status}`);
  }

  const entries = (await response.json()) as IpApiEntry[];
  for (const entry of Array.isArray(entries) ? entries : []) {
    if (typeof entry.query !== "string") continue;
    if (entry.status === "success") {
      result.set(entry.query, {
        country: entry.country ?? null,
        city: entry.city ?? null,
        latitude: typeof entry.lat === "number" ? entry.lat : null,
        longitude: typeof entry.lon === "number" ? entry.lon : null,
      });
    } else {
      result.set(entry.query, null);
    }
  }

  // Any IP ip-api silently omitted stays unresolved.
  for (const ip of ips) {
    if (!result.has(ip)) result.set(ip, null);
  }
  return result;
}
