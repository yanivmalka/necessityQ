import { createHash } from "node:crypto";
import { USER_AGENT, sleep } from "./sources/http";

export interface GeoPoint {
  lat: number;
  lng: number;
  approx: boolean;
}

export interface GeoCache {
  get(key: string): Promise<GeoPoint | null | undefined>;
  put(key: string, value: GeoPoint | null): Promise<void>;
}

export function geoKey(address: string, city: string): string {
  return createHash("sha1").update(`${address}|${city}`.toLowerCase()).digest("hex");
}

/** Removes noise Nominatim does not understand ("פינת", store names in brackets, "מרכז מסחרי"). */
export function cleanAddress(address: string): string {
  return address
    .replace(/\(.*?\)/g, " ")
    .replace(/פינת.*$/, " ")
    .replace(/(מרכז מסחרי|קניון|ק\.|רח'|רחוב)\s*/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

let lastCall = 0;

async function nominatim(params: Record<string, string>): Promise<{ lat: number; lng: number } | null> {
  // usage policy: at most one request per second, identify the application
  const wait = lastCall + 1100 - Date.now();
  if (wait > 0) await sleep(wait);
  lastCall = Date.now();
  const q = new URLSearchParams({ format: "jsonv2", limit: "1", countrycodes: "il", "accept-language": "he", ...params });
  const res = await fetch(`https://nominatim.openstreetmap.org/search?${q}`, {
    headers: { "User-Agent": USER_AGENT },
    signal: AbortSignal.timeout(20_000),
  });
  if (!res.ok) throw new Error(`nominatim HTTP ${res.status}`);
  const body = (await res.json()) as { lat: string; lon: string }[];
  if (!body.length) return null;
  return { lat: Number(body[0].lat), lng: Number(body[0].lon) };
}

/** Address → point (cached). Falls back to the city centre, flagged `approx`. */
export async function geocode(address: string, city: string, cache: GeoCache): Promise<GeoPoint | null> {
  const key = geoKey(address, city);
  const hit = await cache.get(key);
  if (hit !== undefined) return hit;
  let point: GeoPoint | null = null;
  const street = cleanAddress(address);
  if (street) {
    const p = await nominatim({ street, city, country: "Israel" });
    if (p) point = { ...p, approx: false };
  }
  if (!point && city) {
    const p = await nominatim({ city, country: "Israel" });
    if (p) point = { ...p, approx: true };
  }
  await cache.put(key, point);
  return point;
}
