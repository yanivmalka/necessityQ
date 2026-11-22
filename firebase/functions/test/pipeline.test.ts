import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { Chain } from "../src/chains";
import { entry, latest, parseFileName } from "../src/filenames";
import { cleanAddress } from "../src/geocode";
import { addDays, mergeSnapshot, range } from "../src/history";
import { BranchStatus, DEFAULT_CONFIG, PriceStore, isDue, refreshBranchPrices, tick, withDefaults } from "../src/ingest";
import { Branch, FileEntry, FileKind, RawItem, Snapshot } from "../src/model";
import { cityInScope, parsePrices, parseStores } from "../src/normalize";
import { Source, parseLaibFiles, parsePublishPricePage, parseShufersalPage } from "../src/sources";
import { decodePayload } from "../src/xml";

const fixture = (name: string) => new Uint8Array(readFileSync(join(__dirname, "fixtures", name)));
const raw = (code: string, price: number): RawItem => ({ code, name: code, brand: "", qty: null, unit: "", measure: "", weighted: false, price, unitPrice: null, internal: false, updated: "" });
const meta = { file: "f", stamp: "s", now: "2026-11-16T03:00:00Z" };

describe("file names", () => {
  it("reads kind, store and stamp from the common patterns", () => {
    expect(parseFileName("PriceFull7290027600007-001-202611160300.gz")).toEqual({ kind: "pricefull", storeId: "1", stamp: "202611160300" });
    expect(parseFileName("Price7290058140886-039-202611161400.gz")).toEqual({ kind: "price", storeId: "39", stamp: "202611161400" });
    expect(parseFileName("Stores7290058140886-202611160500.xml")).toEqual({ kind: "stores", storeId: null, stamp: "202611160500" });
    expect(parseFileName("PromoFull7290696200003-001-012-20261116-030000.xml.gz")).toEqual({ kind: "promofull", storeId: "12", stamp: "202611160300" });
    expect(parseFileName("https://x.blob.core.windows.net/a/PriceFull7290027600007-207-202611160300.gz?sv=1&sig=2").storeId).toBe("207");
    expect(parseFileName("NULLPrice7290027600007-001-202611160300.gz").kind).toBeNull();
  });

  it("picks the newest full file of a branch", () => {
    const files = [
      entry("PriceFull7290058140886-039-202611150300.gz", "a"),
      entry("PriceFull7290058140886-039-202611160300.gz", "b"),
      entry("PriceFull7290058140886-041-202611170300.gz", "c"),
      entry("Price7290058140886-039-202611161500.gz", "d"),
    ];
    expect(latest(files, "pricefull", "039")?.url).toBe("b");
    expect(latest(files, "stores")).toBeNull();
  });
});

describe("decoding and parsing", () => {
  it("parses Shufersal's upper-case Stores dialect and the Jerusalem scope", () => {
    const branches = parseStores(decodePayload(fixture("shufersal-stores.xml")));
    expect(branches.map((b) => b.storeId)).toEqual(["1", "207", "213"]);
    expect(branches[1]).toMatchObject({ name: "שלי גבעת שאול", address: "כנפי נשרים 5", city: "ירושלים", subChain: "שופרסל שלי" });
    const jerusalem = branches.filter((b) => cityInScope(b.city, ["ירושלים"]));
    expect(jerusalem.map((b) => b.storeId)).toEqual(["207", "213"]);
  });

  it("parses UTF-16 Cerberus Stores with StoreID casing and city codes", () => {
    const branches = parseStores(decodePayload(fixture("cerberus-stores.utf16.xml")));
    expect(branches.map((b) => b.storeId)).toEqual(["39", "41", "52"]);
    expect(branches.filter((b) => cityInScope(b.city, ["ירושלים"])).map((b) => b.name)).toEqual(["תלפיות", "הר חוצבים"]);
  });

  it("parses a gzipped PriceFull, tolerating an unescaped & and skipping zero prices", () => {
    const { storeId, items } = parsePrices(decodePayload(fixture("pricefull.xml.gz")));
    expect(storeId).toBe("39");
    expect(items).toHaveLength(5);
    expect(items[0]).toMatchObject({ code: "7290000042015", name: "חלב תנובה 3% 1 ליטר", brand: "תנובה", price: 6.2, unitPrice: 6.2, qty: 1, unit: "ליטר", measure: "ליטר", weighted: false, internal: false });
    expect(items[1].name).toContain("קוטג' 5%");
    expect(items[2]).toMatchObject({ code: "1234", weighted: true, internal: true, price: 7.9, unit: "קילו", measure: "" });
  });

  it("keeps UnitOfMeasure apart from UnitQty, so a weighted price per 100 g keeps its 100", () => {
    const { items } = parsePrices(decodePayload(fixture("pricefull.xml")));
    const olives = items.find((i) => i.code === "2000123");
    // UnitQty "גרם" still fills `unit`; the price is per UnitOfMeasure, spacing collapsed
    expect(olives).toMatchObject({ weighted: true, price: 4.9, unit: "גרם", measure: "100 גרם" });
    const snap = mergeSnapshot("b", null, items, "2026-11-16", 90, { file: "f", stamp: "s", now: "2026-11-16T03:00:00Z" });
    expect(snap.items.find((i) => i.c === "2000123")).toMatchObject({ u: "גרם", m: "100 גרם", w: true, p: 4.9 });
  });

  it("reads snapshots written before the unit of measure was kept", () => {
    const old = mergeSnapshot("b", null, [raw("a", 10)], "2026-11-01", 90, meta);
    delete old.items[0].m; // as in an older file
    const snap = mergeSnapshot("b", old, [raw("b", 2)], "2026-11-02", 90, meta, true);
    expect(snap.items.find((i) => i.c === "a")).toMatchObject({ p: 10, u: "" });
    expect(snap.items.find((i) => i.c === "a")?.m).toBeUndefined();
    expect(snap.items.find((i) => i.c === "b")?.m).toBe("");
  });

  it("matches city spellings", () => {
    expect(cityInScope("ירושליים", ["ירושלים"])).toBe(true);
    expect(cityInScope("JERUSALEM", ["ירושלים"])).toBe(true);
    expect(cityInScope("מבשרת ציון", ["ירושלים"])).toBe(false);
    expect(cityInScope("בית שמש", ["ירושלים", "בית שמש"])).toBe(true);
  });

  it("matches Haifa, the Krayot, Nesher and Tirat Carmel", () => {
    const scope = ["ירושלים", "חיפה", "הקריות", "נשר", "טירת כרמל"];
    for (const c of ["חיפה", "HAIFA", "4000", "קרית חיים", "קריית אתא", "קרית ביאליק", "Kiryat Motzkin", "קרית-ים", "9600", "נשר", "NESHER", "טירת הכרמל", "Tirat Carmel", "2100"]) {
      expect(cityInScope(c, scope)).toBe(true);
    }
    for (const c of ["קריית שמונה", "קרית גת", "עכו", "יקנעם"]) {
      expect(cityInScope(c, scope)).toBe(false);
    }
    expect(cityInScope("קרית ים", ["חיפה"])).toBe(false);
    expect(cityInScope("קרית ים", ["קריית ים"])).toBe(true);
  });
});

describe("listing pages", () => {
  it("reads Shufersal's table links", () => {
    const html = `<table><tr><td><a href="https://pricesprodpublic.blob.core.windows.net/pricefull/PriceFull7290027600007-207-202611160300.gz?sv=2014-02-14&amp;sr=b&amp;sig=x">לחץ להורדה</a></td></tr>
      <tr><td><a href="/FileObject/UpdateCategory?catID=2&amp;page=2">2</a></td></tr></table>`;
    const files = parseShufersalPage(html);
    expect(files).toHaveLength(1);
    expect(files[0]).toMatchObject({ kind: "pricefull", storeId: "207", stamp: "202611160300" });
    expect(files[0].url).toContain("&sr=b&sig=x");
  });

  it("reads PublishPrice's embedded file list", () => {
    const html = `<script>\nconst path = './20261116';\nconst files = [{"name":"PriceFull7290055700007-2960-202611160220.gz","size":12345},{"name":"Stores7290055700007-000-202611160201.gz","size":10}];\n</script>`;
    const files = parsePublishPricePage(html, "https://prices.carrefour.co.il/");
    expect(files.map((f) => f.url)).toEqual([
      "https://prices.carrefour.co.il/20261116/PriceFull7290055700007-2960-202611160220.gz",
      "https://prices.carrefour.co.il/20261116/Stores7290055700007-000-202611160201.gz",
    ]);
    expect(files[1].kind).toBe("stores");
  });

  it("reads the laibcatalog API", () => {
    const files = parseLaibFiles([{ fileName: "PriceFull7290696200003-001-012-202611160300.xml.gz", fileSize: "2 MB" }, { foo: 1 }], "7290696200003");
    expect(files).toHaveLength(1);
    expect(files[0].url).toBe("https://laibcatalog.co.il/webapi/7290696200003/PriceFull7290696200003-001-012-202611160300.xml.gz");
    expect(files[0].storeId).toBe("12");
  });
});

describe("history and ranges", () => {

  it("records changes only and keeps the day the current price started", () => {
    let snap: Snapshot | null = null;
    snap = mergeSnapshot("b", snap, [raw("a", 10)], "2026-10-01", 90, meta);
    snap = mergeSnapshot("b", snap, [raw("a", 10)], "2026-10-02", 90, meta);
    snap = mergeSnapshot("b", snap, [raw("a", 8)], "2026-10-20", 90, meta);
    snap = mergeSnapshot("b", snap, [raw("a", 12)], "2026-11-10", 90, meta);
    const a = snap.items[0];
    expect(a.h).toEqual([["2026-10-01", 10], ["2026-10-20", 8], ["2026-11-10", 12]]);
    expect(a).toMatchObject({ p: 12, s: "2026-11-10", lo: 8, hi: 12 });
  });

  it("a second load on the same day replaces that day's price", () => {
    let snap = mergeSnapshot("b", null, [raw("a", 10)], "2026-11-01", 90, meta);
    snap = mergeSnapshot("b", snap, [raw("a", 9)], "2026-11-02", 90, meta);
    snap = mergeSnapshot("b", snap, [raw("a", 9.5)], "2026-11-02", 90, meta);
    expect(snap.items[0].h).toEqual([["2026-11-01", 10], ["2026-11-02", 9.5]]);
  });

  it("the range counts the price in force at the window start but not older ones", () => {
    const h: [string, number][] = [["2026-01-01", 20], ["2026-07-01", 15], ["2026-10-01", 11]];
    expect(range(h, "2026-11-16", 90)).toEqual({ lo: 11, hi: 15 });
    expect(range(h, "2026-11-16", 365)).toEqual({ lo: 11, hi: 20 });
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
  });

  it("drops items no longer sold and duplicate lines", () => {
    let snap = mergeSnapshot("b", null, [raw("a", 1), raw("b", 2)], "2026-11-01", 90, meta);
    snap = mergeSnapshot("b", snap, [raw("b", 2), raw("b", 3)], "2026-11-02", 90, meta);
    expect(snap.items.map((i) => [i.c, i.p])).toEqual([["b", 2]]);
  });

  it("a partial (Price) file updates what it lists and keeps the rest", () => {
    let snap = mergeSnapshot("b", null, [raw("a", 1), raw("b", 2)], "2026-11-01", 90, meta);
    snap = mergeSnapshot("b", snap, [raw("b", 3), raw("c", 4)], "2026-11-02", 90, meta, true);
    expect(snap.items.map((i) => [i.c, i.p]).sort()).toEqual([["a", 1], ["b", 3], ["c", 4]]);
  });
});

describe("config and scheduling", () => {
  it("fills defaults and ignores bad values", () => {
    expect(withDefaults(undefined)).toEqual(DEFAULT_CONFIG);
    const c = withDefaults({ refreshHours: -1, rangeDays: 1000, chains: ["ramilevy", "nope"], cities: [] });
    expect(c).toMatchObject({ refreshHours: 24, rangeDays: 365, chains: ["ramilevy"], cities: ["ירושלים", "חיפה", "הקריות", "נשר", "טירת כרמל"] });
  });

  it("is due by age; failures back off for an hour", () => {
    const now = new Date("2026-11-16T12:00:00Z");
    const st = (updatedAt: string | null, error: string | null, lastAttemptAt: string | null): BranchStatus => ({ updatedAt, error, lastAttemptAt, items: 0, sourceFile: "", sourceStamp: "" });
    expect(isDue(null, DEFAULT_CONFIG, now)).toBe(true);
    expect(isDue(st("2026-11-16T02:00:00Z", null, null), DEFAULT_CONFIG, now)).toBe(false);
    expect(isDue(st("2026-11-15T11:00:00Z", null, null), DEFAULT_CONFIG, now)).toBe(true);
    expect(isDue(st("2026-11-15T11:00:00Z", "x", "2026-11-16T11:30:00Z"), DEFAULT_CONFIG, now)).toBe(false);
    expect(isDue(st("2026-11-16T02:00:00Z", null, null), { ...DEFAULT_CONFIG, refreshHours: 6 }, now)).toBe(true);
  });

  it("tick builds the Jerusalem branch index and loads tracked branches", async () => {
    const files: Record<string, { entries: FileEntry[]; data: Record<string, Uint8Array> }> = {
      ramilevy: {
        entries: [entry("Stores7290058140886-202611160500.xml", "s"), entry("PriceFull7290058140886-039-202611160300.gz", "p39")],
        data: { s: fixture("cerberus-stores.utf16.xml"), p39: fixture("pricefull.xml.gz") },
      },
    };
    const fake: Source = {
      async list(chain: Chain, kind: FileKind) {
        return (files[chain.key]?.entries ?? []).filter((f) => f.kind === kind);
      },
      async fetch(chain: Chain, f: FileEntry) {
        return files[chain.key].data[f.url];
      },
    };
    const mem = memoryStore(["ramilevy_39", "ramilevy_52"]);
    const report = await tick({ ...DEFAULT_CONFIG, chains: ["ramilevy"] }, { store: mem, sourceFor: () => fake, now: () => new Date("2026-11-16T04:00:00Z") }, 60_000);
    expect(mem.index.branches.map((b) => b.id)).toEqual(["ramilevy_52", "ramilevy_39"]); // sorted by name: הר חוצבים, תלפיות
    expect(mem.index.branches.find((b) => b.id === "ramilevy_39")).toMatchObject({ lat: 31.75, lng: 35.21, chainName: "רמי לוי", city: "3000" });
    expect(mem.snapshots.get("ramilevy_39")?.items).toHaveLength(4); // duplicate line dropped
    expect(mem.snapshots.get("ramilevy_39")?.items.find((i) => i.c === "2000123")?.m).toBe("100 גרם");
    expect(mem.status.get("ramilevy_39")).toMatchObject({ items: 4, error: null });
    expect(mem.status.get("ramilevy_52")?.error).toContain("no PriceFull/Price file for store 52");
    expect(mem.status.get("ramilevy_52")?.error).toContain("price files for 1 stores (39)");
    expect(report.join("\n")).toContain("branches: 2");
  });

  it("falls back to the newest Price file when a branch has no PriceFull", async () => {
    const entries = [entry("Price7290058140886-052-202611160100.gz", "old"), entry("Price7290058140886-052-202611160300.gz", "new")];
    const fake: Source = {
      async list(_c: Chain, kind: FileKind) {
        return entries.filter((f) => f.kind === kind);
      },
      async fetch() {
        return fixture("pricefull.xml.gz");
      },
    };
    const mem = memoryStore([]);
    const branch = { id: "ramilevy_52", storeId: "52", chainKey: "ramilevy" } as Branch;
    const st = await refreshBranchPrices(branch, DEFAULT_CONFIG, { store: mem, sourceFor: () => fake, now: () => new Date("2026-11-16T04:00:00Z") });
    expect(st).toMatchObject({ error: null, items: 4, sourceFile: "Price7290058140886-052-202611160300.gz" });
  });
});

function memoryStore(tracked: string[]) {
  const s = {
    index: { updatedAt: null as string | null, branches: [] as Branch[] },
    snapshots: new Map<string, Snapshot>(),
    status: new Map<string, BranchStatus>(),
  };
  const store: PriceStore & typeof s = Object.assign(s, {
    async readIndex() {
      return s.index;
    },
    async writeIndex(branches: Branch[], meta: { updatedAt: string }) {
      s.index = { updatedAt: meta.updatedAt, branches };
    },
    async readSnapshot(id: string) {
      return s.snapshots.get(id) ?? null;
    },
    async writeSnapshot(snap: Snapshot) {
      s.snapshots.set(snap.branchId, snap);
    },
    async readStatus(id: string) {
      return s.status.get(id) ?? null;
    },
    async writeStatus(id: string, st: BranchStatus) {
      s.status.set(id, st);
    },
    async trackedBranchIds() {
      return tracked;
    },
    geoCache: {
      async get() {
        return { lat: 31.75, lng: 35.21, approx: false };
      },
      async put() {},
    },
  });
  return store;
}

describe("geocoding input", () => {
  it("strips noise from addresses", () => {
    expect(cleanAddress("(קניון) קרית המדע 5")).toBe("קרית המדע 5");
    expect(cleanAddress("רח' יפו 97 פינת שטראוס")).toBe("יפו 97");
  });
});

import { guardConfig, parseNotification, shouldCut } from "../src/budget";

describe("budget kill switch", () => {
  it("cuts only when the cost reaches the budget and the switch is on", () => {
    const n = parseNotification({ costAmount: 25.1, budgetAmount: 25, currencyCode: "ILS", alertThresholdExceeded: 1 })!;
    expect(shouldCut(n, guardConfig(undefined))).toBe(true);
    expect(shouldCut({ ...n, costAmount: 24.9 }, guardConfig(undefined))).toBe(false);
    expect(shouldCut(n, guardConfig({ enabled: false }))).toBe(false);
    expect(shouldCut(n, guardConfig({ cutAtRatio: 2 }))).toBe(false);
    expect(shouldCut({ ...n, budgetAmount: 0 }, guardConfig(undefined))).toBe(false);
    expect(parseNotification({ costAmount: "x" })).toBeNull();
    expect(parseNotification(undefined)).toBeNull();
  });
});
