/**
 * Israel's national nutrition database "Tzameret" (Ministry of Health), published on data.gov.il
 * under the open government-data licence. It changes rarely, so it is downloaded once a month
 * (or on admin request) and stored as one compact gzipped snapshot that the app caches:
 *
 *   Storage nutrition/tzameret.json.gz
 *     { updatedAt, source, foods: [{ c, n, e, p, f, cb, fi, na, u?: [[unitName, grams]], r?: [[code, name, percent]] }] }
 *   Firestore nutrition/meta { updatedAt, foods, units, recipes }
 *
 * Values are per 100 g: e = kcal, p = protein g, f = fat g, cb = carbohydrates g, fi = fibre g, na = sodium mg.
 * u = household measures ("כוס", "יחידה בינונית") with their weight in grams.
 * r = composition of a composite food / dish (ingredient code, name, % of the dish by weight).
 */
import { httpText, sleep } from "./sources/http";

const API = "https://data.gov.il/api/3/action/datastore_search";

export const TZAMERET = {
  /** Foods with nutrients per 100 g. */
  foods: "c3cb0630-0650-46c1-a068-82d575c094b2",
  /** Composition of composite foods (recipes). */
  recipes: "5c726506-5c1e-43a7-94f7-24845dbde9c3",
  /** Weight in grams of a household measure of a food. */
  units: "755d28c0-75f7-40e1-9c8c-ecdd106f9b2d",
  /** Names of the household measures. */
  unitNames: "98fb46fe-e8de-4067-94d2-b0a8ea4269da",
} as const;

export type Row = Record<string, unknown>;

export interface NutritionFood {
  c: number;
  n: string;
  e: number | null;
  p: number | null;
  f: number | null;
  cb: number | null;
  fi: number | null;
  na: number | null;
  u?: [string, number][];
  r?: [number, string, number][];
}

export interface NutritionSnapshot {
  updatedAt: string;
  source: string;
  foods: NutritionFood[];
}

/** First present value among several possible column names (the published column names vary in case). */
function field(row: Row, ...names: string[]): unknown {
  for (const n of names) {
    if (row[n] !== undefined && row[n] !== null && row[n] !== "") return row[n];
    const key = Object.keys(row).find((k) => k.toLowerCase() === n.toLowerCase());
    if (key && row[key] !== null && row[key] !== "") return row[key];
  }
  return undefined;
}

export function num(v: unknown): number | null {
  if (v === undefined || v === null || v === "") return null;
  const n = typeof v === "number" ? v : Number(String(v).replace(",", ".").trim());
  return Number.isFinite(n) ? n : null;
}

const round = (v: number | null, digits = 1) => (v === null ? null : Math.round(v * 10 ** digits) / 10 ** digits);

const text = (v: unknown) => (v === undefined || v === null ? "" : String(v).replace(/\s+/g, " ").trim());

/** Food code: the "smlmitzrach" column, falling back to "Code". */
const foodCode = (row: Row) => num(field(row, "smlmitzrach", "Code", "code"));

/** Builds the snapshot from the four raw tables. Rows without a code or name are skipped. */
export function buildSnapshot(
  tables: { foods: Row[]; recipes: Row[]; units: Row[]; unitNames: Row[] },
  updatedAt = new Date().toISOString(),
): NutritionSnapshot {
  const unitName = new Map<number, string>();
  for (const r of tables.unitNames) {
    const code = num(field(r, "smlmida", "mida"));
    const name = text(field(r, "shmmida", "name"));
    if (code !== null && name) unitName.set(code, name);
  }

  const units = new Map<number, [string, number][]>();
  for (const r of tables.units) {
    const food = num(field(r, "mmitzrach", "smlmitzrach", "Code"));
    const mida = num(field(r, "mida", "smlmida"));
    const grams = num(field(r, "mishkal", "weight"));
    if (food === null || mida === null || grams === null || grams <= 0) continue;
    const name = unitName.get(mida) ?? `מידה ${mida}`;
    const list = units.get(food) ?? [];
    if (!list.some(([n]) => n === name)) list.push([name, round(grams)!]);
    units.set(food, list);
  }

  const recipes = new Map<number, [number, string, number][]>();
  for (const r of tables.recipes) {
    const dish = num(field(r, "smlmitzrach", "mmitzrach"));
    const part = num(field(r, "smlmarciv", "marciv"));
    const name = text(field(r, "shmmarciv"));
    const pct = num(field(r, "achuz_marciv", "achuz"));
    if (dish === null || part === null || pct === null || pct <= 0) continue;
    const list = recipes.get(dish) ?? [];
    list.push([part, name, round(pct, 2)!]);
    recipes.set(dish, list);
  }

  const seen = new Set<number>();
  const foods: NutritionFood[] = [];
  for (const r of tables.foods) {
    const c = foodCode(r);
    const n = text(field(r, "shmmitzrach"));
    if (c === null || !n || seen.has(c)) continue;
    seen.add(c);
    // the serving table may key by either column, so try both
    const alt = num(field(r, "Code"));
    const food: NutritionFood = {
      c,
      n,
      e: round(num(field(r, "food_energy"))),
      p: round(num(field(r, "protein"))),
      f: round(num(field(r, "total_fat"))),
      cb: round(num(field(r, "carbohydrates"))),
      fi: round(num(field(r, "total_dietary_fiber"))),
      na: round(num(field(r, "sodium")), 0),
    };
    const u = units.get(c) ?? (alt !== null ? units.get(alt) : undefined);
    if (u?.length) food.u = u;
    const rec = recipes.get(c) ?? (alt !== null ? recipes.get(alt) : undefined);
    if (rec?.length) food.r = rec.sort((a, b) => b[2] - a[2]);
    foods.push(food);
  }
  foods.sort((a, b) => a.c - b.c);
  return { updatedAt, source: "data.gov.il/dataset/tzameret", foods };
}

type Fetch = (url: string) => Promise<string>;

/** Reads a whole CKAN datastore table, page by page. */
export async function fetchTable(resourceId: string, get: Fetch = (u) => httpText(u, 120_000), pageSize = 5000): Promise<Row[]> {
  const rows: Row[] = [];
  for (let offset = 0; ; offset += pageSize) {
    const url = `${API}?resource_id=${resourceId}&limit=${pageSize}&offset=${offset}`;
    const body = JSON.parse(await get(url)) as { success?: boolean; result?: { records?: Row[]; total?: number } };
    if (!body.success || !body.result) throw new Error(`data.gov.il: unsuccessful response for ${resourceId}`);
    const page = body.result.records ?? [];
    rows.push(...page);
    const total = body.result.total ?? rows.length;
    if (page.length === 0 || rows.length >= total) break;
    if (offset > 200_000) throw new Error(`data.gov.il: ${resourceId} is unexpectedly large`);
    await sleep(200);
  }
  return rows;
}

/** Downloads all four tables. The unit names are optional: without them measures get a numbered name. */
export async function downloadTzameret(get?: Fetch, log: (m: string) => void = () => {}): Promise<NutritionSnapshot> {
  const foods = await fetchTable(TZAMERET.foods, get);
  log(`tzameret foods: ${foods.length}`);
  const recipes = await fetchTable(TZAMERET.recipes, get).catch((e) => (log(`tzameret recipes failed: ${e}`), [] as Row[]));
  const units = await fetchTable(TZAMERET.units, get).catch((e) => (log(`tzameret units failed: ${e}`), [] as Row[]));
  const unitNames = await fetchTable(TZAMERET.unitNames, get).catch((e) => (log(`tzameret unit names failed: ${e}`), [] as Row[]));
  if (foods.length < 1000) throw new Error(`tzameret: only ${foods.length} foods — refusing to replace the snapshot`);
  return buildSnapshot({ foods, recipes, units, unitNames });
}

export function snapshotStats(s: NutritionSnapshot) {
  return {
    foods: s.foods.length,
    units: s.foods.filter((f) => f.u?.length).length,
    recipes: s.foods.filter((f) => f.r?.length).length,
  };
}
