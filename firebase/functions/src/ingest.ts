import { CHAINS, Chain, DEFAULT_CHAINS, chainByKey } from "./chains";
import { latest, normalizeStoreId } from "./filenames";
import { GeoCache, geocode } from "./geocode";
import { israelDay, mergeSnapshot } from "./history";
import { Branch, FileEntry, Snapshot } from "./model";
import { cityInScope, parsePrices, parseStores } from "./normalize";
import { PromoSnapshot, israelLocal, offerable, parsePromotions } from "./promos";
import { Source, sourceFor } from "./sources";
import { decodePayload } from "./xml";

/** Admin-editable settings (Firestore `config/prices`). Every field is optional. */
export interface PriceConfig {
  /** how often a tracked branch's prices are refreshed (hours); 24 = once a day */
  refreshHours: number;
  /** how often the branch lists (Stores files) are re-read (days) */
  storesRefreshDays: number;
  /** window of the price range shown to users (days) */
  rangeDays: number;
  /** cities in the data scope (Hebrew names as published) */
  cities: string[];
  /** chain keys to ingest (see chains.ts) */
  chains: string[];
  /** a user-requested refresh is skipped when the data is younger than this (minutes) */
  manualRefreshMinutes: number;
}

export const DEFAULT_CONFIG: PriceConfig = {
  refreshHours: 24,
  storesRefreshDays: 7,
  rangeDays: 90,
  cities: ["ירושלים"],
  chains: DEFAULT_CHAINS,
  manualRefreshMinutes: 60,
};

export function withDefaults(raw: Partial<PriceConfig> | undefined): PriceConfig {
  const c = { ...DEFAULT_CONFIG, ...(raw ?? {}) };
  const pos = (v: unknown, d: number) => (typeof v === "number" && v > 0 ? v : d);
  return {
    refreshHours: pos(c.refreshHours, DEFAULT_CONFIG.refreshHours),
    storesRefreshDays: pos(c.storesRefreshDays, DEFAULT_CONFIG.storesRefreshDays),
    rangeDays: Math.min(pos(c.rangeDays, DEFAULT_CONFIG.rangeDays), 365),
    cities: Array.isArray(c.cities) && c.cities.length ? c.cities.map(String) : DEFAULT_CONFIG.cities,
    chains: Array.isArray(c.chains) && c.chains.length ? c.chains.map(String).filter((k) => chainByKey(k)) : DEFAULT_CONFIG.chains,
    manualRefreshMinutes: pos(c.manualRefreshMinutes, DEFAULT_CONFIG.manualRefreshMinutes),
  };
}

export interface BranchStatus {
  updatedAt: string | null; // ISO of the last successful price load
  items: number;
  sourceFile: string;
  sourceStamp: string;
  lastAttemptAt: string | null;
  error: string | null;
}

/** Persistence used by the pipeline (Firestore + Storage in production, in-memory in tests). */
export interface PriceStore {
  readIndex(): Promise<{ updatedAt: string | null; branches: Branch[] }>;
  writeIndex(branches: Branch[], meta: { updatedAt: string; cities: string[]; chains: Record<string, string> }): Promise<void>;
  readSnapshot(branchId: string): Promise<Snapshot | null>;
  writeSnapshot(snapshot: Snapshot): Promise<void>;
  readStatus(branchId: string): Promise<BranchStatus | null>;
  writeStatus(branchId: string, status: BranchStatus): Promise<void>;
  /** union of branch ids selected by any household */
  trackedBranchIds(): Promise<string[]>;
  geoCache: GeoCache;
}

export interface Deps {
  store: PriceStore;
  sourceFor?: (chain: Chain) => Source;
  now?: () => Date;
  log?: (msg: string) => void;
}

/** Re-reads the Stores files of the configured chains and rewrites the branch index for the in-scope cities. */
export async function refreshBranches(config: PriceConfig, deps: Deps): Promise<{ branches: number; chains: Record<string, string> }> {
  const log = deps.log ?? (() => {});
  const src = deps.sourceFor ?? sourceFor;
  const previous = await deps.store.readIndex();
  const prevById = new Map(previous.branches.map((b) => [b.id, b]));
  const result: Branch[] = [];
  const chainStatus: Record<string, string> = {};

  for (const key of config.chains) {
    const chain = chainByKey(key);
    if (!chain) continue;
    try {
      const files = await src(chain).list(chain, "stores");
      const file = latest(files, "stores");
      if (!file) throw new Error("no Stores file listed");
      const raw = parseStores(decodePayload(await src(chain).fetch(chain, file)));
      const inScope = raw.filter((b) => cityInScope(b.city, config.cities));
      for (const b of inScope) {
        const id = `${chain.key}_${b.storeId}`;
        const old = prevById.get(id);
        let point = old && old.address === b.address && old.city === b.city && old.lat !== null ? { lat: old.lat!, lng: old.lng!, approx: old.approx } : null;
        if (!point) {
          try {
            point = await geocode(b.address, b.city, deps.store.geoCache);
          } catch (e) {
            log(`geocode failed for ${id}: ${(e as Error).message}`);
          }
        }
        result.push({ ...b, id, chainKey: chain.key, chainName: chain.name, lat: point?.lat ?? null, lng: point?.lng ?? null, approx: point?.approx ?? true });
      }
      chainStatus[key] = `ok:${inScope.length}/${raw.length}`;
      log(`${chain.key}: ${inScope.length} of ${raw.length} branches in scope (${file.name})`);
    } catch (e) {
      chainStatus[key] = `error:${(e as Error).message}`.slice(0, 200);
      log(`${chain.key}: stores failed: ${(e as Error).message}`);
      // keep what we knew about this chain rather than dropping its branches
      result.push(...previous.branches.filter((b) => b.chainKey === key));
    }
  }
  result.sort((a, b) => a.chainName.localeCompare(b.chainName, "he") || a.name.localeCompare(b.name, "he"));
  const now = (deps.now ?? (() => new Date()))().toISOString();
  await deps.store.writeIndex(result, { updatedAt: now, cities: config.cities, chains: chainStatus });
  return { branches: result.length, chains: chainStatus };
}

/** Loads the newest full price file of one branch and merges it into that branch's snapshot. */
export async function refreshBranchPrices(branch: Branch, config: PriceConfig, deps: Deps): Promise<BranchStatus> {
  const src = deps.sourceFor ?? sourceFor;
  const now = (deps.now ?? (() => new Date()))();
  const chain = chainByKey(branch.chainKey);
  const previousStatus = await deps.store.readStatus(branch.id);
  const base: BranchStatus = previousStatus ?? { updatedAt: null, items: 0, sourceFile: "", sourceStamp: "", lastAttemptAt: null, error: null };
  try {
    if (!chain) throw new Error(`unknown chain ${branch.chainKey}`);
    let files = await src(chain).list(chain, "pricefull", branch.storeId);
    // Some branches (new ones, or chains that skip full files for a store) publish only Price files:
    // use the newest one rather than failing, and keep items it does not mention.
    const full = latest(files, "pricefull", branch.storeId);
    if (!full && !files.some((f) => f.kind === "price")) files = files.concat(await src(chain).list(chain, "price", branch.storeId));
    const file = full ?? latest(files, "price", branch.storeId);
    if (!file) throw new Error(missingFileMessage(files, branch.storeId));
    const { items } = parsePrices(decodePayload(await src(chain).fetch(chain, file)));
    if (items.length === 0) throw new Error(`no items in ${file.name}`);
    const previous = await deps.store.readSnapshot(branch.id);
    const snapshot = mergeSnapshot(
      branch.id,
      previous,
      items,
      israelDay(now),
      config.rangeDays,
      { file: file.name, stamp: file.stamp, now: now.toISOString() },
      !full,
    );
    await deps.store.writeSnapshot(snapshot);
    const status: BranchStatus = {
      updatedAt: now.toISOString(),
      items: snapshot.items.length,
      sourceFile: file.name,
      sourceStamp: file.stamp,
      lastAttemptAt: now.toISOString(),
      error: null,
    };
    await deps.store.writeStatus(branch.id, status);
    return status;
  } catch (e) {
    const status = { ...base, lastAttemptAt: now.toISOString(), error: (e as Error).message.slice(0, 300) };
    await deps.store.writeStatus(branch.id, status);
    return status;
  }
}

/** Explains a missing price file: how much was listed and which store ids do have price files. */
export function missingFileMessage(files: FileEntry[], storeId: string): string {
  const own = files.filter((f) => f.storeId === normalizeStoreId(storeId)).map((f) => f.kind ?? "other");
  const stores = [...new Set(files.filter((f) => f.kind === "pricefull" || f.kind === "price").map((f) => f.storeId ?? "?"))];
  stores.sort((a, b) => Number(a) - Number(b) || a.localeCompare(b));
  const sample = stores.length > 25 ? `${stores.slice(0, 25).join(",")},…` : stores.join(",");
  const ownPart = own.length ? `only ${[...new Set(own)].join("/")} files for it` : "no files for it";
  return `no PriceFull/Price file for store ${storeId}: ${ownPart}; ${files.length} files listed, price files for ${stores.length} stores (${sample || "none"})`;
}

/** True when a branch's prices should be (re)loaded now. Failed attempts retry after an hour. */
export function isDue(status: BranchStatus | null, config: PriceConfig, now: Date): boolean {
  if (!status) return true;
  const age = (iso: string | null) => (iso ? now.getTime() - Date.parse(iso) : Infinity);
  if (status.error && age(status.lastAttemptAt) < 60 * 60_000) return false;
  return age(status.updatedAt) >= config.refreshHours * 3600_000 - 5 * 60_000;
}

/** The hourly tick: refresh branch lists when stale, then every tracked branch that is due, within a time budget. */
export async function tick(config: PriceConfig, deps: Deps, budgetMs: number): Promise<string[]> {
  const started = Date.now();
  const now = (deps.now ?? (() => new Date()))();
  const report: string[] = [];
  let index = await deps.store.readIndex();
  const indexAge = index.updatedAt ? now.getTime() - Date.parse(index.updatedAt) : Infinity;
  if (indexAge >= config.storesRefreshDays * 86_400_000 || index.branches.length === 0) {
    const r = await refreshBranches(config, deps);
    report.push(`branches: ${r.branches}`);
    index = await deps.store.readIndex();
  }
  const byId = new Map(index.branches.map((b) => [b.id, b]));
  const tracked = await deps.store.trackedBranchIds();
  for (const id of tracked) {
    if (Date.now() - started > budgetMs) {
      report.push("time budget reached; the rest continues next tick");
      break;
    }
    const branch = byId.get(id);
    if (!branch) continue;
    const status = await deps.store.readStatus(id);
    if (!isDue(status, config, now)) continue;
    const s = await refreshBranchPrices(branch, config, deps);
    report.push(`${id}: ${s.error ? `error ${s.error}` : `${s.items} items`}`);
  }
  return report;
}

export { CHAINS };

// ---------------------------------------------------------------- promotions and stock (every 30 minutes)

/** Freshness of a branch's promotions (Firestore `promoBranches/{branchId}`). */
export interface PromoStatus {
  updatedAt: string | null; // ISO of the last successful promotions load
  /** every promotion in the file (all are kept) */
  promos: number;
  /** of them, those the app may offer right now: running, not stock-dependent, on products the branch sells */
  active?: number;
  sourceFile: string;
  sourceStamp: string;
  lastAttemptAt: string | null;
  error: string | null;
}

/** Where the promotions go (Firestore + Storage in production, in-memory in tests). */
export interface PromoStore {
  readPromoStatus(branchId: string): Promise<PromoStatus | null>;
  writePromoStatus(branchId: string, status: PromoStatus): Promise<void>;
  writePromos(snapshot: PromoSnapshot): Promise<void>;
}

/** How often promotions and stock are checked (minutes). The files are listed each time; downloaded only when newer. */
export const OFFERS_MINUTES = 30;

/**
 * Loads the newest PromoFull (or Promo) file of a branch and writes every promotion in it for the app, as published,
 * with its dates and flags (club, coupon, stock-dependent, whole basket). Nothing is dropped here: the app decides what
 * not to show (ended, stock-dependent, or on a product missing from the branch's price file, i.e. out of stock).
 * Skipped when the newest file is the one already loaded (unless [force]).
 */
export async function refreshBranchPromos(branch: Branch, deps: Deps, promoStore: PromoStore, force = false): Promise<PromoStatus> {
  const src = deps.sourceFor ?? sourceFor;
  const now = (deps.now ?? (() => new Date()))();
  const chain = chainByKey(branch.chainKey);
  const previous = await promoStore.readPromoStatus(branch.id);
  const base: PromoStatus = previous ?? { updatedAt: null, promos: 0, sourceFile: "", sourceStamp: "", lastAttemptAt: null, error: null };
  try {
    if (!chain) throw new Error(`unknown chain ${branch.chainKey}`);
    let files = await src(chain).list(chain, "promofull", branch.storeId);
    const full = latest(files, "promofull", branch.storeId);
    if (!full && !files.some((f) => f.kind === "promo")) files = files.concat(await src(chain).list(chain, "promo", branch.storeId));
    const file = full ?? latest(files, "promo", branch.storeId);
    if (!file) throw new Error(`no PromoFull/Promo file for store ${branch.storeId}`);
    if (!force && previous && !previous.error && previous.sourceFile === file.name) {
      // nothing newer published: the promotions on record stand; only the check time moves
      const status = { ...previous, lastAttemptAt: now.toISOString() };
      await promoStore.writePromoStatus(branch.id, status);
      return status;
    }
    const { promos } = parsePromotions(decodePayload(await src(chain).fetch(chain, file)));
    const snapshot = await deps.store.readSnapshot(branch.id);
    const sold = new Set((snapshot?.items ?? []).map((i) => i.c));
    await promoStore.writePromos({ v: 1, branchId: branch.id, updatedAt: now.toISOString(), sourceFile: file.name, sourceStamp: file.stamp, promos });
    const status: PromoStatus = {
      updatedAt: now.toISOString(),
      promos: promos.length,
      active: offerable(promos, sold, israelLocal(now)).length,
      sourceFile: file.name,
      sourceStamp: file.stamp,
      lastAttemptAt: now.toISOString(),
      error: null,
    };
    await promoStore.writePromoStatus(branch.id, status);
    return status;
  } catch (e) {
    const status = { ...base, lastAttemptAt: now.toISOString(), error: (e as Error).message.slice(0, 300) };
    await promoStore.writePromoStatus(branch.id, status);
    return status;
  }
}

/**
 * Stock and price changes between the daily full loads: when the branch has published a price file (PriceFull, or a
 * Price delta) newer than the one in its snapshot, it is merged in now. A delta only updates the products it lists.
 */
export async function refreshBranchStock(branch: Branch, config: PriceConfig, deps: Deps): Promise<BranchStatus | null> {
  const src = deps.sourceFor ?? sourceFor;
  const now = (deps.now ?? (() => new Date()))();
  const chain = chainByKey(branch.chainKey);
  if (!chain) return null;
  const status = await deps.store.readStatus(branch.id);
  if (!status?.updatedAt) return refreshBranchPrices(branch, config, deps);
  let files = await src(chain).list(chain, "pricefull", branch.storeId);
  if (!files.some((f) => f.kind === "price")) files = files.concat(await src(chain).list(chain, "price", branch.storeId));
  const full = latest(files, "pricefull", branch.storeId);
  const delta = latest(files, "price", branch.storeId);
  const newest = [full, delta].filter((f): f is FileEntry => f !== null).sort((a, b) => (a.stamp < b.stamp ? 1 : -1))[0];
  if (!newest || newest.stamp <= status.sourceStamp || newest.name === status.sourceFile) return null;
  if (newest === full) return refreshBranchPrices(branch, config, deps);
  try {
    const { items } = parsePrices(decodePayload(await src(chain).fetch(chain, newest)));
    if (items.length === 0) return null;
    const previous = await deps.store.readSnapshot(branch.id);
    const snapshot = mergeSnapshot(branch.id, previous, items, israelDay(now), config.rangeDays, { file: newest.name, stamp: newest.stamp, now: now.toISOString() }, true);
    await deps.store.writeSnapshot(snapshot);
    const next: BranchStatus = { ...status, updatedAt: now.toISOString(), items: snapshot.items.length, sourceFile: newest.name, sourceStamp: newest.stamp, lastAttemptAt: now.toISOString(), error: null };
    await deps.store.writeStatus(branch.id, next);
    return next;
  } catch (e) {
    const next = { ...status, lastAttemptAt: now.toISOString(), error: (e as Error).message.slice(0, 300) };
    await deps.store.writeStatus(branch.id, next);
    return next;
  }
}

/** True when a branch's promotions should be checked now (every [OFFERS_MINUTES]; failures retry at the same pace). */
export function offersDue(status: PromoStatus | null, now: Date, minutes = OFFERS_MINUTES): boolean {
  if (!status?.lastAttemptAt) return true;
  return now.getTime() - Date.parse(status.lastAttemptAt) >= minutes * 60_000 - 2 * 60_000;
}

/** The 30-minute tick: for every tracked branch, newer prices (stock) first, then its promotions. */
export async function offersTick(config: PriceConfig, deps: Deps, promoStore: PromoStore, budgetMs: number, only?: string[]): Promise<string[]> {
  const started = Date.now();
  const now = (deps.now ?? (() => new Date()))();
  const report: string[] = [];
  const index = await deps.store.readIndex();
  const byId = new Map(index.branches.map((b) => [b.id, b]));
  const ids = only ?? (await deps.store.trackedBranchIds());
  for (const id of ids) {
    if (Date.now() - started > budgetMs) {
      report.push("time budget reached; the rest continues next tick");
      break;
    }
    const branch = byId.get(id);
    if (!branch) continue;
    if (!offersDue(await promoStore.readPromoStatus(id), now)) continue;
    try {
      const stock = await refreshBranchStock(branch, config, deps);
      if (stock) report.push(`${id}: prices ${stock.error ? `error ${stock.error}` : `${stock.items} items`}`);
    } catch (e) {
      report.push(`${id}: stock check failed ${(e as Error).message}`);
    }
    const p = await refreshBranchPromos(branch, deps, promoStore);
    report.push(`${id}: promos ${p.error ? `error ${p.error}` : p.promos}`);
  }
  return report;
}
