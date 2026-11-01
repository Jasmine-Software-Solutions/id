import { isObject } from "./codec";

/** Mirrors `SecurityEventLocation`. */
export interface SecurityEventLocation {
  country: string | null;
  city: string | null;
  latitude: number | null;
  longitude: number | null;
}

export function toLocation(value: unknown): SecurityEventLocation | null {
  if (!isObject(value)) return null;
  return {
    country: typeof value.country === "string" ? value.country : null,
    city: typeof value.city === "string" ? value.city : null,
    latitude: typeof value.latitude === "number" ? value.latitude : null,
    longitude: typeof value.longitude === "number" ? value.longitude : null,
  };
}

export function locationFromJson(json: string | null | undefined): SecurityEventLocation | null {
  if (json === null || json === undefined || json === "null") return null;
  try {
    return toLocation(JSON.parse(json));
  } catch {
    return null;
  }
}

export function locationToJson(location: SecurityEventLocation | null): string {
  return location === null ? "null" : JSON.stringify(location);
}
