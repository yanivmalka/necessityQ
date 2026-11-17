import { describe, expect, it } from "vitest";
import { TZAMERET, buildSnapshot, fetchTable, num, snapshotStats } from "../src/nutrition";

const foods = [
  { _id: 1, Code: 1001, smlmitzrach: 1001, shmmitzrach: "עגבנייה, טרייה", food_energy: 18.04, protein: 0.88, total_fat: 0.2, carbohydrates: 3.9, total_dietary_fiber: 1.2, sodium: 5 },
  { _id: 2, Code: 2002, smlmitzrach: 2002, shmmitzrach: "שקשוקה", food_energy: "95.5", protein: "5.1", total_fat: null, carbohydrates: "", sodium: "310.4" },
  { _id: 3, Code: 3003, smlmitzrach: 3003, shmmitzrach: "  ביצה,   שלמה  ", food_energy: 143, protein: 12.6 },
  { _id: 4, Code: 3003, smlmitzrach: 3003, shmmitzrach: "כפילות", food_energy: 1 },
  { _id: 5, Code: null, smlmitzrach: null, shmmitzrach: "ללא קוד" },
];

describe("tzameret snapshot", () => {
  it("keeps the main nutrients per 100 g, rounded, and skips duplicates and rows without a code", () => {
    const s = buildSnapshot({ foods, recipes: [], units: [], unitNames: [] }, "2026-11-17T00:00:00.000Z");
    expect(s.foods.map((f) => f.c)).toEqual([1001, 2002, 3003]);
    expect(s.foods[0]).toEqual({ c: 1001, n: "עגבנייה, טרייה", e: 18, p: 0.9, f: 0.2, cb: 3.9, fi: 1.2, na: 5 });
    expect(s.foods[1]).toMatchObject({ e: 95.5, p: 5.1, f: null, cb: null, na: 310 });
    expect(s.foods[2].n).toBe("ביצה, שלמה");
  });

  it("attaches household measures with their names and dish composition sorted by share", () => {
    const s = buildSnapshot({
      foods,
      unitNames: [{ smlmida: 1, shmmida: "יחידה בינונית" }, { smlmida: 2, shmmida: "כוס" }],
      units: [
        { mmitzrach: 1001, mida: 1, mishkal: 123 },
        { mmitzrach: 1001, mida: 2, mishkal: "180" },
        { mmitzrach: 1001, mida: 2, mishkal: 999 }, // duplicate measure is ignored
        { mmitzrach: 3003, mida: 7, mishkal: 50 }, // unknown measure name
        { mmitzrach: 3003, mida: 1, mishkal: 0 },
      ],
      recipes: [
        { smlmitzrach: 2002, smlmarciv: 3003, shmmarciv: "ביצה, שלמה", achuz_marciv: 30 },
        { smlmitzrach: 2002, smlmarciv: 1001, shmmarciv: "עגבנייה, טרייה", achuz_marciv: 55.555 },
        { smlmitzrach: 2002, smlmarciv: 9, shmmarciv: "מלח", achuz_marciv: 0 },
      ],
    });
    expect(s.foods[0].u).toEqual([["יחידה בינונית", 123], ["כוס", 180]]);
    expect(s.foods[2].u).toEqual([["מידה 7", 50]]);
    expect(s.foods[1].r).toEqual([[1001, "עגבנייה, טרייה", 55.56], [3003, "ביצה, שלמה", 30]]);
    expect(snapshotStats(s)).toEqual({ foods: 3, units: 2, recipes: 1 });
  });

  it("parses numbers leniently", () => {
    expect(num("1,5")).toBe(1.5);
    expect(num("abc")).toBeNull();
    expect(num(undefined)).toBeNull();
  });

  it("reads a datastore table page by page", async () => {
    const urls: string[] = [];
    const all = Array.from({ length: 5 }, (_, i) => ({ smlmitzrach: i }));
    const get = async (url: string) => {
      urls.push(url);
      const offset = Number(new URL(url).searchParams.get("offset"));
      return JSON.stringify({ success: true, result: { total: all.length, records: all.slice(offset, offset + 2) } });
    };
    const rows = await fetchTable(TZAMERET.foods, get, 2);
    expect(rows).toHaveLength(5);
    expect(urls).toHaveLength(3);
    expect(urls[0]).toContain(`resource_id=${TZAMERET.foods}`);
  });

  it("fails on an unsuccessful response", async () => {
    await expect(fetchTable(TZAMERET.foods, async () => JSON.stringify({ success: false }))).rejects.toThrow(/unsuccessful/);
  });
});
