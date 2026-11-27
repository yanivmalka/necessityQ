import { describe, expect, it } from "vitest";
import type { GoogleGenAI } from "@google/genai";
import {
  MAX_AMOUNT,
  STARTER_SYSTEM_PROMPT,
  parseStarter,
  starterCacheKey,
  starterMessage,
  starterWithGemini,
  validateStarterRequest,
} from "../src/starter";

const catalog = [
  { name: "חלב", category: "DAIRY" as const },
  { name: "לחם", category: "BAKERY" as const },
  { name: "חיתולים", category: "BABY" as const },
];
const ok = { householdId: "h1", ages: [40, 38, 1], days: 7, catalog, exclude: [] as string[] };

describe("AI list: requests", () => {
  it("accepts a sane request", () => {
    expect(validateStarterRequest({ ...ok, ages: [40.6, 38, 1], catalog: [...catalog, { name: " חלב ", category: "DAIRY" }] })).toEqual({
      ...ok,
      ages: [40, 38, 1],
    });
  });

  it("refuses what it can't use", () => {
    expect(typeof validateStarterRequest({ ...ok, householdId: "a/b" })).toBe("string");
    expect(typeof validateStarterRequest({ ...ok, ages: [] })).toBe("string");
    expect(typeof validateStarterRequest({ ...ok, days: 0 })).toBe("string");
    expect(typeof validateStarterRequest({ ...ok, days: 60 })).toBe("string");
    expect(typeof validateStarterRequest({ ...ok, catalog: [] })).toBe("string");
    expect(typeof validateStarterRequest({ ...ok, catalog: [{ name: "x", category: "NOPE" }] })).toBe("string");
    expect(typeof validateStarterRequest(null)).toBe("string");
  });
});

describe("AI list: the answer", () => {
  it("keeps only catalogue products, known units, sane amounts, once each, nothing already on the list", () => {
    const json = JSON.stringify({
      items: [
        { name: "חלב", amount: 3, unit: "liter", reason: "לבוקר" },
        { name: "חלב", amount: 9, unit: "liter", reason: "שוב" },
        { name: "גבינה מומצאת", amount: 1, unit: "piece", reason: "" },
        { name: "לחם", amount: 2, unit: "spoon", reason: "" },
        { name: "חיתולים", amount: MAX_AMOUNT + 1, unit: "package", reason: "" },
      ],
    });
    expect(parseStarter(json, ok)).toEqual([{ name: "חלב", amount: 3, unit: "liter", reason: "לבוקר" }]);
    expect(parseStarter(json, { ...ok, exclude: ["חלב"] })).toEqual([]);
    expect(parseStarter("not json", ok)).toEqual([]);
    expect(parseStarter(undefined, ok)).toEqual([]);
  });

  it("the cache key ignores the order of ages and changes with the span", () => {
    expect(starterCacheKey({ ...ok, ages: [1, 40, 38] })).toBe(starterCacheKey(ok));
    expect(starterCacheKey({ ...ok, days: 14 })).not.toBe(starterCacheKey(ok));
  });

  it("sends ages, span and catalogue to the model, with the system prompt", async () => {
    let sent: { contents?: string; config?: { systemInstruction?: string } } = {};
    const ai = {
      models: {
        generateContent: async (args: typeof sent) => {
          sent = args;
          return { text: JSON.stringify({ items: [{ name: "לחם", amount: 2, unit: "piece", reason: "כריכים" }] }) };
        },
      },
    } as unknown as GoogleGenAI;
    const items = await starterWithGemini(ai, "m", ok);
    expect(items).toEqual([{ name: "לחם", amount: 2, unit: "piece", reason: "כריכים" }]);
    expect(sent.contents).toBe(starterMessage(ok));
    expect(sent.contents).toContain("40, 38, 1");
    expect(sent.contents).toContain("7 days");
    expect(sent.config?.systemInstruction).toBe(STARTER_SYSTEM_PROMPT);
  });
});
