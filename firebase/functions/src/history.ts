import { RawItem, Snapshot, SnapshotItem } from "./model";

/** History older than this is dropped (the range window is configurable up to this). */
export const HISTORY_KEEP_DAYS = 400;

/**
 * Merges today's observation into the previous snapshot of a branch:
 * - appends [day, price] to an item's history only when the price changed,
 * - keeps `s` = the day the current price started,
 * - lo/hi = min/max of every price that was in force at some point inside the last [rangeDays].
 * Items missing from today's file are dropped (the branch no longer sells them),
 * unless the file is `partial` (a Price delta used when no PriceFull exists): then they are kept as they were.
 */
export function mergeSnapshot(
  branchId: string,
  previous: Snapshot | null,
  items: RawItem[],
  today: string,
  rangeDays: number,
  meta: { file: string; stamp: string; now: string },
  partial = false,
): Snapshot {
  const prev = new Map<string, SnapshotItem>();
  for (const it of previous?.items ?? []) prev.set(it.c, it);
  const seen = new Set<string>();
  const out: SnapshotItem[] = [];
  for (const raw of items) {
    if (seen.has(raw.code)) continue; // duplicate lines: first wins
    seen.add(raw.code);
    const old = prev.get(raw.code);
    let h: [string, number][] = old ? old.h.slice() : [];
    const last = h[h.length - 1];
    if (!last || last[1] !== raw.price) {
      if (last && last[0] === today) h[h.length - 1] = [today, raw.price];
      else h.push([today, raw.price]);
    }
    h = trim(h, today, HISTORY_KEEP_DAYS);
    const { lo, hi } = range(h, today, rangeDays);
    out.push({
      c: raw.code,
      n: raw.name,
      b: raw.brand,
      q: raw.qty,
      u: raw.unit,
      w: raw.weighted,
      i: raw.internal,
      p: raw.price,
      up: raw.unitPrice,
      s: h[h.length - 1][0],
      lo,
      hi,
      h,
    });
  }
  if (partial) {
    for (const old of previous?.items ?? []) {
      if (seen.has(old.c)) continue;
      const h = trim(old.h, today, HISTORY_KEEP_DAYS);
      out.push({ ...old, h, ...range(h, today, rangeDays) });
    }
  }
  return {
    v: 1,
    branchId,
    updatedAt: meta.now,
    sourceFile: meta.file,
    sourceStamp: meta.stamp,
    rangeDays,
    items: out,
  };
}

/** min/max over prices in force during [today - days, today] (the price in force at the window start counts). */
export function range(h: [string, number][], today: string, days: number): { lo: number; hi: number } {
  const from = addDays(today, -days);
  let lo = Infinity;
  let hi = -Infinity;
  for (let i = 0; i < h.length; i++) {
    const [, p] = h[i];
    const nextStart = i + 1 < h.length ? h[i + 1][0] : "9999-12-31";
    if (nextStart <= from) continue; // replaced before the window
    lo = Math.min(lo, p);
    hi = Math.max(hi, p);
  }
  return { lo, hi };
}

function trim(h: [string, number][], today: string, days: number): [string, number][] {
  const from = addDays(today, -days);
  let i = 0;
  while (i + 1 < h.length && h[i + 1][0] <= from) i++;
  return h.slice(i);
}

export function addDays(day: string, delta: number): string {
  const d = new Date(`${day}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + delta);
  return d.toISOString().slice(0, 10);
}

/** yyyy-mm-dd in Israel time. */
export function israelDay(date: Date): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Jerusalem" }).format(date);
}
